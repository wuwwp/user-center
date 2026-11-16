package com.wwp.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wwp.usercenter.model.entity.User;
import org.apache.ibatis.annotations.Param;

public interface UserMapper extends BaseMapper<User> {

  /** 更新密码哈希并自增令牌版本，原子吊销该用户全部旧令牌。 */
  int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

  /** 自增令牌版本，原子吊销该用户全部旧令牌。 */
  int incrementTokenVersion(@Param("userId") Long userId);
}
