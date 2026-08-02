create table notifications (
    id                uuid primary key,
    recipient_email   varchar(255) not null,
    subject           varchar(255) not null,
    body              text not null,
    channel           varchar(20) not null,
    status            varchar(20) not null,
    idempotency_key   varchar(255),
    attempt_count     integer not null default 0,
    last_error        varchar(500),
    created_at        timestamptz not null,
    sent_at           timestamptz,
    constraint uq_notifications_idempotency_key unique (idempotency_key)
);

create index idx_notifications_status on notifications (status);
