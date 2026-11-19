-- 管理员操作审计：只追加，记录操作者、目标、动作与结果摘要。
create table admin_audit_log (
    id              bigint unsigned auto_increment primary key,
    actor_id        bigint unsigned not null comment '操作者用户 id',
    actor_account   varchar(32)  not null comment '操作者账号快照',
    target_user_id  bigint unsigned not null comment '目标用户 id',
    target_account  varchar(32)  not null comment '目标账号快照',
    action          varchar(32)  not null comment 'user_ban/user_unban/password_reset',
    detail          varchar(255) null comment '状态变化等摘要，不放密码/令牌',
    created_at      datetime     not null default current_timestamp,
    index idx_actor_time (actor_id, created_at),
    index idx_target_time (target_user_id, created_at)
) engine = innodb default charset = utf8mb4;
