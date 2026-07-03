package com.cinema.film_service.services.impl;

import com.cinema.Enum.SuccessMessage;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.film_service.dto.request.CreateFilmTypeRequest;
import com.cinema.film_service.dto.request.UpdateFilmTypeRequest;
import com.cinema.film_service.dto.response.FilmTypeResponse;
import com.cinema.film_service.entity.FilmType;
import com.cinema.film_service.repository.FilmTypeRepository;
import com.cinema.film_service.services.FilmCatalogSyncService;
import com.cinema.film_service.services.TypeService;
import com.cinema.http.HeaderNames;
import com.cinema.http.RequestAuthUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TypeServiceImpl implements TypeService {

    private final FilmTypeRepository filmTypeRepository;
    private final FilmCatalogSyncService filmCatalogSyncService;

    @Override
    @Transactional
    public ActionMessageResponse createType(CreateFilmTypeRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "createType");
        String name = normalize(request.getName());
        if (filmTypeRepository.findByNameIgnoreCaseAndIsDeletedFalse(name).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        filmTypeRepository.save(FilmType.builder()
                .name(name)
                .build());

        return ActionMessageResponse.builder()
                .message(SuccessMessage.CREATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse updateType(UUID id, UpdateFilmTypeRequest request, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "updateType");
        FilmType type = getActiveTypeOrThrow(id);
        String name = normalize(request.getName());
        if (filmTypeRepository.existsByNameIgnoreCaseAndIdNotAndIsDeletedFalse(name, id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        type.setName(name);
        filmTypeRepository.save(type);
        filmCatalogSyncService.refreshFilmsForType(id);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.UPDATED.getMessage())
                .build();
    }

    @Override
    @Transactional
    public ActionMessageResponse deleteType(UUID id, HttpServletRequest httpRequest) {
        validateAdmin(httpRequest, "deleteType");
        FilmType type = getActiveTypeOrThrow(id);
        type.setIsDeleted(true);
        filmTypeRepository.save(type);
        filmCatalogSyncService.refreshFilmsForType(id);

        return ActionMessageResponse.builder()
                .message(SuccessMessage.DELETED.getMessage())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public FilmTypeResponse getTypeById(UUID id) {
        return toResponse(getActiveTypeOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FilmTypeResponse> listTypes() {
        return filmTypeRepository.findAllByIsDeletedFalseOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private FilmType getActiveTypeOrThrow(UUID id) {
        return filmTypeRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }

    private FilmTypeResponse toResponse(FilmType type) {
        return FilmTypeResponse.builder()
                .id(type.getId())
                .name(type.getName())
                .timeCreated(type.getTimeCreated())
                .timeUpdated(type.getTimeUpdated())
                .build();
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        return value.trim();
    }

    private void validateAdmin(HttpServletRequest httpRequest, String action) {
        RequestAuthUtils.requireRole(httpRequest, HeaderNames.ROLE_ADMIN, log, action);
    }
}
