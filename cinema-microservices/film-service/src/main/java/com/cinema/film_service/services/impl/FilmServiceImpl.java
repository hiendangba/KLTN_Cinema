package com.cinema.film_service.services.impl;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.FilterField;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.film_service.dto.request.BatchFilmRequest;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.film_service.dto.response.BatchFilmResponse;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.mapper.FilmMapper;
import com.cinema.film_service.repository.FilmRepository;
import com.cinema.film_service.repository.FilmRepositoryImpl;
import com.cinema.film_service.services.FilmService;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cinema.dto.request.SortField;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class FilmServiceImpl implements FilmService {

    private final FilmRepository filmRepository;
    private final FilmRepositoryImpl filmRepositoryImpl;
    private final FilmMapper filmMapper;

    @Override
    public ActionMessageResponse createFilm(CreateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Tạo mới phim: {}", request.getTitle());
        log.info("httpRequest: " + httpRequest.getHeader(HeaderNames.X_USER_ROLE));
        log.info("httpRequest: " + httpRequest.getHeaderNames());
        log.info("httpRequestName: " + HeaderNames.X_USER_ROLE);
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!(HeaderNames.ROLE_ADMIN.equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        // Kiểm tra tên phim và năm phát hành đã tồn tại hay chưa
        if (filmRepository.findByTitleAndReleaseDate(request.getTitle(), request.getReleaseDate()).isPresent()) {
            log.error("Phim '{}' phát hành năm {} đã tồn tại", request.getTitle(), request.getReleaseDate().getYear());
            throw new BusinessException(ErrorCode.FILM_TITLE_EXISTED);
        }

        Film film = filmMapper.toEntity(request);
        Film savedFilm = filmRepository.save(film);
        log.info("Phim được tạo thành công với ID: {}", savedFilm.getId());
        return ActionMessageResponse.builder()
                .message("Tạo phim thành công")
                .build();
    }

    @Override
    public ActionMessageResponse updateFilm(UUID id, UpdateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Cập nhật phim với ID: {}", id);
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!(HeaderNames.ROLE_ADMIN.equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Film film = filmRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Không tìm thấy phim với ID: {}", id);
                    return new BusinessException(ErrorCode.FILM_NOT_FOUND);
                });

        filmMapper.updateEntityFromRequest(film, request);

        // Kiểm tra tên phim và năm phát hành đã tồn tại ở phim khác hay chưa
        if (filmRepository.existsByTitleAndReleaseDateAndIdNot(request.getTitle(), request.getReleaseDate(), id)) {
            log.error("Phim '{}' phát hành năm {} đã tồn tại", request.getTitle(), request.getReleaseDate().getYear());
            throw new BusinessException(ErrorCode.FILM_TITLE_EXISTED);
        }

        filmRepository.save(film);

        log.info("Phim được cập nhật thành công: {}", id);
        return ActionMessageResponse.builder()
                .message("Cập nhật phim thành công")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "films", key = "#id")
    public FilmResponse getFilmById(UUID id) {
        log.info("Lấy thông tin phim với ID: {}", id);

        Film film = filmRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> {
                    log.error("Không tìm thấy phim với ID: {}", id);
                    return new BusinessException(ErrorCode.FILM_NOT_FOUND);
                });

        return filmMapper.toResponse(film);
    }

    @Override
    @Transactional(readOnly = true)
    public BatchFilmResponse getFilmsInBatch(BatchFilmRequest request) {
        log.info("Lấy danh sách phim theo batch: {} ids", request.getIds().size());
        List<Film> films = filmRepository.findAllById(request.getIds());
        List<FilmResponse> filmResponses = films.stream()
                .filter(film -> !film.getIsDeleted())
                .map(filmMapper::toResponse)
                .collect(Collectors.toList());
        return new BatchFilmResponse(filmResponses);
    }

    @Override
    public CursorPageResponse<FilmResponse> searchFilms(
            CursorPageRequest<FilmField> request) {
        log.info("Lấy danh sách phim (cursor={}, size={}, keyword={}, sortBy={}, filterBy={})",
                request.getCursor(), request.getSize(), request.getKeyword(), request.getSortBy(),
                request.getFilterBy());

        String[] cursorParts = request.getParsedCompositeCursor();
        String keyword = request.getNormalizedKeyword();
        int size = request.getSizeOrDefault();

        // Truyền thẳng các DTO filter/sort vào repository
        List<SortField<FilmField>> sortFields = request.getSortBy();
        sortFields = sortFields == null ? new ArrayList<>() : new ArrayList<>(sortFields);
        // Luôn thêm ID làm sort cuối để đảm bảo thứ tự ổn định
        sortFields.add(new SortField<>(FilmField.ID, "ASC"));
        List<FilterField<FilmField>> filterFields = request.getFilterBy();
        List<Film> films = filmRepositoryImpl.searchWithCursorAndSortAndFilter(
                cursorParts, keyword, size, sortFields, filterFields);
        boolean hasNext = films.size() > size;
        String nextCursor = null;
        if (hasNext) {
            films = films.subList(0, size);
            nextCursor = CursorPageRequest
                    .encodeCompositeCursor(FilmField.getFieldValues(films.get(films.size() - 1), sortFields));
        }

        String prevCursor = null;
        if (cursorParts != null && cursorParts.length > 0) {
            List<Film> prevFilms = filmRepositoryImpl.previousCursor(
                    cursorParts, keyword, size, sortFields, filterFields);
            if (!prevFilms.isEmpty()) {
                if (prevFilms.size() == size) {
                    prevCursor = CursorPageRequest
                            .encodeCompositeCursor(
                                    FilmField.getFieldValues(prevFilms.get(size - 1), sortFields));
                }
            }
        }

        return CursorPageResponse.<FilmResponse>builder()
                .data(films.stream()
                        .map(filmMapper::toResponse)
                        .collect(Collectors.toList()))
                .nextCursor(nextCursor)
                .prevCursor(prevCursor)
                .hasNext(hasNext)
                .size(films.size())
                .build();
    }

    @Override
    public ActionMessageResponse deleteFilm(UUID id, HttpServletRequest httpRequest) {
        log.info("Deleting film with ID: {}", id);
        String role = httpRequest.getHeader(HeaderNames.X_USER_ROLE);
        if (!(HeaderNames.ROLE_ADMIN.equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Film film = filmRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> {
                    log.error("Film not found with ID: {}", id);
                    return new BusinessException(ErrorCode.FILM_NOT_FOUND);
                });

        film.setIsDeleted(true);
        filmRepository.save(film);
        log.info("Xóa phim thành công: {}", id);
        return ActionMessageResponse.builder()
                .message("Xóa phim thành công")
                .build();
    }
}
