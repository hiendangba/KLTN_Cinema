# PHẦN MỞ ĐẦU

## 1. Lý do chọn đề tài

Trong xu thế chuyển đổi số hiện nay, công nghệ thông tin không chỉ hỗ trợ doanh nghiệp tin học hóa một số thao tác riêng lẻ, mà còn từng bước tái cấu trúc cách thức tổ chức quy trình, quản lý dữ liệu và cung cấp dịch vụ. Cùng với sự phổ biến của các nền tảng trực tuyến, kỳ vọng của người dùng đối với chất lượng phục vụ cũng thay đổi theo hướng rõ rệt: thông tin phải được cập nhật kịp thời, thao tác phải thuận tiện, kết quả phản hồi phải chính xác và các dịch vụ phải có khả năng hoạt động liên tục ở nhiều thời điểm khác nhau. Trong bối cảnh đó, các mô hình kinh doanh thuộc lĩnh vực dịch vụ giải trí, đặc biệt là rạp chiếu phim, chịu tác động trực tiếp của yêu cầu số hóa vì phần lớn tương tác giữa doanh nghiệp và khách hàng hiện nay đều diễn ra thông qua môi trường mạng.

Đối với hoạt động rạp chiếu phim, nhu cầu số hóa không chỉ xuất phát từ mong muốn cung cấp một kênh bán vé trực tuyến, mà còn bắt nguồn từ đặc thù vận hành của chính bài toán nghiệp vụ. Một hệ thống rạp chiếu phim phải đồng thời quản lý nhiều nhóm thông tin có liên hệ chặt chẽ với nhau, bao gồm danh mục phim, cụm rạp, phòng chiếu, sơ đồ ghế, suất chiếu, chính sách giá, khuyến mãi, tài khoản người dùng, đơn đặt vé và giao dịch thanh toán. Các đối tượng này không tồn tại độc lập mà liên tục tương tác trong những luồng xử lý có điều kiện ràng buộc cao. Chẳng hạn, một suất chiếu chỉ có thể được khởi tạo và khai thác khi dữ liệu về phim, phòng chiếu, thời lượng, khung giờ và tình trạng vận hành đã phù hợp; một đơn đặt vé chỉ có thể hoàn tất khi ghế ngồi còn khả dụng, giá vé được xác định đúng chính sách, thông tin người dùng hợp lệ và kết quả thanh toán được xác nhận. Điều đó cho thấy bài toán quản lý rạp chiếu phim thực chất là một bài toán hệ thống, trong đó dữ liệu, trạng thái và quy trình nghiệp vụ gắn với nhau một cách chặt chẽ.

Ở góc độ người dùng cuối, yêu cầu đối với một nền tảng đặt vé xem phim hiện đại cũng không còn dừng ở mức hiển thị danh sách phim hoặc lịch chiếu. Người dùng ngày nay mong muốn có thể chủ động tìm kiếm phim theo nhu cầu, xem thông tin chi tiết, lựa chọn cụm rạp phù hợp, theo dõi lịch chiếu, kiểm tra sơ đồ ghế, áp dụng ưu đãi và hoàn tất thanh toán trong một luồng thao tác liền mạch. Bất kỳ sự gián đoạn nào trong quá trình đó, chẳng hạn như thông tin không đồng nhất giữa các màn hình, trạng thái ghế cập nhật chậm, quy trình xác thực phức tạp hoặc phản hồi thanh toán thiếu rõ ràng, đều ảnh hưởng trực tiếp đến trải nghiệm sử dụng. Như vậy, từ góc nhìn dịch vụ, một hệ thống quản lý rạp chiếu phim không thể chỉ được xây dựng như một công cụ lưu trữ dữ liệu, mà cần được tổ chức như một nền tảng phục vụ đồng thời cả nhu cầu tra cứu, giao dịch và theo dõi của khách hàng.

Ở góc độ đơn vị vận hành, yêu cầu đặt ra còn rộng hơn và mang tính quản trị rõ rệt hơn. Một rạp chiếu phim hoặc chuỗi rạp cần theo dõi tình trạng khai thác của từng phòng chiếu, cấu hình sơ đồ ghế, lịch chiếu, giá vé theo từng khung giờ, chính sách khuyến mãi, doanh thu phát sinh và trạng thái của các giao dịch đang diễn ra. Trong thực tế, các nghiệp vụ này thường liên quan đến nhiều nhóm người dùng với phạm vi trách nhiệm khác nhau, bao gồm khách hàng, nhân viên hỗ trợ, quản lý tại cơ sở và quản trị viên ở mức hệ thống. Mỗi vai trò cần được cấp quyền truy cập phù hợp với chức năng nghiệp vụ của mình. Nếu không có cơ chế xác thực và phân quyền rõ ràng, hệ thống rất dễ phát sinh các thao tác vượt thẩm quyền, sai lệch dữ liệu hoặc khó khăn trong việc quy trách nhiệm khi có sự cố xảy ra. Vì vậy, bài toán đặt ra không chỉ là xây dựng đủ chức năng, mà còn là tổ chức hệ thống sao cho từng vai trò có thể sử dụng đúng phần việc của mình trong một môi trường dữ liệu thống nhất.

Trong nhiều đơn vị vận hành, khi các nghiệp vụ kể trên còn được tổ chức bằng thao tác thủ công hoặc phân tán trên nhiều công cụ rời rạc, quá trình quản lý thường bộc lộ hàng loạt hạn chế. Dữ liệu phim, lịch chiếu, giá vé, trạng thái ghế, đơn đặt vé và doanh thu nếu được lưu giữ ở các nguồn tách biệt sẽ gây khó khăn cho tra cứu, đối soát và cập nhật. Những thay đổi ở một khâu nghiệp vụ có thể không được phản ánh kịp thời sang các khâu còn lại, dẫn đến chênh lệch giữa dữ liệu hiển thị và dữ liệu thực tế. Trong bối cảnh số lượng suất chiếu tăng, lưu lượng truy cập lớn hoặc các chương trình khuyến mãi được áp dụng linh hoạt, các rủi ro này càng dễ phát sinh. Từ đó có thể thấy rằng nhu cầu xây dựng một hệ thống quản lý tập trung, có khả năng xử lý đồng bộ và kiểm soát trạng thái theo thời gian thực là một nhu cầu có cơ sở rõ ràng cả về mặt nghiệp vụ lẫn vận hành.

Từ góc nhìn của công nghệ phần mềm, đây là một bài toán phù hợp để tiếp cận theo hướng xây dựng hệ thống web hiện đại với sự phân tách rõ giữa lớp giao diện, lớp xử lý nghiệp vụ và lớp dữ liệu hỗ trợ. Bài toán đặt ra đồng thời nhiều yêu cầu có giá trị nghiên cứu và triển khai thực tế, như quản lý nhiều vai trò người dùng, tổ chức dữ liệu theo từng miền chức năng, kiểm soát tính nhất quán của giao dịch đặt vé, hỗ trợ thanh toán, xử lý bất đồng bộ cho một số tác vụ nền và tạo điều kiện cho việc mở rộng hệ thống trong tương lai. Đây là những vấn đề không chỉ phổ biến trong bài toán rạp chiếu phim, mà còn có ý nghĩa tham khảo đối với nhiều hệ thống thông tin dịch vụ khác có số lượng người dùng lớn và nhiều luồng nghiệp vụ liên kết.

Ngoài giá trị ứng dụng thực tiễn, đề tài còn có ý nghĩa rõ rệt về mặt học thuật và rèn luyện chuyên môn. Việc triển khai một hệ thống như vậy đòi hỏi phải trải qua nhiều bước từ khảo sát hiện trạng, xác định yêu cầu, phân tích tác nhân, mô hình hóa nghiệp vụ, thiết kế dữ liệu, lựa chọn kiến trúc, xây dựng giao diện, hiện thực các thành phần xử lý phía sau cho đến kiểm thử và đánh giá kết quả. Chuỗi hoạt động này phản ánh tương đối đầy đủ vòng đời phát triển của một sản phẩm phần mềm trong thực tế. Thông qua đề tài, người thực hiện có cơ hội vận dụng một cách tổng hợp các kiến thức đã học về lập trình web, cơ sở dữ liệu, phân tích và thiết kế hệ thống, kiến trúc phần mềm, phân quyền truy cập và kiểm thử phần mềm trên một bài toán có phạm vi đủ rộng và có mức độ gắn kết cao giữa các thành phần.

Từ những cơ sở nêu trên, đề tài “Xây dựng hệ thống website quản lý rạp chiếu phim” được lựa chọn nhằm nghiên cứu và hiện thực một nền tảng web phục vụ đồng thời hai mục tiêu: hỗ trợ khách hàng tra cứu và đặt vé thuận tiện, đồng thời hỗ trợ đơn vị vận hành quản lý dữ liệu và điều phối các nghiệp vụ cốt lõi của rạp chiếu phim. Việc lựa chọn đề tài này không chỉ xuất phát từ tính thời sự của nhu cầu số hóa dịch vụ, mà còn từ giá trị của bài toán trong việc thể hiện một quy trình xây dựng hệ thống phần mềm tương đối hoàn chỉnh, có cấu trúc rõ ràng và có khả năng tiếp tục phát triển trong các giai đoạn sau.

## 2. Mục đích nghiên cứu

Mục đích của đề tài là nghiên cứu và xây dựng một hệ thống website hỗ trợ quản lý, vận hành và đặt vé trong lĩnh vực rạp chiếu phim trên cơ sở phân tích bài toán nghiệp vụ và hiện thực hóa thành một sản phẩm phần mềm có cấu trúc rõ ràng. Trọng tâm của đề tài không chỉ là xây dựng một giao diện phục vụ đặt vé, mà là tổ chức được một hệ thống có khả năng liên kết giữa lớp tương tác người dùng, lớp xử lý nghiệp vụ và lớp dữ liệu hỗ trợ.

Trên phương diện chức năng, đề tài hướng đến việc hiện thực các nhóm nghiệp vụ chính của bài toán, bao gồm quản lý thông tin rạp chiếu, phòng chiếu, sơ đồ ghế, phim, suất chiếu, chính sách giá, khuyến mãi, tài khoản người dùng, đặt vé, thanh toán và báo cáo. Đồng thời, các chức năng này cần được tổ chức theo một cơ chế phân quyền phù hợp với từng vai trò sử dụng để bảo đảm rằng mỗi nhóm người dùng chỉ thao tác trong đúng phạm vi nghiệp vụ được giao.

Trên phương diện kỹ thuật, đề tài hướng đến việc vận dụng các kiến thức về phân tích và thiết kế hệ thống, xây dựng ứng dụng web, tổ chức dịch vụ phần mềm, quản lý dữ liệu và kiểm thử vào một bài toán có tính liên kết nghiệp vụ cao. Qua đó, đề tài góp phần làm rõ cách chuyển hóa yêu cầu nghiệp vụ thành các thành phần phần mềm cụ thể, đồng thời củng cố tư duy thiết kế và triển khai một hệ thống thông tin có khả năng vận hành trên thực tế.

## 3. Đối tượng và phạm vi nghiên cứu

### 3.1. Đối tượng nghiên cứu

Đề tài tập trung nghiên cứu hai nhóm đối tượng chính. Thứ nhất là các quy trình nghiệp vụ cốt lõi trong hoạt động của hệ thống rạp chiếu phim, bao gồm quản lý phim, rạp chiếu, phòng chiếu, ghế, suất chiếu, đặt vé, thanh toán và các chức năng hỗ trợ vận hành. Thứ hai là các công nghệ và phương pháp xây dựng hệ thống web hiện đại, trong đó chú trọng đến tổ chức kiến trúc phần mềm, quản lý dữ liệu, giao tiếp giữa các thành phần và bảo đảm tính an toàn trong xử lý truy cập.

### 3.2. Phạm vi chức năng và Phân quyền người dùng

Trong phạm vi đề tài, hệ thống được xây dựng cho bốn nhóm người dùng chính: Khách hàng, Nhân viên, Quản lý và Quản trị viên. Mỗi nhóm được gắn với một phạm vi chức năng riêng nhằm phản ánh đúng đặc thù nghiệp vụ và bảo đảm tính kiểm soát trong quá trình khai thác hệ thống.

Khách hàng là nhóm người dùng sử dụng hệ thống để đăng ký và quản lý tài khoản, tra cứu phim, lịch chiếu, rạp chiếu, sơ đồ ghế, thực hiện đặt vé, thanh toán và theo dõi lịch sử giao dịch. Đây là nhóm tác nhân đại diện cho luồng sử dụng dịch vụ từ phía người dùng cuối.

Nhân viên là nhóm người dùng tham gia hỗ trợ vận hành tại rạp trong phạm vi được phân công. Các chức năng của nhóm này tập trung vào tra cứu dữ liệu đặt vé, hỗ trợ kiểm tra thông tin giao dịch và thực hiện một số thao tác nghiệp vụ phục vụ hoạt động vận hành hằng ngày.

Quản lý là nhóm người dùng chịu trách nhiệm giám sát hoạt động tại rạp hoặc chi nhánh phụ trách. Nhóm này có phạm vi chức năng rộng hơn nhân viên, bao gồm quản lý một số danh mục nghiệp vụ liên quan đến rạp chiếu, phòng chiếu, suất chiếu, giá vé, sản phẩm dịch vụ đi kèm và theo dõi báo cáo vận hành trong phạm vi được giao.

Quản trị viên là nhóm người dùng có quyền quản lý ở mức toàn hệ thống. Nhóm này chịu trách nhiệm quản trị các danh mục dùng chung, tài khoản nội bộ, phân công nhân sự, khuyến mãi và các nội dung tổng hợp phục vụ công tác quản trị hệ thống.

Phạm vi nghiên cứu của đề tài tập trung vào việc xây dựng hệ thống website hỗ trợ các nhóm nghiệp vụ cốt lõi nêu trên theo mô hình phân quyền nhiều vai trò. Đề tài không đi sâu vào các nội dung ngoài phạm vi chính như tích hợp với các hệ thống doanh nghiệp quy mô lớn, phân tích dữ liệu chuyên sâu, tối ưu vận hành đa vùng hoặc triển khai hạ tầng ở mức thương mại hóa hoàn chỉnh.

## 4. Phương pháp nghiên cứu

Để thực hiện đề tài, nhiều phương pháp nghiên cứu được sử dụng kết hợp nhằm bảo đảm sự liên kết giữa cơ sở lý thuyết, phân tích bài toán và hiện thực hóa hệ thống.

Phương pháp nghiên cứu tài liệu được sử dụng để xây dựng nền tảng lý thuyết cho đề tài. Nội dung tham khảo bao gồm các tài liệu liên quan đến phát triển ứng dụng web, cơ sở dữ liệu, kiến trúc hệ thống phần mềm, phân quyền truy cập, thiết kế API và các mô hình tổ chức nghiệp vụ trong hệ thống thông tin.

Phương pháp khảo sát và phân tích nghiệp vụ được áp dụng thông qua việc tìm hiểu quy trình hoạt động của một số hệ thống rạp chiếu phim hiện có, từ đó nhận diện các nhóm chức năng chính, các tác nhân tham gia và những yêu cầu cần được hỗ trợ trong hệ thống đề xuất.

Phương pháp phân tích và thiết kế hệ thống được sử dụng để mô hình hóa bài toán thông qua các sơ đồ và mô hình biểu diễn phù hợp, bao gồm sơ đồ use case, sơ đồ lớp, sơ đồ trình tự và thiết kế cấu trúc dữ liệu. Đây là cơ sở để chuyển các yêu cầu nghiệp vụ thành thiết kế phần mềm cụ thể trước khi triển khai.

Phương pháp thực nghiệm được sử dụng trong giai đoạn hiện thực hóa và kiểm chứng hệ thống. Trên cơ sở thiết kế đã xây dựng, hệ thống được triển khai thử nghiệm, kiểm tra các chức năng cốt lõi và đối chiếu với các yêu cầu đã xác định để đánh giá mức độ phù hợp của giải pháp đề xuất.

## 5. Ý nghĩa khoa học và thực tiễn

### 5.1. Ý nghĩa khoa học

Về phương diện học thuật, đề tài góp phần vận dụng và hệ thống hóa các kiến thức đã học vào một bài toán cụ thể có độ phức tạp nghiệp vụ tương đối cao. Quá trình thực hiện đề tài làm rõ mối liên hệ giữa các bước khảo sát, xác định yêu cầu, phân tích tác nhân, thiết kế hệ thống, tổ chức dữ liệu, xây dựng thành phần phần mềm và kiểm thử trong một quy trình phát triển tương đối hoàn chỉnh.

Bên cạnh đó, đề tài tạo điều kiện để tiếp cận thực tế hơn với một số vấn đề điển hình trong phát triển hệ thống web hiện đại, như phân quyền nhiều vai trò, tổ chức chức năng theo từng miền nghiệp vụ, phối hợp giữa các thành phần xử lý và duy trì tính nhất quán dữ liệu trong các luồng thao tác có nhiều trạng thái.

### 5.2. Ý nghĩa thực tiễn

Về phương diện thực tiễn, đề tài hướng đến việc xây dựng một hệ thống có khả năng hỗ trợ số hóa các nghiệp vụ quan trọng trong vận hành rạp chiếu phim, từ quản lý danh mục dữ liệu, tổ chức suất chiếu, kiểm soát ghế ngồi cho đến đặt vé, thanh toán và theo dõi báo cáo. Hệ thống vì vậy có thể góp phần giảm sự phụ thuộc vào các thao tác thủ công rời rạc, hạn chế sai sót trong cập nhật dữ liệu và nâng cao khả năng kiểm soát thông tin trong quá trình vận hành.

Đồng thời, hệ thống còn tạo ra một nền tảng phục vụ cho cả hai phía của bài toán dịch vụ. Về phía khách hàng, hệ thống hỗ trợ tiếp cận thông tin và đặt vé thuận tiện hơn. Về phía vận hành, nhân viên, quản lý và quản trị viên có thêm công cụ để thực hiện nghiệp vụ, theo dõi trạng thái hoạt động và khai thác dữ liệu phục vụ công tác quản lý. Đây là cơ sở để hệ thống tiếp tục được hoàn thiện và mở rộng trong các giai đoạn phát triển tiếp theo.
