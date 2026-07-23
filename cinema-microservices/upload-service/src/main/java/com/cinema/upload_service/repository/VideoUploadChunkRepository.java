package com.cinema.upload_service.repository;

import com.cinema.upload_service.entity.VideoUploadChunk;
import com.cinema.upload_service.entity.VideoUploadChunk.VideoUploadChunkId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VideoUploadChunkRepository extends JpaRepository<VideoUploadChunk, VideoUploadChunkId> {

    long countBySessionIdAndReceivedTrue(UUID sessionId);

    List<VideoUploadChunk> findBySessionIdOrderByChunkIndexAsc(UUID sessionId);

    @Modifying
    @Query("delete from VideoUploadChunk c where c.sessionId = :sessionId")
    void deleteBySessionId(@Param("sessionId") UUID sessionId);
}
