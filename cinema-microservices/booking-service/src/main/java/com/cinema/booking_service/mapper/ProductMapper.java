package com.cinema.booking_service.mapper;

import com.cinema.booking_service.dto.request.CreateProductRequest;
import com.cinema.booking_service.dto.request.UpdateProductRequest;
import com.cinema.booking_service.dto.response.ProductResponse;
import com.cinema.booking_service.entity.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProductMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cinemaId", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    Product toEntity(CreateProductRequest request);

    ProductResponse toResponse(Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "cinemaId", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "timeCreated", ignore = true)
    @Mapping(target = "timeUpdated", ignore = true)
    void updateEntityFromRequest(@MappingTarget Product product, UpdateProductRequest request);
}

