-- 管理端列表按未注销 + 状态过滤；InnoDB 二级索引包含主键，按 id 倒序可直接利用。
alter table `user` add index idx_user_status_delete (is_delete, status);
