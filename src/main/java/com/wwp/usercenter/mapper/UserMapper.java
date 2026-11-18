package com.wwp.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wwp.usercenter.model.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper extends BaseMapper<User> {

  /** 只查询令牌校验需要的列，避免每个认证请求加载密码哈希等敏感字段。 */
  User selectAuthStateById(@Param("userId") Long userId);

  /** 当前用户资料只取展示列，不加载密码哈希。 */
  User selectCurrentUserById(@Param("userId") Long userId);

  /** 更新密码哈希并自增令牌版本，原子吊销该用户全部旧令牌。 */
  int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

  /** 自增令牌版本，原子吊销该用户全部旧令牌。 */
  int incrementTokenVersion(@Param("userId") Long userId);
}
