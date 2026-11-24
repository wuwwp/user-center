-- 压测收尾：把「修改密码」段（60001–80000）和「忘记密码」段（80001–96000）
-- 的密码恢复成种子哈希，使压测可以重复跑。
--
-- 为什么需要：改密 / 重置会把 password_hash 改成 PerfNew123456，
-- 不恢复的话第二轮的登录会全部失败（验证码也是一次性的，需重新预置）。
--
-- 用法：
--   docker compose exec -T mysql mysql -uroot -p"$DB_ROOT_PASSWORD" usercenter < perf/reset-scenario-accounts.sql

-- 所有压测账号的初始密码哈希，与 perf/seed-users.sql 保持一致
set @seed_hash = '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a';

-- 分 4 批，每批约 1 万行，避免单条大事务
update `user`
set password_hash = @seed_hash
where email >= 'perf060001@perf.local' and email <= 'perf070000@perf.local';

update `user`
set password_hash = @seed_hash
where email >= 'perf070001@perf.local' and email <= 'perf080000@perf.local';

update `user`
set password_hash = @seed_hash
where email >= 'perf080001@perf.local' and email <= 'perf090000@perf.local';

update `user`
set password_hash = @seed_hash
where email >= 'perf090001@perf.local' and email <= 'perf096000@perf.local';
