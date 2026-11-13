-- 初始化脚本：建库、用户表、种子管理员。
-- 种子管理员仅用于开发与首次部署，初始密码记录在本地 docs/decisions.md，部署前必须修改。
-- SQL 关键字统一小写；参数化由应用层保证，此文件只做结构与种子数据初始化。

-- 容器入口执行脚本时连接字符集不一定是 utf8mb4，显式声明避免中文种子数据双重编码
set names utf8mb4;

create database if not exists usercenter default character set utf8mb4 collate utf8mb4_0900_ai_ci;
use usercenter;

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

insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version)
select 'admin0001', '系统管理员', 'admin@user-center.local',
       '$2a$10$Jm7AF8rSUo7lfJqt1JpKaOR32VH/HNi5b7hkhhLH8o522GAhqMoFq', 1, 0, 0, 0
where not exists (select 1 from `user` where email = 'admin@user-center.local');
