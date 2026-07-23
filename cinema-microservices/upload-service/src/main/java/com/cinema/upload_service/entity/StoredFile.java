package com.cinema.upload_service.entity;

import com.cinema.upload_service.entity.enums.MediaType;
import com.cinema.upload_service.entity.enums.StoredFileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stored_file")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFile {

    @Id
    private UUID id;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "original_file_name", nullable = false)
    private String originalFileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType;

    @Column(nullable = false)
    private String extension;

    @Column(nullable = false)
    private Long size;

    @Column(name = "absolute_path", nullable = false, length = 2048)
    private String absolutePath;

    @Column(name = "object_key", nullable = false, length = 1024, unique = true)
    private String objectKey;

    @Column(name = "public_url", nullable = false, length = 2048)
    private String publicUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StoredFileStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
