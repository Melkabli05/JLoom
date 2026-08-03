create table media_assets (
    id                  uuid primary key,
    storage_key         varchar(1024) not null,
    content_type        varchar(127) not null,
    size_bytes          bigint not null check (size_bytes >= 0),
    checksum            varchar(64) not null,
    owner_id            uuid,
    purpose             varchar(64) not null,
    visibility          varchar(16) not null,
    status              varchar(16) not null,
    original_filename   varchar(255),
    created_at          timestamptz not null,
    available_at        timestamptz,
    expires_at          timestamptz,
    thumbnail_key       varchar(1024),
    idempotency_key     varchar(255),
    constraint uq_media_assets_storage_key unique (storage_key),
    constraint chk_media_assets_visibility check (visibility in ('PRIVATE','INTERNAL','PUBLIC')),
    constraint chk_media_assets_status check (status in ('PENDING','AVAILABLE','FAILED','DELETED')),
    constraint chk_media_assets_purpose check (purpose in ('USER_AVATAR','ATTACHMENT','INGEST','THUMBNAIL'))
);

create unique index uq_media_assets_idempotency
    on media_assets (owner_id, purpose, idempotency_key)
    where idempotency_key is not null and status <> 'FAILED';

create index idx_media_assets_owner on media_assets (owner_id);
create index idx_media_assets_status on media_assets (status);
create index idx_media_assets_expires on media_assets (expires_at) where status <> 'DELETED';
create index idx_media_assets_owner_purpose on media_assets (owner_id, purpose, status);