package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.dto.request.CreateProductRequest;
import com.cinema.booking_service.dto.request.ProductField;
import com.cinema.booking_service.dto.request.UpdateProductRequest;
import com.cinema.booking_service.dto.response.ProductResponse;
import com.cinema.booking_service.entity.Product;
import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.grpc.CinemaGrpcClient;
import com.cinema.booking_service.mapper.ProductMapper;
import com.cinema.booking_service.repository.ProductRepository;
import com.cinema.booking_service.services.ProductService;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CinemaGrpcClient cinemaGrpcClient;

    @Override
    @Transactional
    public ActionMessageResponse createProduct(CreateProductRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);

        if (productRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(
                cinemaId, request.getName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Product product = productMapper.toEntity(request);
        product.setName(request.getName().trim());
        product.setCinemaId(cinemaId);
        product.setStatus(request.getStatus() == null ? ProductStatus.ACTIVE : request.getStatus());

        productRepository.save(product);
        return ActionMessageResponse.builder()
                .message("Product created successfully")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateProduct(UUID id, UpdateProductRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);

        Product product = getManagedProductOrThrow(id, cinemaId);

        if (productRepository.existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(
                product.getCinemaId(), request.getName(), id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        productMapper.updateEntityFromRequest(product, request);
        product.setName(request.getName().trim());
        productRepository.save(product);

        return ActionMessageResponse.builder()
                .message("Product updated successfully")
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteProduct(UUID id, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        Product product = getManagedProductOrThrow(id, cinemaId);
        product.setIsDeleted(true);
        productRepository.save(product);

        return ActionMessageResponse.builder()
                .message("Product deleted successfully")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id) {
        return productMapper.toResponse(getActiveProductOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProductsByOperatorCinema(
            PageRequest<ProductField> request,
            HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        return getPagedProductsByCinemaId(cinemaId, request);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProductsByCinemaId(UUID cinemaId, PageRequest<ProductField> request) {
        return getPagedProductsByCinemaId(cinemaId, request);
    }

    private PageResponse<ProductResponse> getPagedProductsByCinemaId(UUID cinemaId, PageRequest<ProductField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "timeCreated"));

        Page<Product> productPage = productRepository.findAllByCinemaIdAndIsDeletedFalse(cinemaId, pageable);
        List<ProductResponse> data = productPage.getContent().stream()
                .map(productMapper::toResponse)
                .toList();

        return PageResponse.<ProductResponse>builder()
                .data(data)
                .currentPage(page)
                .totalPages(productPage.getTotalPages())
                .totalElements(productPage.getTotalElements())
                .size(size)
                .hasNext(productPage.hasNext())
                .hasPrevious(productPage.hasPrevious())
                .build();
    }

    private Product getActiveProductOrThrow(UUID id) {
        return productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Product getManagedProductOrThrow(UUID id, UUID cinemaId) {
        Product product = getActiveProductOrThrow(id);
        if (!cinemaId.equals(product.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return product;
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER);
    }

    private void validateOperatorRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
    }

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            return cinemaGrpcClient.getCinemaIdByUserId(userId);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.CINEMA_NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            throw ex;
        }
    }
}
