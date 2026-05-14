package com.cinema.booking_service.controller;

import com.cinema.booking_service.dto.request.CreateProductRequest;
import com.cinema.booking_service.dto.request.ProductField;
import com.cinema.booking_service.dto.request.UpdateProductRequest;
import com.cinema.booking_service.dto.response.ProductResponse;
import com.cinema.booking_service.services.ProductService;
import com.cinema.controller.BaseController;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.APIResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/bookings/products")
@RequiredArgsConstructor
public class ProductController extends BaseController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<APIResponse<ActionMessageResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = productService.createProduct(request, httpRequest);
        return created(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProductRequest request,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = productService.updateProduct(id, request, httpRequest);
        return ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<APIResponse<ActionMessageResponse>> deleteProduct(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        ActionMessageResponse response = productService.deleteProduct(id, httpRequest);
        return ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<APIResponse<ProductResponse>> getProductById(@PathVariable UUID id) {
        ProductResponse response = productService.getProductById(id);
        return ok(response);
    }

    @PostMapping("/me/search")
    public ResponseEntity<APIResponse<PageResponse<ProductResponse>>> getProductsByOperatorCinema(
            @Valid @RequestBody PageRequest<ProductField> request,
            HttpServletRequest httpRequest) {
        PageResponse<ProductResponse> response = productService.getProductsByOperatorCinema(request, httpRequest);
        return ok(response);
    }

    @PostMapping("/cinemas/{cinemaId}/search")
    public ResponseEntity<APIResponse<PageResponse<ProductResponse>>> getProductsByCinemaId(
            @PathVariable UUID cinemaId,
            @Valid @RequestBody PageRequest<ProductField> request) {
        PageResponse<ProductResponse> response = productService.getProductsByCinemaId(cinemaId, request);
        return ok(response);
    }
}
