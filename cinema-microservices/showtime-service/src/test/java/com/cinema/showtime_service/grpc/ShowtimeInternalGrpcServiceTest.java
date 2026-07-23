package com.cinema.showtime_service.grpc;

import com.cinema.grpc.showtime.ListActiveFilmIdsReply;
import com.cinema.grpc.showtime.ListActiveFilmIdsRequest;
import com.cinema.grpc.showtime.ListActiveFilmIdsByCinemaReply;
import com.cinema.grpc.showtime.ListActiveFilmIdsByCinemaRequest;
import com.cinema.grpc.showtime.ListActiveFilmIdsByShowtimeDateReply;
import com.cinema.grpc.showtime.ListActiveFilmIdsByShowtimeDateRequest;
import com.cinema.showtime_service.repository.ShowTimeRepository;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShowtimeInternalGrpcServiceTest {

    @Mock
    private ShowTimeRepository showTimeRepository;

    @Mock
    private HallGrpcClient hallGrpcClient;

    @InjectMocks
    private ShowtimeInternalGrpcService grpcService;

    @Test
    void listActiveFilmIds_shouldReturnDistinctFilmIds() {
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();
        when(showTimeRepository.findActiveFilmIds()).thenReturn(List.of(film1, film2, film1));

        CapturingObserver<ListActiveFilmIdsReply> observer = new CapturingObserver<>();
        grpcService.listActiveFilmIds(ListActiveFilmIdsRequest.newBuilder().build(), observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertEquals(2, observer.value.getFilmIdsCount());
        assertEquals(film1.toString(), observer.value.getFilmIds(0));
        assertEquals(film2.toString(), observer.value.getFilmIds(1));
    }

    @Test
    void listActiveFilmIdsByCinema_shouldReturnDistinctFilmIds() {
        UUID cinemaId = UUID.randomUUID();
        UUID hall1 = UUID.randomUUID();
        UUID hall2 = UUID.randomUUID();
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();

        when(hallGrpcClient.listActiveHallIdsByCinema(cinemaId)).thenReturn(List.of(hall1, hall2));
        when(showTimeRepository.findActiveFilmIdsByHallIds(List.of(hall1, hall2))).thenReturn(List.of(film1, film2, film1));

        CapturingObserver<ListActiveFilmIdsByCinemaReply> observer = new CapturingObserver<>();
        grpcService.listActiveFilmIdsByCinema(
                ListActiveFilmIdsByCinemaRequest.newBuilder().setCinemaId(cinemaId.toString()).build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertEquals(2, observer.value.getFilmIdsCount());
        assertEquals(film1.toString(), observer.value.getFilmIds(0));
        assertEquals(film2.toString(), observer.value.getFilmIds(1));
        verify(hallGrpcClient).listActiveHallIdsByCinema(cinemaId);
    }

    @Test
    void listActiveFilmIdsByShowtimeDate_shouldReturnDistinctFilmIds() {
        UUID film1 = UUID.randomUUID();
        UUID film2 = UUID.randomUUID();
        when(showTimeRepository.findActiveFilmIdsByShowtimeDate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(film1, film2, film1));

        CapturingObserver<ListActiveFilmIdsByShowtimeDateReply> observer = new CapturingObserver<>();
        grpcService.listActiveFilmIdsByShowtimeDate(
                ListActiveFilmIdsByShowtimeDateRequest.newBuilder()
                        .setShowtimeDate("2026-06-14")
                        .build(),
                observer);

        assertTrue(observer.completed);
        assertTrue(observer.value.getSuccess());
        assertEquals(2, observer.value.getFilmIdsCount());
        assertEquals(film1.toString(), observer.value.getFilmIds(0));
        assertEquals(film2.toString(), observer.value.getFilmIds(1));
    }

    private static class CapturingObserver<T> implements StreamObserver<T> {
        private T value;
        private boolean completed;

        @Override
        public void onNext(T value) {
            this.value = value;
        }

        @Override
        public void onError(Throwable t) {
            throw new AssertionError(t);
        }

        @Override
        public void onCompleted() {
            this.completed = true;
        }
    }
}
