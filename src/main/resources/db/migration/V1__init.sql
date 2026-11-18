-- 用户表初始结构。Flyway 已连接到 usercenter 库，不再执行 create database/use。
create table if not exists `user` (
    id              bigint unsigned auto_increment primary key,
    user_account    varchar(32)  not null comment '对外标识，自动生成，不参与登录',
    nickname        varchar(32)  null     comment '昵称',
    email           varchar(128) not null comment '登录标识',
    password_hash   varchar(100) not null comment 'BCrypt',
    role            tinyint      not null default 0 comment '0-普通用户 1-管理员',
    status          tinyint      not null default 0 comment '0-正常 1-封禁',
    is_delete       tinyint      not null default 0 comment '0-未注销 1-已注销',
    token_version   int          not null default 0,
    last_login_time datetime     null,
    create_time     datetime     not null default current_timestamp,
    update_time     datetime     not null default current_timestamp on update current_timestamp,
    unique key uk_email (email),
    unique key uk_user_account (user_account)
) engine = innodb default charset = utf8mb4;
