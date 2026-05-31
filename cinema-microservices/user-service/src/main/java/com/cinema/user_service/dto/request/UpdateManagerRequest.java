package com.cinema.user_service.dto.request;

import com.cinema.Enum.UserEnum;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Builder
@Getter
@Setter
@AllArgsConstructor

public class UpdateManagerRequest {

    @NotBlank(message = "Tên không được để trống")
    @Size(max = 100, message = "Tên quá dài không hợp lệ!")
    @Pattern(regexp = "^[\\p{L} .'-]+$", message = "Tên chứa ký tự không hợp lệ")
    private String name;

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    private String email;

    @NotNull(message = "Ngày sinh không được để trống")
    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dob;

    @NotNull(message = "Giới tính không được để trống")
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private UserEnum.Gender gender;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(regexp = "^(0[0-9]{9}|\\+84[0-9]{9})$", message = "Số điện thoại không hợp lệ")
    @Column(length = 15)
    private String phone;

    @Size(max = 50, message = "Mã ngân hàng tối đa 50 ký tự")
    private String bankCode;

    @Size(max = 50, message = "Số tài khoản tối đa 50 ký tự")
    private String accountNumber;

    @Size(max = 255, message = "Tên chủ tài khoản tối đa 255 ký tự")
    private String accountName;

    @Size(max = 32, message = "Mật khẩu cũ tối đa 32 ký tự")
    private String oldPassword;

    @Size(min = 8, max = 32, message = "Mật khẩu mới phải từ 8 đến 32 ký tự")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Mật khẩu mới phải có ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt"
    )
    private String newPassword;
}
