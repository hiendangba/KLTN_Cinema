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
import com.cinema.Enum.SuccessMessage;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        validateCinemaAccess(request.getCinemaId(), accessibleCinemaIds);

        if (productRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(
                request.getCinemaId(), request.getName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        Product product = productMapper.toEntity(request);
        product.setName(request.getName().trim());
        product.setCinemaId(request.getCinemaId());
        product.setStatus(request.getStatus() == null ? ProductStatus.ACTIVE : request.getStatus());

        productRepository.save(product);
        return ActionMessageResponse.builder()
                .message(SuccessMessage.PRODUCT_CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateProduct(UUID id, UpdateProductRequest request, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        validateCinemaAccess(request.getCinemaId(), accessibleCinemaIds);

        Product product = getManagedProductOrThrow(id, request.getCinemaId(), accessibleCinemaIds);

        if (productRepository.existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(
                product.getCinemaId(), request.getName(), id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        productMapper.updateEntityFromRequest(product, request);
        product.setName(request.getName().trim());
        product.setCinemaId(request.getCinemaId());
        productRepository.save(product);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.PRODUCT_UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteProduct(UUID id, HttpServletRequest httpRequest) {
        validateManagerRole(httpRequest);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        Product product = getManagedProductOrThrow(id, null, accessibleCinemaIds);
        product.setIsDeleted(true);
        productRepository.save(product);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.PRODUCT_DELETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getProductById(UUID id, HttpServletRequest httpRequest) {
        Product product = getActiveProductOrThrow(id);
        authorizeProductRead(product, httpRequest);
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> getProductsByOperatorCinema(
            PageRequest<ProductField> request,
            HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return getPagedProductsByAllCinemas(request);
        }

        validateOperatorRole(httpRequest);
        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUserForRead(httpRequest);
        if (accessibleCinemaIds.isEmpty()) {
            return emptyProductPageResponse(request);
        }
        return getPagedProductsByCinemaIds(accessibleCinemaIds, request);
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
        return buildProductPageResponse(productPage, page, size);
    }

    private PageResponse<ProductResponse> getPagedProductsByCinemaIds(Set<UUID> cinemaIds, PageRequest<ProductField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "timeCreated"));

        Page<Product> productPage = productRepository.findAllByCinemaIdInAndIsDeletedFalse(cinemaIds, pageable);
        return buildProductPageResponse(productPage, page, size);
    }

    private PageResponse<ProductResponse> getPagedProductsByAllCinemas(PageRequest<ProductField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        Pageable pageable = org.springframework.data.domain.PageRequest.of(
                page - 1,
                size,
                Sort.by(Sort.Direction.DESC, "timeCreated"));

        Page<Product> productPage = productRepository.findAllByIsDeletedFalse(pageable);
        return buildProductPageResponse(productPage, page, size);
    }

    private PageResponse<ProductResponse> buildProductPageResponse(Page<Product> productPage, int page, int size) {
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

    private PageResponse<ProductResponse> emptyProductPageResponse(PageRequest<ProductField> request) {
        int page = request.getPageOrDefault();
        int size = request.getSizeOrDefault();
        return PageResponse.<ProductResponse>builder()
                .data(List.of())
                .currentPage(page)
                .totalPages(0)
                .totalElements(0)
                .size(size)
                .hasNext(false)
                .hasPrevious(page > 1)
                .build();
    }

    private Product getActiveProductOrThrow(UUID id) {
        return productRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private Product getManagedProductOrThrow(UUID id, UUID requestCinemaId, Set<UUID> accessibleCinemaIds) {
        Product product = getActiveProductOrThrow(id);
        if (!accessibleCinemaIds.contains(product.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (requestCinemaId != null && !requestCinemaId.equals(product.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return product;
    }

    private void authorizeProductRead(Product product, HttpServletRequest httpRequest) {
        String role = RequestAuthUtils.requireRoleHeader(httpRequest);
        if (HeaderNames.ROLE_ADMIN.equals(role)) {
            return;
        }

        if (!HeaderNames.ROLE_MANAGER.equals(role) && !HeaderNames.ROLE_STAFF.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        Set<UUID> accessibleCinemaIds = resolveAccessibleCinemaIdsByUser(httpRequest);
        if (!accessibleCinemaIds.contains(product.getCinemaId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateManagerRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_MANAGER);
    }

    private void validateOperatorRole(HttpServletRequest httpRequest) {
        RequestAuthUtils.requireAnyRole(httpRequest, HeaderNames.ROLE_ADMIN, HeaderNames.ROLE_MANAGER, HeaderNames.ROLE_STAFF);
    }

    private void validateCinemaAccess(UUID cinemaId, Set<UUID> accessibleCinemaIds) {
        if (cinemaId == null || !accessibleCinemaIds.contains(cinemaId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private Set<UUID> resolveAccessibleCinemaIdsByUser(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            String role = RequestAuthUtils.requireRoleHeader(httpRequest);
            List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
            if (cinemaIds.isEmpty()) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            return new HashSet<>(cinemaIds);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.CINEMA_NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                throw new BusinessException(ErrorCode.MANAGER_NOT_ASSIGNED_CINEMA);
            }
            throw ex;
        }
    }

    private Set<UUID> resolveAccessibleCinemaIdsByUserForRead(HttpServletRequest httpRequest) {
        try {
            UUID userId = RequestAuthUtils.requireUserId(httpRequest);
            String role = RequestAuthUtils.requireRoleHeader(httpRequest);
            List<UUID> cinemaIds = cinemaGrpcClient.getCinemaIdsByUserId(userId, role);
            return new HashSet<>(cinemaIds == null ? List.of() : cinemaIds);
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.CINEMA_NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.NOT_FOUND
                    || ex.getErrorCode() == ErrorCode.USER_NOT_FOUND) {
                return Set.of();
            }
            throw ex;
        }
    }
}
