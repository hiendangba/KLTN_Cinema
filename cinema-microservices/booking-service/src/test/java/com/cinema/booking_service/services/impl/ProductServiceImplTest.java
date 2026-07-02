package com.cinema.booking_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.booking_service.dto.request.CreateProductRequest;
import com.cinema.booking_service.dto.request.UpdateProductRequest;
import com.cinema.booking_service.entity.Product;
import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.enums.ProductType;
import com.cinema.booking_service.grpc.CinemaGrpcClient;
import com.cinema.booking_service.mapper.ProductMapper;
import com.cinema.booking_service.repository.ProductRepository;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private CinemaGrpcClient cinemaGrpcClient;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void createProduct_shouldAllowAdminAcrossCinemaScope() {
        UUID cinemaId = UUID.randomUUID();
        CreateProductRequest request = buildCreateRequest(cinemaId);
        when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_ADMIN);
        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema 1")));
        when(productRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(cinemaId, request.getName()))
                .thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(new Product());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = productService.createProduct(request, httpRequest);

        assertEquals(SuccessMessage.PRODUCT_CREATED.getMessage(), response.getMessage());
        verify(cinemaGrpcClient).getAllActiveCinemas();
        verify(httpRequest, never()).getHeader(HeaderNames.X_USER_ID);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();
        assertEquals(cinemaId, saved.getCinemaId());
        assertEquals(request.getName().trim(), saved.getName());
        assertEquals(request.getType(), saved.getType());
        assertEquals(request.getPrice(), saved.getPrice());
        assertEquals(request.getDescription(), saved.getDescription());
        assertEquals(request.getImageUrl(), saved.getImageUrl());
        assertEquals(request.getStatus(), saved.getStatus());
        assertFalse(Boolean.TRUE.equals(saved.getIsDeleted()));
    }

    @Test
    void updateProduct_shouldAllowAdminAcrossCinemaScope() {
        UUID cinemaId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UpdateProductRequest request = buildUpdateRequest(cinemaId);
        Product product = buildProduct(productId, cinemaId);

        when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_ADMIN);
        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema 1")));
        when(productRepository.findByIdAndIsDeletedFalse(productId)).thenReturn(Optional.of(product));
        when(productRepository.existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(
                cinemaId, request.getName(), productId)).thenReturn(false);
        doAnswer(invocation -> {
            Product entity = invocation.getArgument(0);
            UpdateProductRequest updateRequest = invocation.getArgument(1);
            entity.setType(updateRequest.getType());
            entity.setPrice(updateRequest.getPrice());
            entity.setDescription(updateRequest.getDescription());
            entity.setImageUrl(updateRequest.getImageUrl());
            entity.setStatus(updateRequest.getStatus());
            return null;
        }).when(productMapper).updateEntityFromRequest(same(product), eq(request));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = productService.updateProduct(productId, request, httpRequest);

        assertEquals(SuccessMessage.PRODUCT_UPDATED.getMessage(), response.getMessage());
        verify(cinemaGrpcClient).getAllActiveCinemas();
        verify(httpRequest, never()).getHeader(HeaderNames.X_USER_ID);
        assertEquals(cinemaId, product.getCinemaId());
        assertEquals(request.getName().trim(), product.getName());
        assertEquals(request.getType(), product.getType());
        assertEquals(request.getPrice(), product.getPrice());
        assertEquals(request.getDescription(), product.getDescription());
        assertEquals(request.getImageUrl(), product.getImageUrl());
        assertEquals(request.getStatus(), product.getStatus());
    }

    @Test
    void deleteProduct_shouldAllowAdminAcrossCinemaScope() {
        UUID cinemaId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = buildProduct(productId, cinemaId);

        when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_ADMIN);
        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(cinemaId, "Cinema 1")));
        when(productRepository.findByIdAndIsDeletedFalse(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = productService.deleteProduct(productId, httpRequest);

        assertEquals(SuccessMessage.PRODUCT_DELETED.getMessage(), response.getMessage());
        assertTrue(Boolean.TRUE.equals(product.getIsDeleted()));
        verify(cinemaGrpcClient).getAllActiveCinemas();
        verify(httpRequest, never()).getHeader(HeaderNames.X_USER_ID);
    }

    @Test
    void createProduct_shouldAllowManagerWithinAssignedCinema() {
        UUID cinemaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateProductRequest request = buildCreateRequest(cinemaId);
        when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_MANAGER);
        when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER))
                .thenReturn(List.of(cinemaId));
        when(productRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(cinemaId, request.getName()))
                .thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(new Product());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionMessageResponse response = productService.createProduct(request, httpRequest);

        assertEquals(SuccessMessage.PRODUCT_CREATED.getMessage(), response.getMessage());
        verify(cinemaGrpcClient, never()).getAllActiveCinemas();
        verify(cinemaGrpcClient).getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER);
    }

    @Test
    void createProduct_shouldRejectStaff() {
        UUID cinemaId = UUID.randomUUID();
        CreateProductRequest request = buildCreateRequest(cinemaId);
        when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_STAFF);
        when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(UUID.randomUUID().toString());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> productService.createProduct(request, httpRequest));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(cinemaGrpcClient, productRepository, productMapper);
    }

    @Test
    void createProduct_shouldRejectManagerWithoutCinemaAssignment() {
        UUID cinemaId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CreateProductRequest request = buildCreateRequest(cinemaId);
        when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_MANAGER);
        when(httpRequest.getHeader(HeaderNames.X_USER_ID)).thenReturn(userId.toString());
        when(cinemaGrpcClient.getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> productService.createProduct(request, httpRequest));

        assertEquals(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA, exception.getErrorCode());
        verify(cinemaGrpcClient).getCinemaIdsByUserId(userId, HeaderNames.ROLE_MANAGER);
        verifyNoInteractions(productRepository, productMapper);
    }

    @Test
    void createProduct_shouldRejectAdminForInactiveCinema() {
        UUID requestedCinemaId = UUID.randomUUID();
        UUID activeCinemaId = UUID.randomUUID();
        CreateProductRequest request = buildCreateRequest(requestedCinemaId);
        when(httpRequest.getHeader(HeaderNames.X_USER_ROLE)).thenReturn(HeaderNames.ROLE_ADMIN);
        when(cinemaGrpcClient.getAllActiveCinemas()).thenReturn(List.of(
                new CinemaGrpcClient.CinemaSummary(activeCinemaId, "Cinema 1")));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> productService.createProduct(request, httpRequest));

        assertEquals(ErrorCode.FORBIDDEN, exception.getErrorCode());
        verify(cinemaGrpcClient).getAllActiveCinemas();
        verifyNoInteractions(productRepository, productMapper);
    }

    private CreateProductRequest buildCreateRequest(UUID cinemaId) {
        CreateProductRequest request = new CreateProductRequest();
        request.setCinemaId(cinemaId);
        request.setName("Popcorn Combo");
        request.setType(ProductType.SNACK);
        request.setPrice(BigDecimal.valueOf(45000));
        request.setDescription("Large popcorn and two drinks");
        request.setImageUrl("https://example.com/popcorn.png");
        request.setStatus(ProductStatus.ACTIVE);
        return request;
    }

    private UpdateProductRequest buildUpdateRequest(UUID cinemaId) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setCinemaId(cinemaId);
        request.setName("Updated Popcorn Combo");
        request.setType(ProductType.SNACK);
        request.setPrice(BigDecimal.valueOf(55000));
        request.setDescription("Updated combo");
        request.setImageUrl("https://example.com/updated-popcorn.png");
        request.setStatus(ProductStatus.INACTIVE);
        return request;
    }

    private Product buildProduct(UUID productId, UUID cinemaId) {
        Product product = new Product();
        product.setId(productId);
        product.setCinemaId(cinemaId);
        product.setName("Old Combo");
        product.setType(ProductType.SNACK);
        product.setPrice(BigDecimal.valueOf(40000));
        product.setDescription("Old description");
        product.setImageUrl("https://example.com/old-popcorn.png");
        product.setStatus(ProductStatus.ACTIVE);
        product.setIsDeleted(false);
        return product;
    }
}
