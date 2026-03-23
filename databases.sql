-- auto-generated definition
create table users
(
    id          bigint auto_increment
        primary key,
    username    varchar(255)                         not null,
    password    varchar(255)                        null,
    email       varchar(255)                        not null,
    provider    varchar(50)                         null,
    provider_id varchar(255)                        null,
    created_at  timestamp default CURRENT_TIMESTAMP null,
    updated_at  timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint email
        unique (email),
    constraint username
        unique (username)
);

INSERT INTO users (username, email, password)
VALUES ('admin', 'admin@example.com', '{noop}password');

CREATE TABLE refresh_token
(
    id          bigint auto_increment primary key,
    token       varchar(255) not null,
    expiry_date timestamp    not null,
    user_id     bigint       not null,
    constraint fk_refresh_token_user
        foreign key (user_id) references users (id)
            on delete cascade,
    constraint token_unique
        unique (token)
);
