# Rà soát Chương 3 - Use case cần chỉnh

Tài liệu này chỉ dùng để rà soát và biên tập lại phần `3.3. Đặc tả use case` trong báo cáo. Không cắt bớt use case, không đổi phạm vi Chương 3, chỉ tập trung vào những mục cần làm rõ hơn để chương chắc và học thuật hơn.

## 1. Nhóm cần chỉnh tên hoặc description vì quá rộng

### 3.3.11. Quản lý hồ sơ cá nhân
- **Use case:** `3.3.11. Quản lý hồ sơ cá nhân`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Tên use case quá rộng; chưa cho thấy actor được cập nhật những trường nào và giới hạn chỉnh sửa ra sao.
- **Hướng chỉnh:** Giữ tên hiện tại nếu cần thống nhất, nhưng description nên nêu rõ phạm vi như cập nhật thông tin hồ sơ cá nhân trong giới hạn cho phép của vai trò.

### 3.3.12. Quản lý hồ sơ người dùng
- **Use case:** `3.3.12. Quản lý hồ sơ người dùng`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Tên và description quá khái quát; chưa phân biệt rõ với `3.3.11`, `3.3.13`, `3.3.14`.
- **Hướng chỉnh:** Làm rõ đây là cập nhật hồ sơ của người dùng khác theo quyền được cấp, không bao gồm xóa mềm hoặc khôi phục.

### 3.3.17. Quản lý thông tin rạp chiếu
- **Use case:** `3.3.17. Quản lý thông tin rạp chiếu`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Tên use case bao trùm nhiều thao tác; nếu description quá ngắn sẽ khiến phần đặc tả thiếu điểm tựa nghiệp vụ.
- **Hướng chỉnh:** Mô tả rõ đây là nhóm thao tác tạo mới, cập nhật, thay đổi trạng thái hoặc chỉnh sửa thông tin rạp trong phạm vi quản trị hệ thống.

### 3.3.18. Phân công nhân sự vào rạp chiếu
- **Use case:** `3.3.18. Phân công nhân sự vào rạp chiếu`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Description hiện quá ngắn, chưa thể hiện điều kiện phân công, kiểm tra trùng, hoặc quan hệ với quyền của quản lý/nhân viên.
- **Hướng chỉnh:** Bổ sung rõ mục tiêu nghiệp vụ là gán nhân sự phù hợp vào rạp chiếu và kiểm soát điều kiện phân công hợp lệ.

### 3.3.21. Quản lý phòng chiếu
- **Use case:** `3.3.21. Quản lý phòng chiếu`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Dễ bị lặp mẫu với quản lý rạp, quản lý phim, quản lý suất chiếu.
- **Hướng chỉnh:** Làm rõ phạm vi quản lý là thông tin định danh, sức chứa, trạng thái vận hành hoặc liên kết với rạp chiếu.

### 3.3.22. Quản lý sơ đồ ghế phòng chiếu
- **Use case:** `3.3.22. Quản lý sơ đồ ghế phòng chiếu`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Đây là use case có ý nghĩa nghiệp vụ hơn CRUD thông thường nhưng tên/description hiện dễ bị viết quá chung.
- **Hướng chỉnh:** Nhấn mạnh thao tác cấu hình bố cục ghế, loại ghế, vị trí ghế và ràng buộc nhất quán với phòng chiếu.

### 3.3.25. Quản lý thông tin phim
- **Use case:** `3.3.25. Quản lý thông tin phim`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Dễ trở thành mô tả CRUD rất chung, chưa cho thấy dữ liệu phim nào là trọng yếu.
- **Hướng chỉnh:** Làm rõ các nhóm thông tin quản lý như nội dung mô tả, thời lượng, phân loại, trạng thái khai thác và thời gian công chiếu.

### 3.3.31. Quản lý suất chiếu
- **Use case:** `3.3.31. Quản lý suất chiếu`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Đây là use case quan trọng nhưng tên quá rộng; nếu đặc tả viết chung chung sẽ làm mất chiều sâu nghiệp vụ.
- **Hướng chỉnh:** Làm rõ ràng buộc thời gian, phòng chiếu, phim, trạng thái suất chiếu và điều kiện tránh chồng lấp.

### 3.3.32. Quản lý chính sách giá vé
- **Use case:** `3.3.32. Quản lý chính sách giá vé`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Dễ bị mô tả như CRUD thuần túy, chưa nêu được logic áp dụng giá.
- **Hướng chỉnh:** Bổ sung rõ việc thiết lập mức giá theo điều kiện áp dụng như loại ghế, khung giờ hoặc suất chiếu.

### 3.3.40. Quản lý sản phẩm bắp nước
- **Use case:** `3.3.40. Quản lý sản phẩm bắp nước`
- **Mức độ ưu tiên:** Thấp
- **Vấn đề:** Tên đủ hiểu nhưng dễ bị mô tả quá giống các use case quản lý danh mục khác.
- **Hướng chỉnh:** Chỉ cần làm rõ đây là quản lý danh mục sản phẩm, giá bán và trạng thái kinh doanh trong phạm vi rạp.

### 3.3.49. Quản lý khuyến mãi
- **Use case:** `3.3.49. Quản lý khuyến mãi`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Use case quan trọng với thanh toán nhưng nếu description quá rộng sẽ không thể hiện được điều kiện áp dụng và thời gian hiệu lực.
- **Hướng chỉnh:** Mô tả rõ việc tạo/cập nhật chương trình khuyến mãi cùng các điều kiện áp dụng, thời gian hiệu lực và trạng thái hoạt động.

## 2. Nhóm cần làm rõ ranh giới với use case khác

### 3.3.15 và 3.3.16
- **Use case:** `3.3.15. Tra cứu và xem thông tin rạp chiếu` và `3.3.16. Tra cứu danh sách rạp chiếu của tôi`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Hai use case dễ bị hiểu trùng nhau nếu không làm rõ khác biệt về actor và phạm vi dữ liệu.
- **Hướng chỉnh:** Nêu rõ `3.3.15` là tra cứu thông tin rạp ở phạm vi chung; `3.3.16` là danh sách rạp gắn với actor nội bộ đang đăng nhập.

### 3.3.20, 3.3.23 và 3.3.29
- **Use case:** `3.3.20`, `3.3.23`, `3.3.29`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Ba use case liên quan phòng chiếu/ghế rất dễ chồng lấn về đầu vào và đầu ra.
- **Hướng chỉnh:** Tách rõ:
  - `3.3.20` xem thông tin phòng chiếu ở mức danh mục
  - `3.3.23` xem sơ đồ ghế của phòng chiếu tĩnh
  - `3.3.29` xem sơ đồ ghế của một suất chiếu cụ thể theo trạng thái thời gian thực

### 3.3.26 và 3.3.27
- **Use case:** `3.3.26. Tra cứu lịch chiếu` và `3.3.27. Tra cứu lịch chiếu theo phim`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Dễ bị xem là hai tên gọi của cùng một thao tác tra cứu.
- **Hướng chỉnh:** Làm rõ `3.3.26` là tra cứu lịch chiếu theo nhiều tiêu chí tổng quát; `3.3.27` là tra cứu tập trung từ điểm bắt đầu là một phim cụ thể.

### 3.3.34, 3.3.35, 3.3.36 và 3.3.37
- **Use case:** `3.3.34`, `3.3.35`, `3.3.36`, `3.3.37`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Cụm use case về đơn đặt vé rất dễ lặp ý nếu không tách rõ theo mục tiêu tra cứu và actor.
- **Hướng chỉnh:** Làm rõ:
  - `3.3.34` xem chi tiết một đơn cụ thể
  - `3.3.35` xem các đơn đang hoạt động của khách hàng
  - `3.3.36` xem lịch sử đặt vé của chính khách hàng
  - `3.3.37` xem đơn theo rạp ở góc độ quản trị/vận hành

### 3.3.41 và 3.3.47
- **Use case:** `3.3.41. Xem báo cáo doanh thu đặt vé` và `3.3.47. Xem báo cáo doanh thu thanh toán`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Hai use case báo cáo doanh thu dễ bị đánh giá trùng khái niệm nếu không nói rõ nguồn dữ liệu và chỉ số thống kê.
- **Hướng chỉnh:** Một use case cần bám doanh thu từ booking; use case còn lại bám doanh thu từ giao dịch thanh toán thành công.

## 3. Nhóm nghiệp vụ lõi cần siết lại điều kiện và luồng

### 3.3.1. Đăng ký tài khoản khách hàng
- **Use case:** `3.3.1. Đăng ký tài khoản khách hàng`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Đây là use case lõi; nếu precondition và hậu điều kiện chưa chặt thì phần xác thực đầu vào sẽ yếu.
- **Hướng chỉnh:** Làm rõ hơn trạng thái OTP, điều kiện email chưa tồn tại, và việc xóa trạng thái tạm sau xác thực thành công.

### 3.3.2. Đặt lại mật khẩu
- **Use case:** `3.3.2. Đặt lại mật khẩu`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Cần tránh mô tả quá giống với đăng ký tài khoản vì cùng dùng OTP.
- **Hướng chỉnh:** Nhấn khác biệt ở mục tiêu nghiệp vụ, điều kiện tài khoản đã tồn tại và hậu quả sau khi cập nhật mật khẩu mới.

### 3.3.4. Đăng nhập bằng Google
- **Use case:** `3.3.4. Đăng nhập bằng Google`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Đây là use case tích hợp ngoài; nếu mô tả sơ sài sẽ thiếu chiều sâu kỹ thuật và nghiệp vụ.
- **Hướng chỉnh:** Làm rõ callback, xác thực token Google, đồng bộ hoặc khởi tạo hồ sơ, rồi mới cấp phiên đăng nhập.

### 3.3.7. Tạo tài khoản nhân sự
- **Use case:** `3.3.7. Tạo tài khoản nhân sự`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Use case này liên quan đồng bộ giữa xác thực và hồ sơ người dùng, nên cần mô tả chặt hơn CRUD thường.
- **Hướng chỉnh:** Nhấn bước tạo tài khoản nội bộ, gán vai trò và đồng bộ thông tin nhân sự sang miền người dùng.

### 3.3.33. Đặt vé xem phim
- **Use case:** `3.3.33. Đặt vé xem phim`
- **Mức độ ưu tiên:** Rất cao
- **Vấn đề:** Đây là use case trung tâm của toàn hệ thống; nếu luồng chính và ngoại lệ chưa chắc thì Chương 3 mất điểm rõ rệt.
- **Hướng chỉnh:** Siết chặt điều kiện chọn ghế hợp lệ, kiểm tra trạng thái suất chiếu, tạo đơn và giữ nhất quán trạng thái ghế/booking.

### 3.3.38. Hủy đơn đặt vé
- **Use case:** `3.3.38. Hủy đơn đặt vé`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Use case này cần thể hiện rõ điều kiện được hủy và ảnh hưởng đến trạng thái ghế/đơn.
- **Hướng chỉnh:** Làm rõ điều kiện hủy hợp lệ, cập nhật trạng thái booking và giải phóng tài nguyên liên quan nếu có.

### 3.3.43. Thanh toán đơn đặt vé
- **Use case:** `3.3.43. Thanh toán đơn đặt vé`
- **Mức độ ưu tiên:** Rất cao
- **Vấn đề:** Là một trong những use case chứng minh chiều sâu của hệ thống; không nên mô tả ở mức quá chung.
- **Hướng chỉnh:** Nhấn rõ khởi tạo phiên thanh toán, xác nhận giao dịch, cập nhật trạng thái đơn và ghi nhận kết quả.

### 3.3.45. Xem trước khuyến mãi khi thanh toán
- **Use case:** `3.3.45. Xem trước khuyến mãi khi thanh toán`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Nếu mô tả quá ngắn sẽ không thấy được vai trò của nó trong luồng thanh toán.
- **Hướng chỉnh:** Làm rõ đầu vào là thông tin đơn và dữ liệu ưu đãi; đầu ra là mức giảm giá dự kiến trước khi thanh toán.

## 4. Nhóm cần xác minh lại actor hoặc phạm vi quyền

### 3.3.9. Tra cứu danh sách người dùng
- **Use case:** `3.3.9. Tra cứu danh sách người dùng`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Actor hiện gồm `Quản trị viên, Quản lý, Nhân viên`; cần xác minh xem nhân viên có thực sự được phép xem danh sách rộng đến đâu.
- **Hướng chỉnh:** Đối chiếu với rule nghiệp vụ hoặc codebase, sau đó ghi rõ phạm vi dữ liệu mà từng actor được tra cứu.

### 3.3.10. Xem chi tiết hồ sơ người dùng
- **Use case:** `3.3.10. Xem chi tiết hồ sơ người dùng`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Tương tự `3.3.9`, cần kiểm tra xem nhân viên/quản lý xem được hồ sơ của ai.
- **Hướng chỉnh:** Nếu quyền khác nhau theo vai trò, cần phản ánh điều đó ngay trong precondition hoặc flow.

### 3.3.12. Quản lý hồ sơ người dùng
- **Use case:** `3.3.12. Quản lý hồ sơ người dùng`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Actor hiện gồm cả `Nhân viên`; đây là quyền nhạy cảm và cần kiểm tra lại tính hợp lý.
- **Hướng chỉnh:** Xác minh theo nghiệp vụ; nếu nhân viên chỉ được chỉnh hạn chế thì phải ghi rõ phạm vi thay vì để actor rộng.

### 3.3.34. Xem chi tiết đơn đặt vé
- **Use case:** `3.3.34. Xem chi tiết đơn đặt vé`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Actor trải rộng từ khách hàng đến quản trị viên; cần phân biệt mỗi vai trò xem đơn theo phạm vi nào.
- **Hướng chỉnh:** Nêu rõ khách hàng chỉ xem đơn của mình, còn nội bộ xem theo phạm vi quản trị được cấp.

### 3.3.44. Xem chi tiết phiên thanh toán
- **Use case:** `3.3.44. Xem chi tiết phiên thanh toán`
- **Mức độ ưu tiên:** Trung bình
- **Vấn đề:** Đây là dữ liệu nhạy cảm; cần làm rõ phạm vi xem của từng actor.
- **Hướng chỉnh:** Mô tả rõ giới hạn truy cập theo chính đơn của khách hàng hoặc theo quyền quản trị nội bộ.

### 3.3.46. Xem lịch sử giao dịch thanh toán
- **Use case:** `3.3.46. Xem lịch sử giao dịch thanh toán`
- **Mức độ ưu tiên:** Cao
- **Vấn đề:** Actor rất rộng, dễ tạo cảm giác quyền xem lịch sử thanh toán đang được mở quá lớn.
- **Hướng chỉnh:** Xác minh lại quyền theo code/thiết kế; nếu đúng thì cần ghi rõ phạm vi dữ liệu trả về theo từng vai trò.

## Ghi chú sử dụng

- Không cần chỉnh toàn bộ 49 use case.
- Ưu tiên chỉnh trước các mục có mức độ **Cao** và **Rất cao**.
- Với các use case lõi, mục tiêu là **siết logic và điều kiện nghiệp vụ**, không phải rút ngắn.
- Với các use case CRUD, mục tiêu là **làm rõ phạm vi và điểm khác biệt**, không phải cắt bỏ.
