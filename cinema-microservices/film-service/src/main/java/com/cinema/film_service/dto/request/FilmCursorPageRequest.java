package com.cinema.film_service.dto.request;

import com.cinema.dto.request.CursorPageRequest;
import com.cinema.dto.request.DateRange;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class FilmCursorPageRequest extends CursorPageRequest<FilmField> {

    @Valid
    private DateRange dateRange;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate showtimeDate;
    private UUID cinemaId;
}
