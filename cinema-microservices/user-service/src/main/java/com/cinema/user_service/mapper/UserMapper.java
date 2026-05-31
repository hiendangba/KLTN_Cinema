package com.cinema.user_service.mapper;

import com.cinema.user_service.dto.request.*;
import com.cinema.user_service.dto.response.UserResponse;
import com.cinema.user_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "bankCode", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "accountName", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    User toUser(RegisterCustomerRequest registerCustomerRequest);

    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    User toUserManager(RegisterManagerRequest registerManagerRequest);

    //Tự tạo ra Object nên cần Annotation @Mapping để bỏ qua các trường không có dữ liệu
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    User toUserStaff(RegisterStaffRequest registerStaffRequest);

    //Map vào object đã có sẳn update object
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "bankCode", ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "accountName", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateUserCustomer(@MappingTarget User user, UpdateCustomerRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateUserManager(@MappingTarget User user, UpdateManagerRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateUserStaff(@MappingTarget User user, UpdateStaffRequest request);

    UserResponse toUserResponse(User user);
    List<UserResponse> toUserResponseList(List<User> users);
}
