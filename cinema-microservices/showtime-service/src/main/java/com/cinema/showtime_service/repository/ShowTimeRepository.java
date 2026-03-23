package com.cinema.showtime_service.repository;

import com.cinema.showtime_service.entity.ShowTime;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface ShowTimeRepository extends JpaRepository<ShowTime, UUID> {

    // Chỉ kiểm tra xem cái nào là đã lên lịch hoặc đang chiếu thôi
    // StartDatetime < endTime : Suất mới có thời gian bắt đầu nhỏ hơn thời gian kết
    // thúc của phim mới
    // EndDateTime > startTime: Suất mới có thời gian kết thúc nhỏ hơn thời gian bắt
    // đầu của phim mới
    @Query(value = """
            SELECT * FROM show_time s
            WHERE s.hall_id = :hallId
              AND s.start_date_time < :endTime
              AND s.end_date_time > :startTime
              AND s.status IN ('SCHEDULED', 'ONGOING')
            ORDER BY s.end_date_time DESC
            LIMIT 1
            """, nativeQuery = true)
    Optional<ShowTime> findOverlapping(
            @Param("hallId") UUID hallId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    // TODO: Implement method này bằng @Query hoặc Specification để hỗ trợ phân
    // trang cursor, lọc status, isDeleted, keyword, sort đa trường
    default java.util.List<ShowTime> findByCursorAndStatusAndIsDeletedAndKeywordAndSortMulti(
            UUID cursor,
            java.util.List<com.cinema.Enum.ShowTimeEnum.ShowTimeStatus> statuses,
            boolean isDeleted,
            String keyword,
            int limit,
            java.util.List<com.cinema.showtime_service.dto.request.ShowTimeSortField> sortFields) {
        throw new UnsupportedOperationException(
                "Chưa implement query động cho findByCursorAndStatusAndIsDeletedAndKeywordAndSortMulti");
    }
}
