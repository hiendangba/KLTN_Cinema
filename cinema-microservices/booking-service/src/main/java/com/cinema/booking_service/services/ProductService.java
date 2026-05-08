package com.cinema.booking_service.services;

import com.cinema.booking_service.dto.request.CreateProductRequest;
import com.cinema.booking_service.dto.request.UpdateProductRequest;
import com.cinema.booking_service.dto.response.ProductResponse;
import com.cinema.dto.response.ActionMessageResponse;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.UUID;

public interface ProductService {
    ActionMessageResponse createProduct(CreateProductRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse updateProduct(UUID id, UpdateProductRequest request, HttpServletRequest httpRequest);

    ActionMessageResponse deleteProduct(UUID id, HttpServletRequest httpRequest);

    ProductResponse getProductById(UUID id);

    List<ProductResponse> getProductsByOperatorCinema(HttpServletRequest httpRequest);

    List<ProductResponse> getProductsByCinemaId(UUID cinemaId);
}
