package com.cinema.upload_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "video_upload_chunk")
@IdClass(VideoUploadChunk.VideoUploadChunkId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoUploadChunk {

    @Id
    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Id
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_size", nullable = false)
    private Long chunkSize;

    @Column(nullable = false)
    private boolean received;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VideoUploadChunkId implements Serializable {
        private UUID sessionId;
        private Integer chunkIndex;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof VideoUploadChunkId that)) {
                return false;
            }
            return Objects.equals(sessionId, that.sessionId) && Objects.equals(chunkIndex, that.chunkIndex);
        }

        @Override
        public int hashCode() {
            return Objects.hash(sessionId, chunkIndex);
        }
    }
}
