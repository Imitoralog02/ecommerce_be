# NÉORA Ecommerce Microservices

Backend được tách thành các Spring Boot process độc lập. Flutter chỉ gọi API Gateway tại `http://localhost:8080/api/v1`; gateway chuyển request đến service sở hữu nghiệp vụ tương ứng.

## Service boundaries

| Module | Port | Sở hữu dữ liệu | Trách nhiệm |
|---|---:|---|---|
| `gateway-service` | 8080 | Không | Một entry point cho Flutter, route HTTP |
| `auth-service` | 8081 | MongoDB `ecommerce_auth` | User, đăng ký, đăng nhập, profile, phát JWT |
| `catalog-service` | 8082 | MongoDB `ecommerce_catalog`, Redis cache | Product, category, tìm kiếm, tồn kho theo size/màu |
| `cart-service` | 8083 | Redis `cart:*` | Giỏ hàng; chỉ gọi catalog qua HTTP |
| `order-service` | 8084 | MongoDB `ecommerce_order` | Checkout, order; phát Kafka event |
| `notification-service` | 8085 | MongoDB `ecommerce_notification` | Consume order event, lưu notification |
| `customer-service` | 8086 | MongoDB `ecommerce_customer` | Sổ địa chỉ và wishlist của khách hàng |
| `shared-events` | — | Không | Hợp đồng Kafka `OrderEvent` và `OrderEventType` |
| `shared-kernel` | — | Không | Security filter và error contract kỹ thuật dùng chung |

Không service nào truy cập collection của service khác. MongoDB dùng chung server local nhưng mỗi service có database riêng. Các biến `AUTH_MONGODB_URI`, `CATALOG_MONGODB_URI`, `ORDER_MONGODB_URI`, `NOTIFICATION_MONGODB_URI` và `CUSTOMER_MONGODB_URI` cho phép cấu hình độc lập.

## Luồng xử lý

```text
Flutter -> Gateway -> Auth / Catalog / Cart / Order / Notification / Customer
                            Cart ----HTTP----> Catalog
                            Order ---HTTP----> Cart / Catalog inventory
                            Order ---Kafka---> Notification
                         Customer ----HTTP----> Catalog

Cart, Catalog cache -------------------------> Redis
Auth / Catalog / Order / Notification / Customer -> database riêng
```

Order không ghi trực tiếp notification. Đơn mới được lưu ở `PENDING`/`inventoryState=PENDING`; tác vụ nền giữ hàng tại catalog, chuyển thành `CONFIRMED`/`RESERVED` nếu đủ hàng hoặc `CANCELLED`/`REJECTED` nếu thiếu. Lỗi mạng được thử lại; các món đã đặt chỉ được xóa khỏi giỏ sau khi giữ hàng thành công. Sau xác nhận tồn kho, order-service phát `ORDER_CREATED` lên topic `order.lifecycle.v1`, thử lại khi Kafka lỗi. Notification-service dùng event ID cố định để bỏ qua event phát lại.

## Chạy local

Khởi động MongoDB, Redis và Kafka:

```powershell
docker compose up -d --wait
```

Sau đó chạy mỗi service trong terminal riêng:

```powershell
.\gradlew.bat :auth-service:bootRun
.\gradlew.bat :catalog-service:bootRun
.\gradlew.bat :cart-service:bootRun
.\gradlew.bat :order-service:bootRun
.\gradlew.bat :notification-service:bootRun
.\gradlew.bat :customer-service:bootRun
.\gradlew.bat :gateway-service:bootRun
```

API Flutter tiếp tục dùng một base URL:

```text
http://localhost:8080/api/v1
```

Hợp đồng MVP và ánh xạ trạng thái/DTO khi nối Flutter: [docs/flutter-api-contract.md](docs/flutter-api-contract.md).

## Build

Build/test tất cả module:

```powershell
.\gradlew.bat clean test bootJar
```

Build một service độc lập:

```powershell
.\gradlew.bat :order-service:bootJar
```

JAR của từng service nằm trong `<service>/build/libs/`.

## Endpoint routing

| Prefix | Service |
|---|---|
| `/api/v1/auth/**`, `/api/v1/me` | auth-service |
| `/api/v1/products/**`, `/api/v1/categories/**` | catalog-service |
| `/api/v1/cart/**` | cart-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/addresses/**`, `/api/v1/wishlist/**` | customer-service |

### Customer API

Các endpoint dưới đây yêu cầu `Authorization: Bearer <access-token>`:

| Chức năng | Endpoint |
|---|---|
| Danh sách/tạo địa chỉ | `GET/POST /api/v1/addresses` |
| Sửa/xóa địa chỉ | `PUT/DELETE /api/v1/addresses/{id}` |
| Đặt địa chỉ mặc định | `PATCH /api/v1/addresses/{id}/default` |
| Xem wishlist | `GET /api/v1/wishlist` |
| Thêm sản phẩm | `POST /api/v1/wishlist/items` với body `{ "productId": "..." }` |
| Xóa một/xóa hết | `DELETE /api/v1/wishlist/items/{productId}`, `DELETE /api/v1/wishlist` |

### Checkout COD

Catalog có tồn kho riêng theo từng biến thể size/màu. `GET /api/v1/products/{id}/availability?size=M&color=...` trả số lượng có thể bán. Bộ sản phẩm demo được seed 20 chiếc mỗi biến thể khi chưa có bản ghi tồn kho; đặt `INVENTORY_DEMO_SEED_QUANTITY=0` ngoài môi trường demo và nạp số lượng thật trước khi bán. Các thao tác giữ/hoàn trả tồn kho chỉ có ở endpoint nội bộ của catalog và yêu cầu `X-Internal-Api-Key` (`INTERNAL_API_KEY`).

Catalog mẫu mới khớp 8 sản phẩm trong Flutter (bao gồm size/màu). Database đã có dữ liệu không bị ghi đè. Nếu chỉ đang dùng bản seed demo cũ, có thể bật `CATALOG_SYNC_LEGACY_DEMO=true` một lần sau khi sao lưu để cập nhật đúng các bản ghi mang dấu hiệu seed cũ; tắt lại sau khi chạy. Không dùng chế độ này để di trú dữ liệu sản phẩm thật hoặc đơn đang xử lý.

Tạo đơn trả ngay trạng thái `PENDING`; mobile cần đọc lại `GET /api/v1/orders/{id}` để biết kết quả giữ hàng. `inventoryState=RESERVED`/`status=CONFIRMED` mới là đơn được chấp nhận; `inventoryState=REJECTED`/`status=CANCELLED` nghĩa là thiếu hàng, giỏ vẫn được giữ. Khi catalog hoặc cart tạm lỗi, worker thử lại theo `ORDER_INVENTORY_POLL_MS`.

`PATCH /api/v1/orders/{id}/cancel` cho phép chính chủ hủy đơn `PENDING` hoặc `CONFIRMED`, trả `CANCELLED`/`RELEASE_PENDING`. Tác vụ nền hoàn lại lượng đã giữ ở catalog, sau đó chuyển `inventoryState=RELEASED`; lỗi mạng được thử lại. Nếu hủy đúng lúc đang giữ hàng, tác vụ sẽ hoàn kho sau khi thao tác giữ hàng kết thúc. Kafka phát `ORDER_CANCELLED` để tạo notification. Chưa có tự động hoàn sản phẩm vào giỏ sau khi hủy.

`POST /api/v1/orders/quote` nhận `{ "shippingType": "EXPRESS" }`, dùng giỏ hàng trên server và trả `subtotal`, `shippingFee`, `discount`, `total`, `currency`. Giao hỏa tốc hiện là 30.000 VND; giao tiêu chuẩn và nhận tại cửa hàng là 0 VND.

`POST /api/v1/orders` yêu cầu header `Idempotency-Key` (8–128 ký tự chữ, số, `_` hoặc `-`) và body ví dụ:

```json
{
  "shippingType": "EXPRESS",
  "paymentType": "CASH_ON_DELIVERY",
  "recipientName": "Nguyễn An",
  "phone": "0908123456",
  "addressLine": "12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh"
}
```

Cùng user và key sẽ trả cùng đơn; dùng lại key với thông tin khác nhận HTTP 409. `couponCode` khác rỗng bị từ chối cho đến khi backend khuyến mãi hoàn thành. Các phương thức thanh toán trực tuyến cũng bị từ chối thay vì tạo đơn giả đã thanh toán.

### Phiên đăng nhập cho mobile

`POST /api/v1/auth/register` và `POST /api/v1/auth/login` trả `accessToken`, `tokenType`, `expiresIn`, `refreshToken`, `refreshExpiresIn` và `user`. Access token mặc định sống 30 phút; refresh token mặc định sống 30 ngày (`AUTH_REFRESH_TTL`). Chỉ lưu refresh token trong kho an toàn của thiết bị; không ghi token vào log.

Khi access token hết hạn, gọi `POST /api/v1/auth/refresh` với body `{ "refreshToken": "..." }`. Phản hồi có cả access token và refresh token mới; refresh token cũ chỉ dùng được một lần. Nếu nhận HTTP 401, yêu cầu người dùng đăng nhập lại. `POST /api/v1/auth/logout` nhận cùng body và trả HTTP 204 để thu hồi refresh token hiện tại. App phải xóa cả hai token trên thiết bị. Do các service tự xác minh JWT, access token đã phát vẫn hợp lệ đến khi hết hạn; logout không thu hồi access token ngay lập tức.

Auth-service ký JWT bằng `JWT_SECRET`. Các service bảo vệ dữ liệu tự xác minh JWT bằng cùng secret. Thiết lập các biến môi trường từ `.env.example` trong terminal hoặc môi trường chạy service; Gradle `bootRun` không tự đọc file `.env`. Thay secret phát triển trước khi triển khai.

## Ghi chú production

Đây là microservice base cho giai đoạn phát triển. Luồng giữ hàng đã dùng cập nhật nguyên tử theo từng biến thể và hủy đơn hoàn lại reservation, nhưng còn cần bước hoàn tất reservation khi giao hàng, kiểm thử đa instance và kiểm thử tích hợp MongoDB/Redis/Kafka trước production. Cũng cần thu hồi access token tức thời nếu nghiệp vụ yêu cầu, transactional outbox cho Kafka, retry/dead-letter topic, distributed tracing, rate limiting tại gateway và secret management. Chưa có API quên/đặt lại mật khẩu và dịch vụ gửi email; không nối nút quên mật khẩu của Flutter vào API giả.
#   e c o m m e r c e _ b e  
 
