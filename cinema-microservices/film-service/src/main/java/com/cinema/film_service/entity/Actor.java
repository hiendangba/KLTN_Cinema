package com.cinema.film_service.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "actors", indexes = {
        @Index(name = "idx_actor_name", columnList = "name"),
        @Index(name = "idx_actor_is_deleted", columnList = "is_deleted")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Actor {

    @Id
    @Column(columnDefinition = "uuid")
    UUID id;

    @Column(nullable = false, length = 255)
    String name;

    @Column(name = "birth_year")
    Integer birthYear;

    @Column(columnDefinition = "text")
    String hometown;

    @Column(name = "avatar_url", columnDefinition = "text")
    String avatarUrl;

    @Column(name = "is_deleted", nullable = false)
    Boolean isDeleted;

    @Column(nullable = false, updatable = false)
    LocalDateTime timeCreated;

    @Column(nullable = false)
    LocalDateTime timeUpdated;

    @Builder.Default
    @ManyToMany(mappedBy = "actors", fetch = FetchType.LAZY)
    Set<Film> films = new LinkedHashSet<>();

    @PrePersist
    public void generateId() {
        if (this.id == null) {
            this.id = UuidCreator.getTimeOrderedEpoch();
        }
        isDeleted = false;
        timeCreated = LocalDateTime.now();
        timeUpdated = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        timeUpdated = LocalDateTime.now();
    }
}
