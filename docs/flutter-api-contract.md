# Flutter ↔ Backend API v1 (MVP)

Flutter chỉ gọi gateway `/api/v1`. Các service nội bộ không phải endpoint của app. Payload tiền tệ dùng số VND (backend `BigDecimal`), không gửi giá tính ở client làm nguồn sự thật.

## Phạm vi có API thật

| Màn hình/chức năng | Backend | Ghi chú hợp đồng |
|---|---|---|
| Đăng ký/đăng nhập/hồ sơ | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `/me` | Login/register trả `accessToken`, `refreshToken`, `expiresIn`, `refreshExpiresIn`, `user`. Lưu refresh token an toàn và thay bằng token mới sau mỗi lần refresh. |
| Catalog | `/products`, `/products/featured`, `/products/{id}`, `/categories`, `/products/{id}/availability` | Size/màu gửi đúng chuỗi `availableSizes`/`colorNames`; màu hex ở `availableColors`. Ảnh demo là đường dẫn asset Flutter. |
| Giỏ hàng | `/cart`, `/cart/items`, `/cart/items/{itemId}` | Sử dụng `itemId` do backend trả; tổng tiền ở response là dữ liệu server. |
| Sổ địa chỉ và wishlist | `/addresses`, `/wishlist` | Địa chỉ có các trường `streetAddress`, `ward`, `district`, `city`; không gửi `fullAddress` đã ghép. |
| Checkout COD | `POST /orders/quote`, `POST /orders`, `GET /orders/{id}`, `PATCH /orders/{id}/cancel` | Tạo đơn yêu cầu `Idempotency-Key` ổn định cho một lần bấm đặt hàng. Chỉ `paymentType=CASH_ON_DELIVERY`, chưa nhận coupon. |
| Thông báo | `/notifications`, `/notifications/{id}/read` | Backend có `type`, `title`, `message`, `referenceId`, `read`, `createdAt`; icon và nhãn thời gian do Flutter trình bày. |

## Ánh xạ trạng thái đơn

| Backend | Flutter hiện tại | Cách xử lý |
|---|---|---|
| `PENDING` + `inventoryState=PENDING` | `pendingConfirmation` | Đơn đang chờ giữ hàng; đọc lại `GET /orders/{id}`. Chưa hiển thị như đơn chắc chắn được chấp nhận. |
| `CONFIRMED` + `RESERVED` | `pendingConfirmation` hoặc `processing` | Đã giữ hàng và chấp nhận COD. Chốt một cách hiển thị trước khi nối UI. |
| `CANCELLED` + `REJECTED` | `cancelled` | Thiếu hàng; giỏ không bị xóa. |
| `CANCELLED` + `RELEASE_PENDING` | `cancelled` | Người dùng đã hủy, backend đang hoàn kho. |
| `CANCELLED` + `RELEASED` | `cancelled` | Đã hoàn kho. |
| `PROCESSING`, `SHIPPED`, `DELIVERED` | `processing`, `shipping`, `delivered` | Enum backend có nhưng chưa có API vận hành chuyển trạng thái/giao vận. |

`POST /orders` trả `201` với đơn `PENDING`, **không phải** xác nhận đã giữ được hàng. Sau khi có `id`, app phải theo dõi trạng thái hoặc cho người dùng xem lịch sử đơn. Dùng cùng `Idempotency-Key` khi retry do mất kết nối; không sinh key mới cho cùng một thao tác đặt hàng.

## Chưa nối vào API thật

- Thanh toán thẻ/Apple Pay/MoMo/ZaloPay, voucher, ví/coin/quà tặng và quên mật khẩu: backend chưa hỗ trợ. Ẩn/khóa các lựa chọn này trong MVP hoặc triển khai service tương ứng trước khi bật.
- Tracking chi tiết, đánh giá, đổi trả, live chat, visual search và thông báo khuyến mãi chưa có endpoint/backend đầy đủ. Không biến mock thành giao dịch hoặc trạng thái thật.
- Repository Flutter hiện dùng mock/lưu cục bộ; khi nối API cần thêm DTO/network data source, chuyển các thao tác đồng bộ thành `Future` phù hợp và thay Riverpod provider theo từng feature. Không chỉnh dữ liệu domain dựa trên giả định rằng response giống hệt entity mock.

## Kiểm chứng bắt buộc trước khi thay provider

1. Chạy MongoDB, Redis, Kafka và các service; kiểm tra luồng qua gateway trên thiết bị/emulator.
2. Kiểm thử đầy đủ đăng nhập → thêm giỏ → quote → đặt COD → xác nhận/thiếu hàng → hủy → notification.
3. Thử gửi lại request với cùng key, restart service giữa lúc giữ hàng, và mất kết nối catalog/cart/Kafka.
4. Nạp tồn kho thật; `INVENTORY_DEMO_SEED_QUANTITY=20` chỉ dành cho catalog demo.
