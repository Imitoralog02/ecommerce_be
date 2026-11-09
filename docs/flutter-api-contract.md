# Flutter ↔ Backend API v1 (MVP)

Flutter chỉ gọi gateway `/api/v1`. Các service nội bộ không phải endpoint của app. Payload tiền tệ dùng số VND (backend `BigDecimal`), không gửi giá tính ở client làm nguồn sự thật.

## Phạm vi có API thật

| Màn hình/chức năng | Backend | Ghi chú hợp đồng |
|---|---|---|
| Đăng ký/đăng nhập/hồ sơ | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, `GET/PUT /me` | Login/register trả `accessToken`, `refreshToken`, `expiresIn`, `refreshExpiresIn`, `user`. `PUT /me` hiện cập nhật `fullName`, `phone`; email không chỉnh tại đây. |
| Đặt lại mật khẩu | `POST /auth/password-reset/request`, `POST /auth/password-reset/confirm` | Request nhận `email`, luôn trả `204` với email hợp lệ. Confirm nhận `email`, `code` 8 chữ số, `newPassword`; trả `204` khi thành công, `400` khi mã sai/hết hạn. Mã gửi qua email, hạn 10 phút, tối đa 5 lần sai, giới hạn gửi lại 1 phút. Sau thành công app xóa phiên cũ và chuyển về đăng nhập. |
| Catalog | `/products`, `/products/featured`, `/products/{id}`, `/categories`, `/products/{id}/availability` | Size/màu gửi đúng chuỗi `availableSizes`/`colorNames`; màu hex ở `availableColors`. Ảnh demo là đường dẫn asset Flutter. |
| Flash Sale | `GET /flash-sales/active` | Trả chiến dịch đang diễn ra cùng sản phẩm và giá sale. Cùng giá này được trả bởi `/products` và dùng khi giỏ hàng tải lại sản phẩm; đơn COD luôn tính lại theo giỏ server. |
| Giỏ hàng | `/cart`, `/cart/items`, `/cart/items/{itemId}` | Sử dụng `itemId` do backend trả; tổng tiền ở response là dữ liệu server. Các thao tác ghi có kiểm tra xung đột; sau nhiều lần tranh chấp Redis, API trả `409 CONFLICT` để app tải lại giỏ và cho thử lại. |
| Sổ địa chỉ và wishlist | `/addresses`, `/wishlist` | Địa chỉ có các trường `streetAddress`, `ward`, `district`, `city`; không gửi `fullAddress` đã ghép. Mỗi tài khoản có tối đa một địa chỉ mặc định; nếu đổi mặc định đồng thời có thể nhận `409` và cần tải lại danh sách. Wishlist dùng cập nhật nguyên tử, giới hạn 100 sản phẩm và trả `409` khi đầy hoặc tranh chấp liên tiếp. |
| Checkout COD | `POST /orders/quote`, `POST /orders`, `GET /orders/by-request/{key}`, `POST /orders/by-request/{key}/abandon`, `GET /orders/{id}`, `PATCH /orders/{id}/cancel` | Tạo đơn yêu cầu `Idempotency-Key` ổn định và lưu key theo tài khoản cho đến khi xác minh được kết quả. `GET /orders/by-request/{key}` trả `404` nếu chưa tìm thấy đơn. Chỉ `paymentType=CASH_ON_DELIVERY`; `couponCode` tùy chọn được server kiểm tra và tính lại khi tạo đơn. |
| Mã ưu đãi | `GET /promotions` | Trả các mã đang có hiệu lực cùng `available`, `remainingUses` theo tài khoản đăng nhập. Checkout dùng danh sách này để gợi ý mã, vẫn kiểm tra lại qua `/orders/quote`. |
| Vận hành đơn | `PATCH /orders/{id}/fulfillment` | Chỉ JWT có role `ADMIN`. Chuyển lần lượt `CONFIRMED → PROCESSING → SHIPPED → DELIVERED`; khi chuyển sang `SHIPPED` cần `trackingNumber`. Body gồm `status`, `trackingNumber`, `estimatedDelivery` (ngày `YYYY-MM-DD`). Trả `409` khi đơn vừa đổi trạng thái hoặc chưa giữ hàng. |
| Thông báo | `/notifications`, `/notifications/{id}/read` | Backend có `type`, `title`, `message`, `referenceId`, `read`, `createdAt`; icon và nhãn thời gian do Flutter trình bày. |

Ở chế độ API thật, số điện thoại tài khoản được đọc/ghi qua `GET/PUT /me` trong `ecommerce_auth.users`; số điện thoại người nhận và địa chỉ được đọc/ghi qua `/addresses` trong `ecommerce_customer.shipping_addresses`. Checkout dùng địa chỉ được chọn và lưu bản chụp người nhận vào đơn hàng. Danh sách địa phương trong Flutter chỉ là gợi ý; người dùng có thể nhập địa phương khác, và API lưu chính chuỗi đã nhập.

Khuyến mãi COD nằm trong `ecommerce_order.promotions`. Dữ liệu demo `NEORA10`, `NEORA30K`, `FREESHIP` chỉ được thêm nếu chưa tồn tại; đặt `PROMOTION_DEMO_SEED=false` để tắt. Mỗi mã demo tối đa 1.000 lượt tổng cộng và một lượt cho mỗi tài khoản. Bộ khởi tạo nâng cấp bản ghi demo cũ và tính lượt từ các đơn chưa hủy. `POST /orders/quote` nhận `{"shippingType":"EXPRESS","couponCode":"NEORA10"}` và trả `subtotal`, `shippingFee`, `discount`, `total`, `currency`. `POST /orders` dùng cùng mã, tính lại theo giỏ hiện tại và trả `couponCode` trên đơn. Việc giữ lượt dùng được cập nhật nguyên tử trên một bản ghi MongoDB; khi hủy đơn, lượt được hoàn ngay và worker định kỳ đối soát đơn thiếu hàng hoặc yêu cầu bị ngắt giữa chừng. Mã hết hạn, không đủ giá trị tối thiểu hoặc không phù hợp cách giao hàng trả lỗi 400; mã hết lượt trả 409.

Chiến dịch Flash Sale ở `ecommerce_catalog.flash_sales`: mỗi bản ghi có `startsAt`, `endsAt`, `active`, và `offers: [{productId, salePrice, quota}]`. `quota` là hạn mức cho từng sản phẩm; offer cũ chưa có trường này dùng mặc định 20 lượt. Khi đang hoạt động, giá hợp lệ phải lớn hơn 0 và thấp hơn giá sản phẩm thường. Catalog trả giá sale trong `price`, giá trước sale trong `originalPrice`, cùng `flashSaleId` và `flashSaleRemaining`. Khi hết lượt, catalog trả giá thường và bỏ sản phẩm khỏi danh sách sale. Cart và order không dùng giá từ client; đơn lưu `flashSaleId` để worker giữ lượt đúng chương trình, trừ lượt bằng cập nhật MongoDB có điều kiện, hoàn lượt khi hủy và chốt lượt khi giao xong. Trạng thái nằm trong `ecommerce_catalog.flash_sale_allocations`, khóa `_id` dạng `campaignId:productId`, gồm `capacity`, `available`, `reservations`. Hạn mức được tạo khi sản phẩm sale được đọc lần đầu; sau đó thay đổi `quota` trong offer không tự đặt lại hạn mức đã dùng. Dữ liệu demo `demo_week` được tạo một lần trong 7 ngày từ lúc catalog khởi động lần đầu; đặt `FLASH_SALE_DEMO_SEED=false` để tắt. Sau khi hết hạn, cần tạo hoặc cập nhật chiến dịch trong MongoDB để có đợt mới. Màn Flutter chế độ API thật hiển thị `flashSaleRemaining`, tải lại danh sách mỗi 15 giây và kiểm tra giá mới nhất khi mở chi tiết; không tạo giả số lượng đã bán.

## Ánh xạ trạng thái đơn

| Backend | Flutter hiện tại | Cách xử lý |
|---|---|---|
| `PENDING` + `inventoryState=PENDING` | `pendingConfirmation` | Đơn đang chờ giữ hàng; đọc lại `GET /orders/{id}`. Chưa hiển thị như đơn chắc chắn được chấp nhận. |
| `CONFIRMED` + `RESERVED` | `processing` | Đã giữ hàng và chấp nhận COD. |
| `CANCELLED` + `REJECTED` | `cancelled` | Thiếu hàng; giỏ không bị xóa. |
| `CANCELLED` + `RELEASE_PENDING` | `cancelled` | Người dùng đã hủy, backend đang hoàn kho. |
| `CANCELLED` + `RELEASED` | `cancelled` | Đã hoàn kho. |
| `PROCESSING`, `SHIPPED`, `DELIVERED` | `processing`, `shipping`, `delivered` | API vận hành chuyển trạng thái theo thứ tự; app đọc `trackingNumber`, `estimatedDelivery` khi backend cung cấp. |

Với đơn `DELIVERED`, `inventoryState` có thể tạm là `RESERVED` trong lúc worker chốt tồn kho, sau đó là `FINALIZED`. Trạng thái giao hàng trên app không phụ thuộc vào bước chốt tồn kho này.

Khi `POST /orders`, app gửi `expectedCartItems: [{"id": "<cartItemId>", "quantity": 1}]` lấy từ giỏ vừa tải. Backend trả `409` nếu giỏ đã đổi trước khi tạo đơn.

`POST /orders` trả `201` với đơn `PENDING`, **không phải** xác nhận đã giữ được hàng. Sau khi có `id`, app phải theo dõi trạng thái hoặc cho người dùng xem lịch sử đơn. Nếu mất phản hồi, giữ key và tra cứu `GET /orders/by-request/{key}` trước. Khi cần đặt lại mà tra cứu vẫn `404`, gọi `POST /orders/by-request/{key}/abandon`: response `ORDER_FOUND` kèm `order` nghĩa là đơn cũ đã được tạo; `ABANDONED` nghĩa là backend đã chặn key cũ và app có thể tạo key mới. Marker `ABANDONED` không hiện trong lịch sử đơn. Nếu thao tác này lỗi, app giữ key và chưa tạo đơn mới.

## Chưa nối vào API thật

- Thanh toán thẻ/Apple Pay/MoMo/ZaloPay và ví/coin/quà tặng: backend chưa hỗ trợ. Quên mật khẩu đã có API và email local qua Mailpit; khi phát hành cần cấu hình SMTP production. Voucher COD cơ bản đã có ở backend; ví voucher và các loại mã phức tạp trên app vẫn là demo. Ẩn/khóa các lựa chọn chưa hỗ trợ trước khi phát hành.
- Tích hợp đơn vị vận chuyển và ETA tự động, đánh giá, đổi trả, live chat, visual search và thông báo khuyến mãi chưa có endpoint/backend đầy đủ. Không biến mock thành giao dịch hoặc trạng thái thật.
- Chế độ API thật có ô nhập mã và danh sách mã đang có từ `GET /promotions` tại checkout; app gọi `POST /orders/quote` để kiểm tra và gửi cùng mã khi tạo đơn. Màn ví voucher đọc API và chỉ cho sao chép mã, không hiển thị xu/hạng/thao tác lưu voucher giả. Màn Flash Sale đọc API. Các mã demo phức tạp, lựa chọn thanh toán mẫu, nút đánh giá/đổi trả/chat ở chi tiết đơn và tìm kiếm ảnh chưa bật. Các màn demo còn lại cần được rà theo luồng phát hành trước khi công bố ứng dụng.
- Flutter đã dùng repository gọi API cho đăng nhập, catalog, giỏ, địa chỉ, wishlist, COD, lịch sử đơn và thông báo. Các tính năng còn lại trong danh sách này vẫn dùng mock hoặc lưu cục bộ; khi nối tiếp cần DTO/network data source và xử lý bất đồng bộ theo từng feature.

## Kiểm chứng bắt buộc trước khi thay provider

1. Chạy MongoDB, Redis, Kafka và các service; kiểm tra luồng qua gateway trên thiết bị/emulator.
2. Kiểm thử đầy đủ đăng nhập → thêm giỏ → quote → đặt COD → xác nhận/thiếu hàng → hủy → notification.
3. Thử gửi lại request với cùng key, gửi đồng thời nhiều thao tác trên một giỏ, restart service giữa lúc giữ hàng, và mất kết nối catalog/cart/Kafka.
4. Nạp tồn kho thật; `INVENTORY_DEMO_SEED_QUANTITY=20` chỉ dành cho catalog demo.
