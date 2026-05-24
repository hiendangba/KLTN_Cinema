package com.cinema.booking_service.services;

import com.cinema.booking_service.dto.request.CreateProductRequest;
import com.cinema.booking_service.dto.request.ProductField;
import com.cinema.booking_service.dto.request.UpdateProductRequest;
import com.cinema.booking_service.dto.response.ProductResponse;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public interface ProductService {
    ActionMessageResponse createProduct(CreateProductRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateProduct(UUID id, UpdateProductRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteProduct(UUID id, HttpServletRequest httpRequest);

    ProductResponse getProductById(UUID id, HttpServletRequest httpRequest);

    PageResponse<ProductResponse> getProductsByOperatorCinema(
            PageRequest<ProductField> request,
            HttpServletRequest httpRequest);

    PageResponse<ProductResponse> getProductsByCinemaId(UUID cinemaId, PageRequest<ProductField> request);
}
