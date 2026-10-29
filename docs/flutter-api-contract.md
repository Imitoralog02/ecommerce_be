# Flutter ↔ Backend API v1 (MVP)

Flutter chỉ gọi gateway `/api/v1`. Các service nội bộ không phải endpoint của app. Payload tiền tệ dùng số VND (backend `BigDecimal`), không gửi giá tính ở client làm nguồn sự thật.

## Phạm vi có API thật

| Màn hình/chức năng | Backend | Ghi chú hợp đồng |
|---|---|---|
| Đăng ký/đăng nhập/hồ sơ | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `GET/PUT /me` | Login/register trả `accessToken`, `refreshToken`, `expiresIn`, `refreshExpiresIn`, `user`. `PUT /me` hiện cập nhật `fullName`, `phone`; email không chỉnh tại đây. |
| Catalog | `/products`, `/products/featured`, `/products/{id}`, `/categories`, `/products/{id}/availability` | Size/màu gửi đúng chuỗi `availableSizes`/`colorNames`; màu hex ở `availableColors`. Ảnh demo là đường dẫn asset Flutter. |
| Giỏ hàng | `/cart`, `/cart/items`, `/cart/items/{itemId}` | Sử dụng `itemId` do backend trả; tổng tiền ở response là dữ liệu server. Các thao tác ghi có kiểm tra xung đột; sau nhiều lần tranh chấp Redis, API trả `409 CONFLICT` để app tải lại giỏ và cho thử lại. |
| Sổ địa chỉ và wishlist | `/addresses`, `/wishlist` | Địa chỉ có các trường `streetAddress`, `ward`, `district`, `city`; không gửi `fullAddress` đã ghép. Mỗi tài khoản có tối đa một địa chỉ mặc định; nếu đổi mặc định đồng thời có thể nhận `409` và cần tải lại danh sách. Wishlist dùng cập nhật nguyên tử, giới hạn 100 sản phẩm và trả `409` khi đầy hoặc tranh chấp liên tiếp. |
| Checkout COD | `POST /orders/quote`, `POST /orders`, `GET /orders/by-request/{key}`, `POST /orders/by-request/{key}/abandon`, `GET /orders/{id}`, `PATCH /orders/{id}/cancel` | Tạo đơn yêu cầu `Idempotency-Key` ổn định và lưu key theo tài khoản cho đến khi xác minh được kết quả. `GET /orders/by-request/{key}` trả `404` nếu chưa tìm thấy đơn. Chỉ `paymentType=CASH_ON_DELIVERY`, chưa nhận coupon. |
| Vận hành đơn | `PATCH /orders/{id}/fulfillment` | Chỉ JWT có role `ADMIN`. Chuyển lần lượt `CONFIRMED → PROCESSING → SHIPPED → DELIVERED`; khi chuyển sang `SHIPPED` cần `trackingNumber`. Body gồm `status`, `trackingNumber`, `estimatedDelivery` (ngày `YYYY-MM-DD`). Trả `409` khi đơn vừa đổi trạng thái hoặc chưa giữ hàng. |
| Thông báo | `/notifications`, `/notifications/{id}/read` | Backend có `type`, `title`, `message`, `referenceId`, `read`, `createdAt`; icon và nhãn thời gian do Flutter trình bày. |

## Ánh xạ trạng thái đơn

| Backend | Flutter hiện tại | Cách xử lý |
|---|---|---|
| `PENDING` + `inventoryState=PENDING` | `pendingConfirmation` | Đơn đang chờ giữ hàng; đọc lại `GET /orders/{id}`. Chưa hiển thị như đơn chắc chắn được chấp nhận. |
| `CONFIRMED` + `RESERVED` | `processing` | Đã giữ hàng và chấp nhận COD. |
| `CANCELLED` + `REJECTED` | `cancelled` | Thiếu hàng; giỏ không bị xóa. |
| `CANCELLED` + `RELEASE_PENDING` | `cancelled` | Người dùng đã hủy, backend đang hoàn kho. |
| `CANCELLED` + `RELEASED` | `cancelled` | Đã hoàn kho. |
| `PROCESSING`, `SHIPPED`, `DELIVERED` | `processing`, `shipping`, `delivered` | API vận hành chuyển trạng thái theo thứ tự; app đọc `trackingNumber`, `estimatedDelivery` khi backend cung cấp. |

Khi `POST /orders`, app gửi `expectedCartItems: [{"id": "<cartItemId>", "quantity": 1}]` lấy từ giỏ vừa tải. Backend trả `409` nếu giỏ đã đổi trước khi tạo đơn.

`POST /orders` trả `201` với đơn `PENDING`, **không phải** xác nhận đã giữ được hàng. Sau khi có `id`, app phải theo dõi trạng thái hoặc cho người dùng xem lịch sử đơn. Nếu mất phản hồi, giữ key và tra cứu `GET /orders/by-request/{key}` trước. Khi cần đặt lại mà tra cứu vẫn `404`, gọi `POST /orders/by-request/{key}/abandon`: response `ORDER_FOUND` kèm `order` nghĩa là đơn cũ đã được tạo; `ABANDONED` nghĩa là backend đã chặn key cũ và app có thể tạo key mới. Marker `ABANDONED` không hiện trong lịch sử đơn. Nếu thao tác này lỗi, app giữ key và chưa tạo đơn mới.

## Chưa nối vào API thật

- Thanh toán thẻ/Apple Pay/MoMo/ZaloPay, voucher, ví/coin/quà tặng và quên mật khẩu: backend chưa hỗ trợ. Ẩn/khóa các lựa chọn này trong MVP hoặc triển khai service tương ứng trước khi bật.
- Tích hợp đơn vị vận chuyển và ETA tự động, đánh giá, đổi trả, live chat, visual search và thông báo khuyến mãi chưa có endpoint/backend đầy đủ. Không biến mock thành giao dịch hoặc trạng thái thật.
- Chế độ API thật đã ẩn các lựa chọn thanh toán mẫu ở checkout, ví/voucher và các mục demo chính trong tài khoản, nút đánh giá/đổi trả/chat ở chi tiết đơn, cùng nút tìm kiếm ảnh. Các màn demo còn lại cần được rà theo luồng phát hành trước khi công bố ứng dụng.
- Flutter đã dùng repository gọi API cho đăng nhập, catalog, giỏ, địa chỉ, wishlist, COD, lịch sử đơn và thông báo. Các tính năng còn lại trong danh sách này vẫn dùng mock hoặc lưu cục bộ; khi nối tiếp cần DTO/network data source và xử lý bất đồng bộ theo từng feature.

## Kiểm chứng bắt buộc trước khi thay provider

1. Chạy MongoDB, Redis, Kafka và các service; kiểm tra luồng qua gateway trên thiết bị/emulator.
2. Kiểm thử đầy đủ đăng nhập → thêm giỏ → quote → đặt COD → xác nhận/thiếu hàng → hủy → notification.
3. Thử gửi lại request với cùng key, gửi đồng thời nhiều thao tác trên một giỏ, restart service giữa lúc giữ hàng, và mất kết nối catalog/cart/Kafka.
4. Nạp tồn kho thật; `INVENTORY_DEMO_SEED_QUANTITY=20` chỉ dành cho catalog demo.
