-- 仅开发环境加载（application-dev.yaml 把 db/dev 加入 Flyway locations）。
-- 生产只跑 db/migration，不会创建默认管理员。
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version)
select 'admin0001', '系统管理员', 'admin@user-center.local',
       '$2a$10$Jm7AF8rSUo7lfJqt1JpKaOR32VH/HNi5b7hkhhLH8o522GAhqMoFq', 1, 0, 0, 0
where not exists (select 1 from `user` where email = 'admin@user-center.local');
