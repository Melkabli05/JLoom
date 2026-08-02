create table users (
    id                 uuid primary key,
    email              varchar(255) not null,
    password_hash      varchar(255) not null,
    created_at         timestamptz not null,
    email_verified_at  timestamptz,
    constraint uq_users_email unique (email),
    constraint chk_users_email_lowercase check (email = lower(email))
);
