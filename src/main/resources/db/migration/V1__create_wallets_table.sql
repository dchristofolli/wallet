create table if not exists wallets
(
    id          uuid primary key,
    owner_name  varchar(100)                not null check (char_length(owner_name) >= 3),
    owner_email varchar(255) unique         not null check (char_length(owner_email) >= 5),
    balance     decimal(19, 2)              not null default 0.00,
    version     bigint                      not null default 0,
    created_at  timestamp without time zone not null default now(),
    updated_at  timestamp without time zone not null default now()
);