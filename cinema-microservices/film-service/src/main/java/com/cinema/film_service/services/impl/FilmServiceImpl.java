package com.cinema.film_service.services.impl;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.response.CursorPageResponse;
import com.cinema.film_service.dto.request.CreateFilmRequest;
import com.cinema.film_service.dto.request.FilmSortField;
import com.cinema.film_service.dto.request.UpdateFilmRequest;
import com.cinema.film_service.dto.response.FilmResponse;
import com.cinema.film_service.entity.Film;
import com.cinema.film_service.mapper.FilmMapper;
import com.cinema.film_service.repository.FilmRepository;
import com.cinema.film_service.services.FilmService;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// ...existing code...

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class FilmServiceImpl implements FilmService {

    private final FilmRepository filmRepository;
    private final FilmMapper filmMapper;

    @Override
    public FilmResponse createFilm(CreateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Tạo mới phim: {}", request.getTitle());
        String role = httpRequest.getHeader("X-User-Role");
        if (!("ADMIN".equals(role))) {
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
        return filmMapper.toResponse(savedFilm);
    }

    @Override
    public FilmResponse updateFilm(UUID id, UpdateFilmRequest request, HttpServletRequest httpRequest) {
        log.info("Cập nhật phim với ID: {}", id);
        String role = httpRequest.getHeader("X-User-Role");
        if (!("ADMIN".equals(role))) {
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

        Film updatedFilm = filmRepository.save(film);

        log.info("Phim được cập nhật thành công: {}", id);
        return filmMapper.toResponse(updatedFilm);
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
    public CursorPageResponse<FilmResponse> getAllFilms(CursorPageRequest<FilmSortField> request) {
        log.info("Lấy danh sách phim (cursor={}, size={}, keyword={})",
                request.getCursor(), request.getSize(), request.getKeyword());

        UUID cursor = request.getParsedCursor(); // ✅ dùng method trong request
        String keyword = request.getNormalizedKeyword(); // ✅ dùng method trong request

        List<Film> films = filmRepository.findByCursorAndKeyword(cursor, keyword, request.getSize() + 1);

        boolean hasNext = films.size() > request.getSize();
        if (hasNext)
            films = films.subList(0, request.getSize());

        String nextCursor = hasNext
                ? films.get(films.size() - 1).getId().toString()
                : null;

        return CursorPageResponse.<FilmResponse>builder()
                .data(films.stream()
                        .map(filmMapper::toResponse)
                        .collect(Collectors.toList()))
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .size(films.size())
                .build();
    }

    @Override
    public void deleteFilm(UUID id, HttpServletRequest httpRequest) {
        log.info("Xóa phim với ID: {}", id);
        String role = httpRequest.getHeader("X-User-Role");
        if (!("ADMIN".equals(role))) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Film film = filmRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> {
                    log.error("Không tìm thấy phim với ID: {}", id);
                    return new BusinessException(ErrorCode.FILM_NOT_FOUND);
                });

        film.setIsDeleted(true);
        filmRepository.save(film);
        log.info("Phim được xóa thành công: {}", id);
    }
}
