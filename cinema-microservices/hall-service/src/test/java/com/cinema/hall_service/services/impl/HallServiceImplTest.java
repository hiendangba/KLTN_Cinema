package com.cinema.hall_service.services.impl;

import com.cinema.Enum.HallEnum;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.PageResponse;
import com.cinema.dto.response.ActionMessageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.hall_service.dto.request.CreateHallRequest;
import com.cinema.hall_service.dto.request.HallLayoutDefinitionRequest;
import com.cinema.hall_service.dto.request.UpdateHallRequest;
import com.cinema.hall_service.entity.Hall;
import com.cinema.hall_service.entity.HallImage;
import com.cinema.hall_service.dto.response.HallResponse;
import com.cinema.hall_service.grpc.BookingGrpcClient;
import com.cinema.hall_service.grpc.CinemaGrpcClient;
import com.cinema.hall_service.grpc.SeatGrpcClient;
import com.cinema.hall_service.grpc.ShowtimeGrpcClient;
import com.cinema.hall_service.mapper.HallImageMapper;
import com.cinema.hall_service.mapper.HallMapper;
import com.cinema.hall_service.repository.HallImageRepository;
import com.cinema.hall_service.repository.HallRepository;
import com.cinema.hall_service.repository.HallRepositoryImpl;
import com.cinema.hall_service.dto.response.CinemaResponse;
import com.cinema.hall_service.config.RedisConfig;
import com.cinema.http.HeaderNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HallServiceImplTest {

    @Mock
    private HallRepository hallRepository;
    @Mock
    private HallRepositoryImpl hallRepositoryImpl;
    @Mock
    private HallImageRepository hallImageRepository;
    @Mock
    private HallMapper hallMapper;
    @Mock
    private HallImageMapper hallImageMapper;
    @Mock
    private CinemaGrpcClient cinemaGrpcClient;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache cache;
    @Mock
    private ShowtimeGrpcClient showtimeGrpcClient;
    @Mock
    private BookingGrpcClient bookingGrpcClient;
    @Mock
    private SeatGrpcClient seatGrpcClient;

    @InjectMocks
    private HallServiceImpl hallService;

    @Test
    void createHall_usesCinemaIdFromRequestAndAllowsOwnedCinema() {
        UUID managerId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        MockHttpServletRequest request = managerRequest(managerId);
        CreateHallRequest createRequest = createRequest(cinemaId, "Hall A");
        Hall hall = new Hall();
        hall.setId(hallId);
        hall.setName("Hall A");
        hall.setStatus(HallEnum.HallStatus.ACTIVE);

        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId)).thenReturn(List.of(cinemaId));
        when(hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIsDeletedFalse(cinemaId, "Hall A"))
                .thenReturn(false);
        when(hallMapper.toEntity(createRequest)).thenReturn(hall);
        when(hallRepository.save(hall)).thenReturn(hall);
        when(hallImageRepository.findAllByHall_Id(hallId)).thenReturn(List.of());
        when(seatGrpcClient.createLayoutDefinition(hallId, createRequest.getLayoutDefinition()))
                .thenReturn(ActionMessageResponse.builder().message("ok").build());

        ActionMessageResponse response = hallService.createHall(createRequest, request);

        assertThat(response.getMessage()).isEqualTo("Hall created successfully");
        assertThat(hall.getCinemaId()).isEqualTo(cinemaId);
        verify(hallRepository).save(hall);
    }

    @Test
    void createHall_rejectsCinemaNotOwnedByManager() {
        UUID managerId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        MockHttpServletRequest request = managerRequest(managerId);
        CreateHallRequest createRequest = createRequest(cinemaId, "Hall A");

        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId, HeaderNames.ROLE_MANAGER))
                .thenReturn(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> hallService.createHall(createRequest, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.HALL_NOT_IN_CINEMA);
    }

    @Test
    void updateHall_rejectsCinemaNotOwnedByManager() {
        UUID managerId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UpdateHallRequest updateRequest = updateRequest(cinemaId, "Hall B");
        MockHttpServletRequest request = managerRequest(managerId);

        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId)).thenReturn(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> hallService.updateHall(UUID.randomUUID(), updateRequest, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.HALL_NOT_IN_CINEMA);
    }

    @Test
    void updateHall_allowsOwnedCinemaAndKeepsCinemaId() {
        UUID managerId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        MockHttpServletRequest request = managerRequest(managerId);
        UpdateHallRequest updateRequest = updateRequest(cinemaId, "Hall B");
        Hall hall = new Hall();
        hall.setId(hallId);
        hall.setCinemaId(cinemaId);
        hall.setName("Hall A");
        hall.setStatus(HallEnum.HallStatus.ACTIVE);
        hall.setIsDeleted(false);

        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId)).thenReturn(List.of(cinemaId));
        when(hallRepository.findByIdAndIsDeletedFalse(hallId)).thenReturn(Optional.of(hall));
        when(hallRepository.existsByCinemaIdAndNameIgnoreCaseAndIdNotAndIsDeletedFalse(cinemaId, "Hall B", hallId))
                .thenReturn(false);
        when(showtimeGrpcClient.listActiveShowtimeIdsByHall(hallId)).thenReturn(List.of());
        doAnswer(invocation -> {
            Hall target = invocation.getArgument(0);
            UpdateHallRequest source = invocation.getArgument(1);
            target.setName(source.getName());
            target.setStatus(source.getStatus());
            return null;
        }).when(hallMapper).updateEntityFromRequest(hall, updateRequest);
        when(hallRepository.save(hall)).thenReturn(hall);
        when(seatGrpcClient.replaceLayoutDefinition(hallId, updateRequest.getLayoutDefinition()))
                .thenReturn(ActionMessageResponse.builder().message("ok").build());

        ActionMessageResponse response = hallService.updateHall(hallId, updateRequest, request);

        assertThat(response.getMessage()).isEqualTo("Hall updated successfully");
        assertThat(hall.getCinemaId()).isEqualTo(cinemaId);
        assertThat(hall.getName()).isEqualTo("Hall B");
        verify(hallRepository).save(hall);
    }

    @Test
    void deleteHall_usesCinemaIdQueryParamAndValidatesOwnership() {
        UUID managerId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        MockHttpServletRequest request = managerRequest(managerId);
        Hall hall = new Hall();
        hall.setId(hallId);
        hall.setCinemaId(cinemaId);
        hall.setName("Hall A");
        hall.setStatus(HallEnum.HallStatus.ACTIVE);
        hall.setIsDeleted(false);

        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId)).thenReturn(List.of(cinemaId));
        when(hallRepository.findByIdAndIsDeletedFalse(hallId)).thenReturn(Optional.of(hall));
        when(hallRepository.save(hall)).thenReturn(hall);

        ActionMessageResponse response = hallService.deleteHall(hallId, request);

        assertThat(response.getMessage()).isEqualTo("Hall deleted successfully");
        assertThat(hall.getIsDeleted()).isTrue();
        verify(hallRepository).save(hall);
    }

    @Test
    void deleteHall_rejectsCinemaNotOwnedByManager() {
        UUID managerId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        MockHttpServletRequest request = managerRequest(managerId);
        Hall hall = new Hall();
        hall.setId(hallId);
        hall.setCinemaId(cinemaId);
        hall.setName("Hall A");
        hall.setStatus(HallEnum.HallStatus.ACTIVE);
        hall.setIsDeleted(false);

        when(hallRepository.findByIdAndIsDeletedFalse(hallId)).thenReturn(Optional.of(hall));
        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId)).thenReturn(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> hallService.deleteHall(hallId, request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.HALL_NOT_IN_CINEMA);
    }

    @Test
    void getHallById_allowsAdminAccess() {
        UUID cinemaId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        Hall hall = hall(hallId, cinemaId, "Hall A");

        when(hallRepository.findByIdAndIsDeletedFalse(hallId)).thenReturn(Optional.of(hall));
        when(cacheManager.getCache(RedisConfig.CACHE_HALLS)).thenReturn(cache);
        when(cache.get(hallId, HallResponse.class)).thenReturn(null);
        when(hallMapper.toResponse(hall)).thenReturn(HallResponse.builder()
                .id(hallId)
                .cinemaId(cinemaId)
                .name("Hall A")
                .build());
        when(hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(hallId)).thenReturn(List.of());
        when(seatGrpcClient.getHallSeats(hallId)).thenReturn(List.of());
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenReturn("Cinema A");

        HallResponse response = withRequestContext(adminRequest(), () -> hallService.getHallById(hallId));

        assertThat(response.getId()).isEqualTo(hallId);
        assertThat(response.getCinemaResponse().getName()).isEqualTo("Cinema A");
    }

    @Test
    void getHallById_rejectsForeignManager() {
        UUID hallId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Hall hall = hall(hallId, cinemaId, "Hall A");

        when(hallRepository.findByIdAndIsDeletedFalse(hallId)).thenReturn(Optional.of(hall));
        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId)).thenReturn(List.of(UUID.randomUUID()));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> withRequestContext(managerRequest(managerId), () -> {
                    hallService.getHallById(hallId);
                    return null;
                }));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getHallById_allowsStaffAssignedToCinema() {
        UUID hallId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Hall hall = hall(hallId, cinemaId, "Hall A");
        MockHttpServletRequest request = staffRequest(staffId);

        when(hallRepository.findByIdAndIsDeletedFalse(hallId)).thenReturn(Optional.of(hall));
        when(cinemaGrpcClient.getCinemaIdsByUserId(staffId, HeaderNames.ROLE_STAFF)).thenReturn(List.of(cinemaId));
        when(cacheManager.getCache(RedisConfig.CACHE_HALLS)).thenReturn(cache);
        when(cache.get(hallId, HallResponse.class)).thenReturn(null);
        when(hallMapper.toResponse(hall)).thenReturn(HallResponse.builder()
                .id(hallId)
                .cinemaId(cinemaId)
                .name("Hall A")
                .build());
        when(hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(hallId)).thenReturn(List.of());
        when(seatGrpcClient.getHallSeats(hallId)).thenReturn(List.of());
        when(cinemaGrpcClient.getCinemaNameById(cinemaId)).thenReturn("Cinema A");

        HallResponse response = withRequestContext(request, () -> hallService.getHallById(hallId));

        assertThat(response.getId()).isEqualTo(hallId);
        assertThat(response.getCinemaResponse().getName()).isEqualTo("Cinema A");
    }

    @Test
    void searchHalls_scopesResultsToManagedCinemas() {
        UUID managerId = UUID.randomUUID();
        UUID ownedCinemaId = UUID.randomUUID();
        UUID foreignCinemaId = UUID.randomUUID();
        Hall ownedHall = hall(UUID.randomUUID(), ownedCinemaId, "Owned Hall");
        Hall foreignHall = hall(UUID.randomUUID(), foreignCinemaId, "Foreign Hall");
        MockHttpServletRequest request = managerRequest(managerId);

        when(cinemaGrpcClient.getCinemaIdsByUserId(managerId, HeaderNames.ROLE_MANAGER))
                .thenReturn(List.of(ownedCinemaId));
        when(hallRepositoryImpl.countWithFilter(any(), any())).thenReturn(1L);
        when(hallRepositoryImpl.searchWithPageAndSortAndFilter(any(), anyInt(), anyInt(), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    List<com.cinema.dto.request.FilterField<com.cinema.hall_service.dto.request.HallField>> filters =
                            invocation.getArgument(4);
                    assertThat(filters).anyMatch(filter ->
                            filter.getField() == com.cinema.hall_service.dto.request.HallField.CINEMA_ID
                                    && "IN".equalsIgnoreCase(filter.getOperator()));
                    return List.of(ownedHall);
                });
        when(hallMapper.toResponse(any(Hall.class))).thenAnswer(invocation -> {
            Hall hall = invocation.getArgument(0);
            return com.cinema.hall_service.dto.response.HallResponse.builder()
                    .id(hall.getId())
                    .cinemaId(hall.getCinemaId())
                    .name(hall.getName())
                    .build();
        });
        when(hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(any())).thenReturn(List.of());
        when(seatGrpcClient.getHallSeats(any())).thenReturn(List.of());
        when(cinemaGrpcClient.getCinemaNameById(ownedCinemaId)).thenReturn("Cinema A");

        PageRequest<com.cinema.hall_service.dto.request.HallField> pageRequest =
                PageRequest.<com.cinema.hall_service.dto.request.HallField>builder()
                        .page(1)
                        .size(20)
                        .keyword("hall")
                        .build();

        PageResponse<HallResponse> response = withRequestContext(request, () -> hallService.searchHalls(pageRequest));

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getCinemaId()).isEqualTo(ownedCinemaId);
    }

    @Test
    void searchHalls_scopesResultsToStaffAssignment() {
        UUID staffId = UUID.randomUUID();
        UUID assignedCinemaId = UUID.randomUUID();
        Hall assignedHall = hall(UUID.randomUUID(), assignedCinemaId, "Assigned Hall");
        MockHttpServletRequest request = staffRequest(staffId);

        when(cinemaGrpcClient.getCinemaIdsByUserId(staffId, HeaderNames.ROLE_STAFF)).thenReturn(List.of(assignedCinemaId));
        when(hallRepositoryImpl.countWithFilter(any(), any())).thenReturn(1L);
        when(hallRepositoryImpl.searchWithPageAndSortAndFilter(any(), anyInt(), anyInt(), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    List<com.cinema.dto.request.FilterField<com.cinema.hall_service.dto.request.HallField>> filters =
                            invocation.getArgument(4);
                    assertThat(filters).anyMatch(filter ->
                            filter.getField() == com.cinema.hall_service.dto.request.HallField.CINEMA_ID
                                    && "IN".equalsIgnoreCase(filter.getOperator()));
                    return List.of(assignedHall);
                });
        when(hallMapper.toResponse(any(Hall.class))).thenAnswer(invocation -> {
            Hall hall = invocation.getArgument(0);
            return com.cinema.hall_service.dto.response.HallResponse.builder()
                    .id(hall.getId())
                    .cinemaId(hall.getCinemaId())
                    .name(hall.getName())
                    .build();
        });
        when(hallImageRepository.findAllByHall_IdAndIsDeletedFalseOrderByTimeCreatedDesc(any())).thenReturn(List.of());
        when(seatGrpcClient.getHallSeats(any())).thenReturn(List.of());
        when(cinemaGrpcClient.getCinemaNameById(assignedCinemaId)).thenReturn("Cinema A");

        PageRequest<com.cinema.hall_service.dto.request.HallField> pageRequest =
                PageRequest.<com.cinema.hall_service.dto.request.HallField>builder()
                        .page(1)
                        .size(20)
                        .keyword("hall")
                        .build();

        PageResponse<HallResponse> response = withRequestContext(request, () -> hallService.searchHalls(pageRequest));

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getCinemaId()).isEqualTo(assignedCinemaId);
    }

    private MockHttpServletRequest managerRequest(UUID managerId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_MANAGER);
        request.addHeader(HeaderNames.X_USER_ID, managerId.toString());
        return request;
    }

    private MockHttpServletRequest staffRequest(UUID staffId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_STAFF);
        request.addHeader(HeaderNames.X_USER_ID, staffId.toString());
        return request;
    }

    private MockHttpServletRequest adminRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_ADMIN);
        request.addHeader(HeaderNames.X_USER_ID, UUID.randomUUID().toString());
        return request;
    }

    private CreateHallRequest createRequest(UUID cinemaId, String hallName) {
        CreateHallRequest request = new CreateHallRequest();
        request.setCinemaId(cinemaId);
        request.setName(hallName);
        request.setStatus(HallEnum.HallStatus.ACTIVE);
        request.setLayoutDefinition(layoutDefinition());
        request.setImagePaths(List.of());
        return request;
    }

    private UpdateHallRequest updateRequest(UUID cinemaId, String hallName) {
        UpdateHallRequest request = new UpdateHallRequest();
        request.setCinemaId(cinemaId);
        request.setName(hallName);
        request.setStatus(HallEnum.HallStatus.ACTIVE);
        request.setLayoutDefinition(layoutDefinition());
        request.setImagePaths(List.of());
        return request;
    }

    private HallLayoutDefinitionRequest layoutDefinition() {
        HallLayoutDefinitionRequest request = new HallLayoutDefinitionRequest();
        request.setTotalRows(1);
        request.setTotalCols(2);
        request.setScreenPosition(HallLayoutDefinitionRequest.ScreenPosition.TOP);

        HallLayoutDefinitionRequest.CellInput seat = new HallLayoutDefinitionRequest.CellInput();
        seat.setRow(1);
        seat.setCol(1);
        seat.setType(HallLayoutDefinitionRequest.CellInputType.SEAT);
        seat.setSeatType(HallLayoutDefinitionRequest.SeatType.STANDARD);
        request.setCells(List.of(seat));
        return request;
    }

    private Hall hall(UUID hallId, UUID cinemaId, String name) {
        Hall hall = new Hall();
        hall.setId(hallId);
        hall.setCinemaId(cinemaId);
        hall.setName(name);
        hall.setStatus(HallEnum.HallStatus.ACTIVE);
        hall.setIsDeleted(false);
        return hall;
    }

    private <T> T withRequestContext(MockHttpServletRequest request, Supplier<T> supplier) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            return supplier.get();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
