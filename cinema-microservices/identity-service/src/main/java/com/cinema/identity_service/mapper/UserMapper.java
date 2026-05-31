package com.cinema.identity_service.mapper;

import com.cinema.identity_service.dto.request.RegisterCustomerRequest;
import com.cinema.identity_service.dto.request.RegisterManagerRequest;
import com.cinema.identity_service.dto.request.RegisterStaffRequest;
import com.cinema.identity_service.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface UserMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "provider", constant = "LOCAL")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "role", constant = "CUSTOMER")
    User toUser(RegisterCustomerRequest registerCustomerRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "provider", constant = "LOCAL")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "role", constant = "MANAGER")
    User toUser(RegisterManagerRequest registerManagerRequest);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    @Mapping(target = "providerId", ignore = true)
    @Mapping(target = "provider", constant = "LOCAL")
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "role", constant = "STAFF")
    User toUser(RegisterStaffRequest registerStaffRequest);
}
