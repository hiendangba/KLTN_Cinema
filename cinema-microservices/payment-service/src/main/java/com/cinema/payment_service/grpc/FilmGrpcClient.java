package com.cinema.payment_service.grpc;

import com.cinema.grpc.film.FilmInternalServiceGrpc;
import com.cinema.grpc.film.FilmPayload;
import com.cinema.grpc.film.GetFilmsByIdsReply;
import com.cinema.grpc.film.GetFilmsByIdsRequest;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@Slf4j
public class FilmGrpcClient {

    private final FilmInternalServiceGrpc.FilmInternalServiceBlockingStub filmBlockingStub;

    public FilmGrpcClient(GrpcChannelFactory channelFactory) {
        this.filmBlockingStub = FilmInternalServiceGrpc.newBlockingStub(channelFactory.createChannel("film"));
    }

    public Map<UUID, FilmMetadata> getFilmMetadataByIds(Collection<UUID> filmIds) {
        if (filmIds == null || filmIds.isEmpty()) {
            return Map.of();
        }

        try {
            GetFilmsByIdsReply reply = filmBlockingStub.getFilmsByIds(
                    GetFilmsByIdsRequest.newBuilder()
                            .addAllFilmIds(filmIds.stream()
                                    .filter(Objects::nonNull)
                                    .map(UUID::toString)
                                    .toList())
                            .build());
            if (!reply.getSuccess()) {
                return Map.of();
            }

            Map<UUID, FilmMetadata> result = new LinkedHashMap<>();
            for (FilmPayload payload : reply.getFilmsList()) {
                if (payload == null || payload.getId() == null || payload.getId().isBlank()) {
                    continue;
                }
                try {
                    result.put(UUID.fromString(payload.getId()), new FilmMetadata(
                            payload.getTitle() == null ? "" : payload.getTitle(),
                            payload.getDirector() == null ? "" : payload.getDirector()));
                } catch (IllegalArgumentException ex) {
                    log.warn("Skipping invalid film payload id={}", payload.getId());
                }
            }
            return result;
        } catch (StatusRuntimeException ex) {
            log.warn("Film lookup failed", ex);
            return Map.of();
        }
    }
}
