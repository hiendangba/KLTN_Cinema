package com.cinema.cinema_service.services.impl;

import com.cinema.cinema_service.dto.request.CreateCinemaRequest;
import com.cinema.cinema_service.dto.request.CinemaField;
import com.cinema.cinema_service.dto.request.UpdateCinemaRequest;
import com.cinema.cinema_service.dto.response.CinemaResponse;
import com.cinema.cinema_service.entity.Cinema;
import com.cinema.cinema_service.entity.CinemaStaff;
import com.cinema.cinema_service.grpc.UserGrpcClient;
import com.cinema.cinema_service.mapper.CinemaMapper;
import com.cinema.cinema_service.repository.CinemaRepository;
import com.cinema.cinema_service.repository.CinemaRepositoryImpl;
import com.cinema.cinema_service.repository.CinemaStaffRepository;
import com.cinema.dto.request.PageRequest;
import com.cinema.dto.response.PageResponse;
import com.cinema.exception.BusinessException;
import com.cinema.exception.ErrorCode;
import com.cinema.http.HeaderNames;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaServiceImplTest {

    @Mock
    private CinemaRepository cinemaRepository;
    @Mock
    private CinemaRepositoryImpl cinemaRepositoryImpl;
    @Mock
    private CinemaStaffRepository cinemaStaffRepository;
    @Mock
    private CinemaMapper cinemaMapper;
    @Mock
    private UserGrpcClient userGrpcClient;

    @InjectMocks
    private CinemaServiceImpl cinemaService;

    @Test
    void createCinema_allowsSameManagerAcrossMultipleCinemas() {
        MockHttpServletRequest request = adminRequest();
        UUID managerId = UUID.randomUUID();
        CreateCinemaRequest first = createRequest("CINEMA-1", managerId);
        CreateCinemaRequest second = createRequest("CINEMA-2", managerId);
        Cinema cinemaOne = cinema("CINEMA-1", managerId);
        Cinema cinemaTwo = cinema("CINEMA-2", managerId);

        when(cinemaRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("CINEMA-1")).thenReturn(false);
        when(cinemaRepository.existsByCodeIgnoreCaseAndIsDeletedFalse("CINEMA-2")).thenReturn(false);
        when(cinemaMapper.toEntity(first)).thenReturn(cinemaOne);
        when(cinemaMapper.toEntity(second)).thenReturn(cinemaTwo);

        assertDoesNotThrow(() -> cinemaService.createCinema(first, request));
        assertDoesNotThrow(() -> cinemaService.createCinema(second, request));

        verify(cinemaRepository, times(2)).save(any(Cinema.class));
    }

    @Test
    void updateCinema_allowsReusingManagerAcrossDifferentCinemas() {
        MockHttpServletRequest request = adminRequest();
        UUID cinemaId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UpdateCinemaRequest updateRequest = updateRequest("Updated Cinema", managerId);
        Cinema cinema = cinema("OLD", managerId);
        cinema.setId(cinemaId);

        when(cinemaRepository.findByIdAndIsDeletedFalse(cinemaId)).thenReturn(Optional.of(cinema));
        doAnswer(invocation -> null).when(cinemaMapper).updateEntity(cinema, updateRequest);

        assertDoesNotThrow(() -> cinemaService.updateCinema(cinemaId, updateRequest, request));
        verify(cinemaRepository).save(cinema);
    }

    @Test
    void getMyManagedCinemas_returnsAllCinemasForManager() {
        UUID managerId = UUID.randomUUID();
        MockHttpServletRequest request = managerRequest(managerId);
        Cinema first = cinema("CINEMA-1", managerId);
        Cinema second = cinema("CINEMA-2", managerId);
        first.setCreatedAt(LocalDateTime.now().minusDays(1));
        second.setCreatedAt(LocalDateTime.now());

        when(cinemaRepository.findAllByManagerIdAndIsDeletedFalseOrderByCreatedAtDesc(managerId))
                .thenReturn(List.of(second, first));
        when(cinemaStaffRepository.findByCinemaIdInAndActiveTrue(anyList())).thenReturn(List.of());
        when(cinemaMapper.toResponse(any(Cinema.class), anyList())).thenAnswer(invocation -> {
            Cinema cinema = invocation.getArgument(0);
            List<UUID> staffIds = invocation.getArgument(1);
            return CinemaResponse.builder()
                    .id(cinema.getId())
                    .code(cinema.getCode())
                    .name(cinema.getName())
                    .managerId(cinema.getManagerId())
                    .staffIds(staffIds)
                    .build();
        });
        when(userGrpcClient.getUserNameById(managerId)).thenReturn("Manager One");

        List<CinemaResponse> responses = cinemaService.getMyManagedCinemas(request);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getName()).isEqualTo("CINEMA-2");
        assertThat(responses.get(1).getName()).isEqualTo("CINEMA-1");
        assertThat(responses).allMatch(response -> "Manager One".equals(response.getManagerName()));
    }

    @Test
    void getMyManagedCinemas_returnsAssignedCinemaForStaff() {
        UUID managerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID cinemaId = UUID.randomUUID();
        MockHttpServletRequest request = staffRequest(staffId);
        Cinema cinema = cinema("STAFF-CINEMA", managerId);
        cinema.setId(cinemaId);
        CinemaStaff staffLink = cinemaStaff(cinemaId, staffId, true);

        when(cinemaStaffRepository.findByStaffId(staffId)).thenReturn(Optional.of(staffLink));
        when(cinemaRepository.findByIdAndIsDeletedFalse(cinemaId)).thenReturn(Optional.of(cinema));
        when(cinemaStaffRepository.findByCinemaIdAndActiveTrue(cinemaId)).thenReturn(List.of());
        when(cinemaMapper.toResponse(cinema, List.of())).thenReturn(CinemaResponse.builder()
                .id(cinemaId)
                .code(cinema.getCode())
                .name(cinema.getName())
                .managerId(managerId)
                .build());
        when(userGrpcClient.getUserNameById(managerId)).thenReturn("Manager One");

        List<CinemaResponse> responses = cinemaService.getMyManagedCinemas(request);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(cinemaId);
        assertThat(responses.get(0).getManagerName()).isEqualTo("Manager One");
    }

    @Test
    void getCinemaById_allowsAdminAccess() {
        UUID cinemaId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        Cinema cinema = cinema("CINEMA-1", managerId);
        cinema.setId(cinemaId);

        when(cinemaRepository.findByIdAndIsDeletedFalse(cinemaId)).thenReturn(Optional.of(cinema));
        when(cinemaStaffRepository.findByCinemaIdAndActiveTrue(cinemaId)).thenReturn(List.of());
        when(cinemaMapper.toResponse(cinema, List.of())).thenReturn(CinemaResponse.builder()
                .id(cinemaId)
                .managerId(managerId)
                .build());
        when(userGrpcClient.getUserNameById(managerId)).thenReturn("Manager One");

        CinemaResponse response = withRequestContext(adminRequest(), () -> cinemaService.getCinemaById(cinemaId));

        assertThat(response.getId()).isEqualTo(cinemaId);
        assertThat(response.getManagerName()).isEqualTo("Manager One");
    }

    @Test
    void getCinemaById_rejectsManagerWhoDoesNotOwnCinema() {
        UUID cinemaId = UUID.randomUUID();
        UUID ownerManagerId = UUID.randomUUID();
        UUID currentManagerId = UUID.randomUUID();
        Cinema cinema = cinema("CINEMA-1", ownerManagerId);
        cinema.setId(cinemaId);

        when(cinemaRepository.findByIdAndIsDeletedFalse(cinemaId)).thenReturn(Optional.of(cinema));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> withRequestContext(managerRequest(currentManagerId), () -> {
                    cinemaService.getCinemaById(cinemaId);
                    return null;
                }));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getCinemaById_allowsStaffAssignedToCinema() {
        UUID cinemaId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        Cinema cinema = cinema("CINEMA-1", managerId);
        cinema.setId(cinemaId);
        CinemaStaff staffLink = cinemaStaff(cinemaId, staffId, true);

        when(cinemaRepository.findByIdAndIsDeletedFalse(cinemaId)).thenReturn(Optional.of(cinema));
        when(cinemaStaffRepository.findByStaffId(staffId)).thenReturn(Optional.of(staffLink));
        when(cinemaStaffRepository.findByCinemaIdAndActiveTrue(cinemaId)).thenReturn(List.of());
        when(cinemaMapper.toResponse(cinema, List.of())).thenReturn(CinemaResponse.builder()
                .id(cinemaId)
                .managerId(managerId)
                .build());
        when(userGrpcClient.getUserNameById(managerId)).thenReturn("Manager One");

        CinemaResponse response = withRequestContext(staffRequest(staffId), () -> cinemaService.getCinemaById(cinemaId));

        assertThat(response.getId()).isEqualTo(cinemaId);
        assertThat(response.getManagerName()).isEqualTo("Manager One");
    }

    @Test
    void searchCinemas_scopesResultsToManagerOwnership() {
        UUID managerId = UUID.randomUUID();
        UUID otherManagerId = UUID.randomUUID();
        MockHttpServletRequest request = managerRequest(managerId);
        Cinema owned = cinema("OWNED", managerId);
        Cinema foreign = cinema("FOREIGN", otherManagerId);

        when(cinemaRepositoryImpl.countWithFilter(any(), anyList())).thenReturn(1L);
        when(cinemaRepositoryImpl.searchWithPageAndSortAndFilter(any(), anyInt(), anyInt(), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    List<com.cinema.dto.request.FilterField<CinemaField>> filters = invocation.getArgument(4);
                    assertThat(filters).anyMatch(filter ->
                            filter.getField() == CinemaField.MANAGER_ID
                                    && "EQ".equalsIgnoreCase(filter.getOperator())
                                    && managerId.equals(filter.getValue()));
                    return List.of(owned);
                });
        when(cinemaStaffRepository.findByCinemaIdInAndActiveTrue(anyList())).thenReturn(List.of());
        when(cinemaMapper.toResponse(any(Cinema.class), anyList())).thenAnswer(invocation -> {
            Cinema cinema = invocation.getArgument(0);
            return CinemaResponse.builder()
                    .id(cinema.getId())
                    .name(cinema.getName())
                    .managerId(cinema.getManagerId())
                    .build();
        });
        when(userGrpcClient.getUserNameById(managerId)).thenReturn("Manager One");

        PageRequest<CinemaField> pageRequest = PageRequest.<CinemaField>builder()
                .page(1)
                .size(20)
                .keyword("cinema")
                .build();

        PageResponse<CinemaResponse> response = withRequestContext(request, () -> cinemaService.searchCinemas(pageRequest));

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getName()).isEqualTo("OWNED");
        assertThat(response.getData()).allMatch(item -> "Manager One".equals(item.getManagerName()));
    }

    @Test
    void searchCinemas_scopesResultsToStaffAssignment() {
        UUID managerId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();
        UUID assignedCinemaId = UUID.randomUUID();
        MockHttpServletRequest request = staffRequest(staffId);
        Cinema assigned = cinema("ASSIGNED", managerId);
        assigned.setId(assignedCinemaId);
        CinemaStaff staffLink = cinemaStaff(assignedCinemaId, staffId, true);

        when(cinemaStaffRepository.findByStaffId(staffId)).thenReturn(Optional.of(staffLink));
        when(cinemaRepositoryImpl.countWithFilter(any(), anyList())).thenReturn(1L);
        when(cinemaRepositoryImpl.searchWithPageAndSortAndFilter(any(), anyInt(), anyInt(), anyList(), anyList()))
                .thenAnswer(invocation -> {
                    List<com.cinema.dto.request.FilterField<CinemaField>> filters = invocation.getArgument(4);
                    assertThat(filters).anyMatch(filter ->
                            filter.getField() == CinemaField.ID
                                    && "EQ".equalsIgnoreCase(filter.getOperator())
                                    && assignedCinemaId.equals(filter.getValue()));
                    return List.of(assigned);
                });
        when(cinemaStaffRepository.findByCinemaIdInAndActiveTrue(anyList())).thenReturn(List.of());
        when(cinemaMapper.toResponse(any(Cinema.class), anyList())).thenAnswer(invocation -> {
            Cinema cinema = invocation.getArgument(0);
            return CinemaResponse.builder()
                    .id(cinema.getId())
                    .name(cinema.getName())
                    .managerId(cinema.getManagerId())
                    .build();
        });
        when(userGrpcClient.getUserNameById(managerId)).thenReturn("Manager One");

        PageRequest<CinemaField> pageRequest = PageRequest.<CinemaField>builder()
                .page(1)
                .size(20)
                .keyword("cinema")
                .build();

        PageResponse<CinemaResponse> response = withRequestContext(request, () -> cinemaService.searchCinemas(pageRequest));

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).getName()).isEqualTo("ASSIGNED");
    }

    private MockHttpServletRequest adminRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderNames.X_USER_ROLE, HeaderNames.ROLE_ADMIN);
        request.addHeader(HeaderNames.X_USER_ID, UUID.randomUUID().toString());
        return request;
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

    private CreateCinemaRequest createRequest(String code, UUID managerId) {
        return CreateCinemaRequest.builder()
                .code(code)
                .name(code + " Name")
                .address("123 Main St")
                .latitude(BigDecimal.valueOf(10.123456))
                .longitude(BigDecimal.valueOf(106.123456))
                .phone("0912345678")
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(22, 0))
                .managerId(managerId)
                .build();
    }

    private UpdateCinemaRequest updateRequest(String name, UUID managerId) {
        return UpdateCinemaRequest.builder()
                .name(name)
                .address("456 Main St")
                .latitude(BigDecimal.valueOf(10.123456))
                .longitude(BigDecimal.valueOf(106.123456))
                .phone("0912345678")
                .openTime(LocalTime.of(8, 0))
                .closeTime(LocalTime.of(22, 0))
                .managerId(managerId)
                .build();
    }

    private Cinema cinema(String code, UUID managerId) {
        Cinema cinema = new Cinema();
        cinema.setId(UUID.randomUUID());
        cinema.setCode(code);
        cinema.setName(code + " Name");
        cinema.setAddress("123 Main St");
        cinema.setLatitude(BigDecimal.valueOf(10.123456));
        cinema.setLongitude(BigDecimal.valueOf(106.123456));
        cinema.setPhone("0912345678");
        cinema.setOpenTime(LocalTime.of(8, 0));
        cinema.setCloseTime(LocalTime.of(22, 0));
        cinema.setManagerId(managerId);
        cinema.setIsDeleted(false);
        cinema.setCreatedAt(LocalDateTime.now());
        cinema.setUpdatedAt(LocalDateTime.now());
        return cinema;
    }

    private CinemaStaff cinemaStaff(UUID cinemaId, UUID staffId, boolean active) {
        CinemaStaff cinemaStaff = new CinemaStaff();
        cinemaStaff.setCinemaId(cinemaId);
        cinemaStaff.setStaffId(staffId);
        cinemaStaff.setActive(active);
        return cinemaStaff;
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
