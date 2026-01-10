package com.cinema.identity_service.dto.request;

import com.cinema.identity_service.entity.User;
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

public class RegisterCustomerRequest {
    @NotBlank(message = "Tên không được để trống")
    @Size(max = 100, message = "Tên quá dài không hợp lệ!")
    @Pattern(
            regexp = "^[\\p{L} .'-]+$",
            message = "Tên chứa ký tự không hợp lệ"
    )
    private String name;

    @Email(message = "Email không hợp lệ")
    @NotBlank(message = "Email không được để trống")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 32, message = "Mật khẩu phải từ 8 đến 32 ký tự")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
            message = "Mật khẩu phải có ít nhất 1 chữ hoa, 1 chữ thường, 1 chữ số và 1 ký tự đặc biệt"
    )
    private String password;

    @NotNull(message = "Ngày sinh không được để trống")
    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dob;

    @NotNull(message = "Giới tính không được để trống")
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private User.Gender gender;
}
