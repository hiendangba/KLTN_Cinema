package com.cinema.cinema_service.mapper;

import com.cinema.cinema_service.dto.request.CreateCinemaRequest;
import com.cinema.cinema_service.dto.request.UpdateCinemaRequest;
import com.cinema.cinema_service.dto.response.CinemaResponse;
import com.cinema.cinema_service.dto.response.CinemaStaffResponse;
import com.cinema.cinema_service.entity.Cinema;
import com.cinema.cinema_service.entity.CinemaStaff;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class CinemaMapper {

    public Cinema toEntity(CreateCinemaRequest request) {
        Cinema cinema = new Cinema();
        cinema.setCode(request.getCode());
        cinema.setName(request.getName());
        cinema.setAddress(request.getAddress());
        cinema.setLatitude(request.getLatitude());
        cinema.setLongitude(request.getLongitude());
        cinema.setPhone(request.getPhone());
        cinema.setOpenTime(request.getOpenTime());
        cinema.setCloseTime(request.getCloseTime());
        cinema.setStatus(request.getStatus());
        cinema.setManagerId(request.getManagerId());
        return cinema;
    }

    public void updateEntity(Cinema cinema, UpdateCinemaRequest request) {
        cinema.setName(request.getName());
        cinema.setAddress(request.getAddress());
        cinema.setLatitude(request.getLatitude());
        cinema.setLongitude(request.getLongitude());
        cinema.setPhone(request.getPhone());
        cinema.setOpenTime(request.getOpenTime());
        cinema.setCloseTime(request.getCloseTime());
        cinema.setManagerId(request.getManagerId());
    }

    public CinemaResponse toResponse(Cinema cinema, List<UUID> staffIds) {
        return CinemaResponse.builder()
                .id(cinema.getId())
                .code(cinema.getCode())
                .name(cinema.getName())
                .address(cinema.getAddress())
                .latitude(cinema.getLatitude())
                .longitude(cinema.getLongitude())
                .phone(cinema.getPhone())
                .openTime(cinema.getOpenTime())
                .closeTime(cinema.getCloseTime())
                .status(cinema.getStatus())
                .managerId(cinema.getManagerId())
                .isDeleted(cinema.getIsDeleted())
                .createdAt(cinema.getCreatedAt())
                .updatedAt(cinema.getUpdatedAt())
                .staffIds(staffIds)
                .build();
    }

    public CinemaStaffResponse toResponse(CinemaStaff entity) {
        return CinemaStaffResponse.builder()
                .id(entity.getId())
                .cinemaId(entity.getCinemaId())
                .staffId(entity.getStaffId())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
