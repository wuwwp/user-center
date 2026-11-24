-- 压测造数：10 万条假用户。只用于压测库，不要在生产库里长期保留。
-- 密码统一为 PerfSeed123456（BCrypt cost=10，全表复用同一个哈希）。
-- 重复执行会因 uk_email 冲突失败，这是预期的“已经造过”信号；先跑 cleanup-perf-data.sql 再重跑。
--
-- 用法（在云服务器仓库根目录）：
--   docker compose exec -T mysql mysql -uroot -p"$DB_ROOT_PASSWORD" usercenter < perf/seed-users.sql
--
-- 分段约定（与 perf/auth-load.js 的账号映射一一对应，改动任何一边都要同步另一边）：
--   1 .. 60000      登录场景（status=0, is_delete=0）
--   60001 .. 80000  修改密码场景（status=0, is_delete=0）
--   80001 .. 96000  忘记密码场景（status=0, is_delete=0）
--   96001 .. 98000  干扰数据：封禁（status=1）
--   98001 .. 100000 干扰数据：已注销（is_delete=1）

-- 递归 CTE 默认深度 1000，每批 1 万条需要放宽
set session cte_max_recursion_depth = 20000;

-- 批次 1：1 .. 10000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n, 6, '0')),
    concat('压测用户', n),
    concat('perf', lpad(n, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n between 96001 and 98000 then 1 else 0 end,
    case when n > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -n, now()),
    timestampadd(minute, -n, now())
from seq;

-- 批次 2：10001 .. 20000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 10000, 6, '0')),
    concat('压测用户', n + 10000),
    concat('perf', lpad(n + 10000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 10000 between 96001 and 98000 then 1 else 0 end,
    case when n + 10000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 10000), now()),
    timestampadd(minute, -(n + 10000), now())
from seq;

-- 批次 3：20001 .. 30000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 20000, 6, '0')),
    concat('压测用户', n + 20000),
    concat('perf', lpad(n + 20000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 20000 between 96001 and 98000 then 1 else 0 end,
    case when n + 20000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 20000), now()),
    timestampadd(minute, -(n + 20000), now())
from seq;

-- 批次 4：30001 .. 40000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 30000, 6, '0')),
    concat('压测用户', n + 30000),
    concat('perf', lpad(n + 30000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 30000 between 96001 and 98000 then 1 else 0 end,
    case when n + 30000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 30000), now()),
    timestampadd(minute, -(n + 30000), now())
from seq;

-- 批次 5：40001 .. 50000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 40000, 6, '0')),
    concat('压测用户', n + 40000),
    concat('perf', lpad(n + 40000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 40000 between 96001 and 98000 then 1 else 0 end,
    case when n + 40000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 40000), now()),
    timestampadd(minute, -(n + 40000), now())
from seq;

-- 批次 6：50001 .. 60000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 50000, 6, '0')),
    concat('压测用户', n + 50000),
    concat('perf', lpad(n + 50000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 50000 between 96001 and 98000 then 1 else 0 end,
    case when n + 50000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 50000), now()),
    timestampadd(minute, -(n + 50000), now())
from seq;

-- 批次 7：60001 .. 70000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 60000, 6, '0')),
    concat('压测用户', n + 60000),
    concat('perf', lpad(n + 60000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 60000 between 96001 and 98000 then 1 else 0 end,
    case when n + 60000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 60000), now()),
    timestampadd(minute, -(n + 60000), now())
from seq;

-- 批次 8：70001 .. 80000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 70000, 6, '0')),
    concat('压测用户', n + 70000),
    concat('perf', lpad(n + 70000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 70000 between 96001 and 98000 then 1 else 0 end,
    case when n + 70000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 70000), now()),
    timestampadd(minute, -(n + 70000), now())
from seq;

-- 批次 9：80001 .. 90000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 80000, 6, '0')),
    concat('压测用户', n + 80000),
    concat('perf', lpad(n + 80000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 80000 between 96001 and 98000 then 1 else 0 end,
    case when n + 80000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 80000), now()),
    timestampadd(minute, -(n + 80000), now())
from seq;

-- 批次 10：90001 .. 100000
insert into `user`
    (user_account, nickname, email, password_hash, role, status, is_delete, token_version, create_time, update_time)
with recursive seq(n) as (
    select 1
    union all
    select n + 1 from seq where n < 10000
)
select
    concat('perf', lpad(n + 90000, 6, '0')),
    concat('压测用户', n + 90000),
    concat('perf', lpad(n + 90000, 6, '0'), '@perf.local'),
    '$2a$10$OXB8hCvj92uy7ivTYDjbN.9DPY0e35dm3JOG5QbyKYZHOLGp8qq0a',
    0,
    case when n + 90000 between 96001 and 98000 then 1 else 0 end,
    case when n + 90000 > 98000 then 1 else 0 end,
    0,
    timestampadd(minute, -(n + 90000), now()),
    timestampadd(minute, -(n + 90000), now())
from seq;

-- 校验：应为 100000 / 2000 / 2000
select count(*) as total, sum(status = 1) as banned, sum(is_delete = 1) as deleted
from `user`
where email like 'perf%@perf.local';
