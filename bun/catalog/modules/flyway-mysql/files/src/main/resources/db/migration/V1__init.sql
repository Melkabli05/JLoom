-- Managed by jloom's flyway module. Add new migrations as V2__..., V3__..., etc. — never edit
-- this file once it has run against a real database; Flyway checksums applied migrations.
-- `key` is backtick-quoted: it's a reserved word in MySQL/MariaDB (unlike Postgres, where the
-- sibling `flyway` module's identical-looking migration works unquoted).
create table if not exists app_info (
    `key`   varchar(255) primary key,
    value varchar(255) not null
);
