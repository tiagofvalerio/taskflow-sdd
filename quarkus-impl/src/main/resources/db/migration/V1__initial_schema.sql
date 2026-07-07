-- Schema mirrors the domain invariants: NOT NULL / length / enum checks match
-- spec/openapi.yaml constraints; the done<->completed_at check mirrors the
-- rule-3 guarantee that Task#complete always assigns completedAt.

create table project (
    id          uuid primary key,
    name        varchar(100) not null,
    description varchar(2000),
    status      varchar(20)  not null,
    created_at  timestamptz  not null,
    constraint project_name_not_blank check (length(trim(name)) > 0),
    constraint project_status_valid   check (status in ('active', 'archived'))
);

create table task (
    id           uuid primary key,
    project_id   uuid         not null references project (id),
    title        varchar(200) not null,
    description  varchar(2000),
    status       varchar(20)  not null,
    priority     varchar(10)  not null,
    created_at   timestamptz  not null,
    completed_at timestamptz,
    constraint task_title_not_blank        check (length(trim(title)) > 0),
    constraint task_status_valid           check (status in ('pending', 'in_progress', 'done')),
    constraint task_priority_valid         check (priority in ('low', 'medium', 'high')),
    constraint task_done_has_completed_at  check ((status = 'done') = (completed_at is not null))
);

create index idx_task_project_id on task (project_id);
