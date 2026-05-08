package com.cinema.booking_service.services.impl;

import com.cinema.booking_service.dto.request.CreateProductRequest;
import com.cinema.booking_service.dto.request.UpdateProductRequest;
import com.cinema.booking_service.dto.response.ProductResponse;
import com.cinema.booking_service.entity.Product;
import com.cinema.booking_service.enums.ProductStatus;
import com.cinema.booking_service.grpc.CinemaGrpcClient;
import com.cinema.booking_service.mapper.ProductMapper;
import com.cinema.booking_service.repository.ProductRepository;
import com.cinema.booking_service.services.ProductService;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
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
    public List<ProductResponse> getProductsByOperatorCinema(HttpServletRequest httpRequest) {
        validateOperatorRole(httpRequest);
        UUID cinemaId = resolveCinemaIdByUser(httpRequest);
        return productRepository.findAllByCinemaIdAndIsDeletedFalseOrderByTimeCreatedDesc(cinemaId)
                .stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getProductsByCinemaId(UUID cinemaId) {
        return productRepository.findAllByCinemaIdAndIsDeletedFalseOrderByTimeCreatedDesc(cinemaId)
                .stream()
                .map(productMapper::toResponse)
                .toList();
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
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!HeaderNames.ROLE_MANAGER.equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void validateOperatorRole(HttpServletRequest httpRequest) {
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!(HeaderNames.ROLE_MANAGER.equals(role) || HeaderNames.ROLE_STAFF.equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private UUID resolveCinemaIdByUser(HttpServletRequest httpRequest) {
        String userIdRaw = httpRequest.getHeader(HeaderNames.X_USER_ID);
        if (userIdRaw == null || userIdRaw.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        try {
            return cinemaGrpcClient.getCinemaIdByUserId(UUID.fromString(userIdRaw));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_FORMAT);
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
