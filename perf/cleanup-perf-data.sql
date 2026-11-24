-- 压测收尾：分批删除所有压测账号。
-- 不用 truncate：会把种子管理员一起清掉。
-- like 前缀是常量，可走 uk_email 的范围扫描，不会全表扫。
--
-- 用法：
--   docker compose exec -T mysql mysql -uroot -p"$DB_ROOT_PASSWORD" usercenter < perf/cleanup-perf-data.sql

-- 种子账号：10 万条，每批 5000 共 20 批
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;
delete from `user` where email like 'perf%@perf.local' limit 5000;

-- 注册场景新建的账号：最多 16000 条，每批 5000 共 4 批
delete from `user` where email like 'reg-%@perf.local' limit 5000;
delete from `user` where email like 'reg-%@perf.local' limit 5000;
delete from `user` where email like 'reg-%@perf.local' limit 5000;
delete from `user` where email like 'reg-%@perf.local' limit 5000;

-- 校验：两行都应为 0
select sum(email like 'perf%@perf.local') as perf_left,
       sum(email like 'reg-%@perf.local') as reg_left
from `user`;
