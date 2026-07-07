package com.taskflow.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence-side representation of a task — never leaves the adapter.
 * Status/priority stored as lowercase wire/database values (see V1 migration
 * checks); {@link TaskMapper} converts to/from the domain enums. The project
 * association is a plain FK column, not a JPA relation — aggregates reference
 * each other by id, mirroring the domain design.
 */
@Entity
@Table(name = "task")
public class TaskEntity {

    @Id
    @Column(name = "id")
    public UUID id;

    @Column(name = "project_id", nullable = false)
    public UUID projectId;

    @Column(name = "title", nullable = false, length = 200)
    public String title;

    @Column(name = "description", length = 2000)
    public String description;

    @Column(name = "status", nullable = false, length = 20)
    public String status;

    @Column(name = "priority", nullable = false, length = 10)
    public String priority;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "completed_at")
    public Instant completedAt;
}
