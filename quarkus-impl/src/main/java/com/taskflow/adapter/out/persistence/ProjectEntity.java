package com.taskflow.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence-side representation of a project — never leaves the adapter.
 * Status is stored as the lowercase wire/database value (see V1 migration
 * checks); {@link ProjectMapper} converts to/from the domain enum.
 */
@Entity
@Table(name = "project")
public class ProjectEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "name", nullable = false, length = 100)
    public String name;

    @Column(name = "description", length = 2000)
    public String description;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}
