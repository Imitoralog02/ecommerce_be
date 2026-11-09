# NÉORA Ecommerce Microservices

Backend được xây dựng theo kiến trúc Microservices, trong đó mỗi service chạy như một Spring Boot process độc lập.

Ứng dụng Flutter chỉ giao tiếp với API Gateway tại `http://localhost:8080/api/v1`. Gateway chuyển request đến service sở hữu nghiệp vụ tương ứng.

## Service Boundaries

| Module | Port | Sở hữu dữ liệu | Trách nhiệm |
|---|---:|---|---|
| `gateway-service` | 8080 | Không | Entry point cho Flutter, định tuyến HTTP |
| `auth-service` | 8081 | MongoDB `ecommerce_auth` | User, đăng ký, đăng nhập, profile, JWT |
| `catalog-service` | 8082 | MongoDB `ecommerce_catalog`, Redis | Product, category, tìm kiếm, tồn kho theo size/màu |
| `cart-service` | 8083 | Redis `cart:*` | Giỏ hàng, giao tiếp catalog qua HTTP |
| `order-service` | 8084 | MongoDB `ecommerce_order` | Checkout, order, phát Kafka event |
| `notification-service` | 8085 | MongoDB `ecommerce_notification` | Consume order event, lưu notification |
| `customer-service` | 8086 | MongoDB `ecommerce_customer` | Sổ địa chỉ, wishlist |
| `shared-events` | — | Không | Kafka `OrderEvent`, `OrderEventType` |
| `shared-kernel` | — | Không | Security filter, error contract dùng chung |

Mỗi service sở hữu dữ liệu riêng và không truy cập trực tiếp collection của service khác.

MongoDB có thể sử dụng chung một server local, nhưng mỗi service được cấu hình database riêng thông qua các biến:

- `AUTH_MONGODB_URI`
- `CATALOG_MONGODB_URI`
- `ORDER_MONGODB_URI`
- `NOTIFICATION_MONGODB_URI`
- `CUSTOMER_MONGODB_URI`

## Luồng xử lý hệ thống

```text
Flutter Application
        |
        v
    API Gateway
        |
        +---- Auth Service
        +---- Catalog Service
        +---- Cart Service
        +---- Order Service
        +---- Notification Service
        +---- Customer Service

Cart --------HTTP--------> Catalog
Order -------HTTP--------> Cart / Catalog Inventory
Order -------Kafka-------> Notification
Customer ----HTTP--------> Catalog

Cart / Catalog Cache ----> Redis

Auth / Catalog / Order / Notification / Customer
                |
                v
        MongoDB riêng từng service
```

### Luồng xử lý đơn hàng

Order Service không ghi trực tiếp notification.

Khi người dùng tạo đơn hàng:

1. Đơn hàng được lưu với `status=PENDING` và `inventoryState=PENDING`.
2. Worker thực hiện giữ hàng tại Catalog Service.
3. Nếu đủ hàng, trạng thái chuyển thành `CONFIRMED` và `RESERVED`.
4. Nếu thiếu hàng, trạng thái chuyển thành `CANCELLED` và `REJECTED`.
5. Các sản phẩm đã đặt chỉ được xóa khỏi giỏ sau khi giữ hàng thành công.
6. Order Service phát `ORDER_CREATED` lên Kafka topic `order.lifecycle.v1`.
7. Notification Service xử lý event và lưu thông báo.
8. Khi đơn chuyển sang `DELIVERED`, worker chốt reservation: bỏ dấu giữ hàng mà không cộng lại số lượng có thể bán. Nếu Catalog tạm thời lỗi, worker thử lại. `inventoryState=FINALIZED` cho biết đã chốt xong.

Các lỗi mạng được thử lại. Kafka event sử dụng event ID cố định để Notification Service có thể bỏ qua event được phát lại.

## Chạy Local

### 1. Khởi động MongoDB, Redis, Kafka và Mailpit

```powershell
docker compose up -d --wait
```

MongoDB chạy trong Docker và chỉ mở trên máy local tại `127.0.0.1:27018`
(cổng `27017` bên trong container). MongoDB Compass là công cụ xem/chỉnh dữ liệu,
không phải database server. Kết nối Compass bằng `mongodb://127.0.0.1:27018`;
không dùng `27017` nếu máy còn một MongoDB Windows riêng.
Mailpit nhận email khôi phục ở SMTP `127.0.0.1:1025`; mở
`http://127.0.0.1:8025` để xem mã gửi đến trong môi trường local. Mailpit chỉ
giữ email thử nghiệm; khi triển khai thật cần cấu hình SMTP qua `MAIL_HOST`,
`MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH` và
`MAIL_SMTP_STARTTLS`.
Compass có thể thêm/sửa/xóa document trực tiếp trong môi trường phát triển, nhưng
thao tác đó bỏ qua kiểm tra nghiệp vụ của API và cache Redis; đặc biệt không sửa
đơn hàng hoặc tồn kho đang giữ hàng nếu chưa kiểm tra các bản ghi liên quan.

Trong `ecommerce_catalog`, Compass hiển thị `flash_sales` (chiến dịch và từng
offer với `quota`) và `flash_sale_allocations` (lượt sale thực tế theo sản phẩm).
Mỗi allocation có `capacity`, `available`, `reservations`; một đơn mới được
xác nhận sẽ giảm `available`, hủy đơn sẽ hoàn lượt, giao xong sẽ xóa dấu giữ
nhưng không hoàn lượt. Đừng chỉ sửa `flash_sales.offers.quota` để đặt lại lượt
của chiến dịch đã bắt đầu, vì allocation đã tạo không tự thay đổi.

Tạo `.env` cho lần chạy đầu, rồi đặt `JWT_SECRET` và `INTERNAL_API_KEY`
thành hai giá trị riêng dài ít nhất 32 ký tự. File `.env` được Git bỏ qua:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
```

### 2. Chạy các Microservices

Gradle tự đọc `.env` ở thư mục gốc khi chạy `bootRun` và truyền các biến cho
service. Biến đã được đặt trong terminal hoặc cấu hình chạy của IDE sẽ được ưu
tiên. Không cần nạp `.env` thủ công trong từng terminal.

Trong IDE, hãy chạy Gradle task `:tên-service:bootRun`; chạy trực tiếp lớp
`main` của Spring Boot không sử dụng cấu hình Gradle này.

Chạy mỗi service trong một terminal riêng:

```powershell
.\gradlew.bat :auth-service:bootRun
```

```powershell
.\gradlew.bat :catalog-service:bootRun
```

```powershell
.\gradlew.bat :cart-service:bootRun
```

```powershell
.\gradlew.bat :order-service:bootRun
```

```powershell
.\gradlew.bat :notification-service:bootRun
```

```powershell
.\gradlew.bat :customer-service:bootRun
```

```powershell
.\gradlew.bat :gateway-service:bootRun
```

### 3. API Base URL

Flutter sử dụng:

```text
http://localhost:8080/api/v1
```

Hợp đồng MVP và ánh xạ trạng thái/DTO:

[Flutter API Contract](docs/flutter-api-contract.md)

## Build

Sau khi hạ tầng và cả bảy service đã chạy, kiểm tra luồng API qua Gateway:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-api.ps1
```

Script chỉ chấp nhận Gateway trên máy local. Nó tạo tài khoản và đơn thử trong database phát triển, kiểm tra đăng nhập/refresh, catalog, wishlist, địa chỉ, giỏ, COD/idempotency, kết thúc key COD chưa có đơn, thông báo Kafka, hủy/hoàn kho và đơn thiếu tồn kho. Đơn COD thành công được hủy sau khi kiểm tra; tài khoản và lịch sử đơn thử vẫn được lưu để đối chiếu.

### Kiểm thử tích hợp tách khỏi dữ liệu demo

Sau khi build JAR và khởi động MongoDB, Redis, Kafka, Mailpit, chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run-order-integration.ps1
```

Script khởi động bảy service tạm trên cổng `8280`–`8286`, dùng năm MongoDB riêng có tiền tố `ecommerce_it_` và một Kafka topic riêng. Nó chạy smoke API, kiểm tra email khôi phục qua Mailpit, rồi kiểm tra luồng `CONFIRMED → PROCESSING → SHIPPED → DELIVERED → FINALIZED`, số lượng tồn kho, khả năng gọi lại finalize và notification Kafka. Tài khoản role `ADMIN` chỉ được tạo trong database kiểm thử để gọi endpoint fulfillment hiện có; đây không phải chức năng quản trị cho app. Script dừng các service tạm sau khi chạy, còn database kiểm thử và log trong `.local-runtime/` được giữ để đối chiếu. Các database `ecommerce_auth`, `ecommerce_catalog`, `ecommerce_order`, `ecommerce_notification`, `ecommerce_customer` không bị thay đổi bởi bộ kiểm thử này.

### Tạo nhiều dữ liệu demo để xem trong Compass

Chỉ chạy sau khi cả bảy service đều `UP` và đã đọc `.env` trỏ tới MongoDB Docker.
Đặt `DEMO_SEED_PASSWORD` trong `.env` (8–72 ký tự, không commit), rồi chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\seed-demo-data.ps1 -Users 12 -OrdersPerUser 2
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\database-summary.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\validate-database.ps1
```

Seed dùng API thật: mỗi batch tạo tài khoản `demo.<batch>.uXX@example.test`,
hai địa chỉ, ba mục wishlist, đơn COD xác nhận/hủy, một giỏ hàng còn mở cho
mỗi tài khoản và một đơn bị từ chối vì thiếu tồn kho. `BatchId` tự sinh nên
chạy lại sẽ tạo **thêm** dữ liệu, không ghi đè tài khoản cũ. Nếu seed lỗi giữa
chừng, các bản ghi đã tạo vẫn được giữ; dùng batch mới để chạy lại. Không chạy
script trên dữ liệu production. Giỏ hàng nằm trong Redis, không hiện ở Compass.
Script từ chối chạy nếu một trong năm service dùng MongoDB không kết nối cổng
Docker `27018` (ví dụ service cũ vẫn đang dùng MongoDB Windows `27017`).
Script kiểm tra cuối cùng chỉ đọc dữ liệu và báo lỗi nếu có user/sản phẩm/đơn
tham chiếu không tồn tại, địa chỉ mặc định sai hoặc reservation tồn kho lệch.

### Build và test toàn bộ module

```powershell
.\gradlew.bat clean test bootJar
```

### Build Order Service

```powershell
.\gradlew.bat :order-service:bootJar
```

JAR của mỗi service nằm trong thư mục:

`<service>/build/libs/`

## Endpoint Routing

| Prefix | Service |
|---|---|
| `/api/v1/auth/**`, `/api/v1/me` | auth-service |
| `/api/v1/products/**`, `/api/v1/categories/**` | catalog-service |
| `/api/v1/cart/**` | cart-service |
| `/api/v1/orders/**` | order-service |
| `/api/v1/notifications/**` | notification-service |
| `/api/v1/addresses/**`, `/api/v1/wishlist/**` | customer-service |

## Customer API

Các endpoint dưới đây yêu cầu header:

`Authorization: Bearer <access-token>`

Mỗi tài khoản có tối đa một địa chỉ `isDefault=true`. MongoDB áp dụng unique partial index
`unique_default_address_per_user` cho quy tắc này. Nếu database cũ đã có nhiều địa chỉ mặc định
của cùng một người dùng, hãy sao lưu và xử lý bản ghi trùng trước khi khởi động customer-service;
MongoDB sẽ không tạo được index khi dữ liệu đang vi phạm. Có thể kiểm tra trong database
`ecommerce_customer` bằng:

```javascript
db.shipping_addresses.aggregate([
  { $match: { isDefault: true } },
  { $group: { _id: "$userId", count: { $sum: 1 }, ids: { $push: "$_id" } } },
  { $match: { count: { $gt: 1 } } }
])
```

| Chức năng | Endpoint |
|---|---|
| Danh sách/tạo địa chỉ | `GET/POST /api/v1/addresses` |
| Sửa/xóa địa chỉ | `PUT/DELETE /api/v1/addresses/{id}` |
| Đặt địa chỉ mặc định | `PATCH /api/v1/addresses/{id}/default` |
| Xem wishlist | `GET /api/v1/wishlist` |
| Thêm sản phẩm | `POST /api/v1/wishlist/items` |
| Xóa sản phẩm | `DELETE /api/v1/wishlist/items/{productId}` |
| Xóa wishlist | `DELETE /api/v1/wishlist` |

Body thêm sản phẩm:

```json
{
  "productId": "..."
}
```

## Checkout COD

### Quản lý tồn kho

Catalog quản lý tồn kho theo từng biến thể size và màu.

API kiểm tra tồn kho:

`GET /api/v1/products/{id}/availability?size=M&color=...`

Dữ liệu demo được seed 20 sản phẩm cho mỗi biến thể khi chưa có bản ghi tồn kho.

Đối với môi trường không phải demo, thiết lập:

```env
INVENTORY_DEMO_SEED_QUANTITY=0
```

Các thao tác giữ và hoàn tồn kho sử dụng endpoint nội bộ, yêu cầu:

`X-Internal-Api-Key`

Giá trị được cấu hình qua `INTERNAL_API_KEY`.

### Đồng bộ Catalog Demo

Catalog mẫu khớp 8 sản phẩm trong Flutter, bao gồm size và màu.

Database hiện có không bị ghi đè mặc định.

Đối với dữ liệu seed demo cũ, có thể sử dụng:

```env
CATALOG_SYNC_LEGACY_DEMO=true
```

Chỉ bật sau khi sao lưu và tắt lại khi hoàn thành.

Không sử dụng chế độ này để di trú dữ liệu sản phẩm thật hoặc đơn hàng đang xử lý.

### Trạng thái đơn hàng

Khi tạo đơn:

- `PENDING` / `PENDING`: Đang chờ giữ hàng.
- `CONFIRMED` / `RESERVED`: Đã giữ hàng thành công.
- `CANCELLED` / `REJECTED`: Không đủ hàng.

Ứng dụng Flutter cần gọi:

`GET /api/v1/orders/{id}`

để cập nhật kết quả xử lý tồn kho.

Khi Catalog hoặc Cart tạm thời gặp lỗi, worker sẽ thử lại theo cấu hình:

`ORDER_INVENTORY_POLL_MS`

### Hủy đơn hàng

Endpoint:

`PATCH /api/v1/orders/{id}/cancel`

Người sở hữu đơn có thể hủy đơn ở trạng thái `PENDING` hoặc `CONFIRMED`.

Sau khi yêu cầu hủy:

1. Đơn chuyển sang `CANCELLED` / `RELEASE_PENDING`.
2. Worker thực hiện hoàn tồn kho.
3. Khi hoàn thành, `inventoryState=RELEASED`.
4. Order Service phát `ORDER_CANCELLED` lên Kafka.
5. Notification Service tạo thông báo hủy đơn.

Nếu đơn bị hủy trong khi đang giữ hàng, hệ thống hoàn kho sau khi thao tác giữ hàng kết thúc.

Hiện chưa có chức năng tự động đưa sản phẩm trở lại giỏ sau khi hủy.

### Báo giá đơn hàng

Endpoint:

`POST /api/v1/orders/quote`

Request:

```json
{
  "shippingType": "EXPRESS"
}
```

Response chứa:

- `subtotal`
- `shippingFee`
- `discount`
- `total`
- `currency`

Phí vận chuyển hiện tại:

| Loại giao hàng | Phí |
|---|---:|
| EXPRESS | 30.000 VND |
| STANDARD | 0 VND |
| Nhận tại cửa hàng | 0 VND |

### Tạo đơn hàng

Endpoint:

`POST /api/v1/orders`

Header bắt buộc:

`Idempotency-Key`

Key dài 8–128 ký tự, chỉ bao gồm chữ, số, `_` hoặc `-`.

Request:

```json
{
  "shippingType": "EXPRESS",
  "paymentType": "CASH_ON_DELIVERY",
  "recipientName": "Nguyễn An",
  "phone": "0908123456",
  "addressLine": "12 Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh"
}
```

Cùng người dùng và cùng key sẽ trả về cùng đơn hàng.

Sử dụng lại key với thông tin khác sẽ nhận HTTP `409 Conflict`.

`couponCode` khác rỗng bị từ chối cho đến khi backend khuyến mãi được hoàn thiện.

Các phương thức thanh toán trực tuyến chưa được hỗ trợ và sẽ bị từ chối.

## Phiên đăng nhập Mobile

### Đăng ký và đăng nhập

Endpoints:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`

Response bao gồm:

- `accessToken`
- `tokenType`
- `expiresIn`
- `refreshToken`
- `refreshExpiresIn`
- `user`

Thời gian mặc định:

| Token | Thời hạn |
|---|---|
| Access Token | 30 phút |
| Refresh Token | 30 ngày |

Refresh token được cấu hình bằng `AUTH_REFRESH_TTL`.

Chỉ lưu refresh token trong kho lưu trữ an toàn của thiết bị và không ghi token vào log.

### Refresh Token

Endpoint:

`POST /api/v1/auth/refresh`

Request:

```json
{
  "refreshToken": "..."
}
```

API trả về access token và refresh token mới.

Refresh token cũ chỉ được sử dụng một lần.

Nếu nhận HTTP `401`, ứng dụng yêu cầu người dùng đăng nhập lại.

### Logout

Endpoint:

`POST /api/v1/auth/logout`

Request:

```json
{
  "refreshToken": "..."
}
```

Response: HTTP `204 No Content`.

Ứng dụng cần xóa cả access token và refresh token khỏi thiết bị.

Access token đã phát vẫn hợp lệ cho đến khi hết hạn vì các service tự xác minh JWT.

### Đặt lại mật khẩu

Gọi `POST /api/v1/auth/password-reset/request` với `{"email":"..."}`. API trả
`204` cho cả email có và không có tài khoản. Nếu tài khoản tồn tại, mã 8 chữ số
được gửi qua SMTP; chỉ bản băm HMAC nằm trong collection
`ecommerce_auth.password_reset_challenges`. Mã hết hạn sau 10 phút, một mã chỉ
dùng một lần, tối đa 5 lần nhập sai và mỗi tài khoản chỉ nhận mã mới sau 1 phút.

Gọi `POST /api/v1/auth/password-reset/confirm` với `email`, `code`,
`newPassword` (8–72 ký tự, gồm chữ hoa, chữ thường và số). API trả `204` khi
thành công; mã sai, hết hạn hoặc đã dùng trả `400`. Các refresh token cũ bị
thu hồi khi mật khẩu đổi. Access token đã phát vẫn hợp lệ tối đa 30 phút theo
cấu hình JWT hiện tại. Khi phát hành production, đặt `PASSWORD_RESET_SECRET`
riêng dài ít nhất 32 ký tự và dùng SMTP thật; Mailpit chỉ dùng local.

### JWT Configuration

Auth Service ký JWT bằng:

`JWT_SECRET`

Các service xác minh JWT bằng cùng secret.

Thiết lập biến môi trường từ `.env.example`.

Gradle `bootRun` tự đọc file `.env` ở thư mục gốc dự án.

Thay secret phát triển trước khi triển khai production.

## Ghi chú Production

Dự án hiện là nền tảng Microservices trong giai đoạn phát triển.

Đã có:

- Quản lý tồn kho theo từng biến thể.
- Cập nhật giữ hàng nguyên tử.
- Hủy đơn và hoàn tồn kho.
- Xử lý bất đồng bộ với Kafka.
- Retry cho các tác vụ nền.
- JWT authentication.
- Phân tách database theo service.

Các hạng mục cần hoàn thiện trước production:

- Kiểm thử đa instance.
- Integration testing với MongoDB, Redis và Kafka.
- Transactional Outbox cho Kafka.
- Retry và Dead Letter Topic.
- Distributed Tracing.
- Rate Limiting tại Gateway.
- Secret Management.
- Thu hồi access token tức thời nếu nghiệp vụ yêu cầu.
- SMTP gửi email production và giám sát giao hàng email.

Flutter chế độ API thật dùng luồng email → mã khôi phục → mật khẩu mới; chế độ
demo dùng mã thử `12345678` và được ghi rõ trên màn hình.
