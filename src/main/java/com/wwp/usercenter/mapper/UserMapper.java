package com.wwp.usercenter.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.UserStatus;
import org.apache.ibatis.annotations.Param;

public interface UserMapper extends BaseMapper<User> {

  /** 只查询令牌校验需要的列，避免每个认证请求加载密码哈希等敏感字段。 */
  User selectAuthStateById(@Param("userId") Long userId);

  /** 当前用户资料只取展示列，不加载密码哈希。 */
  User selectCurrentUserById(@Param("userId") Long userId);

  /** 管理端分页投影，只取展示列。 */
  IPage<User> selectAdminUserPage(IPage<User> page, @Param("status") UserStatus status);

  /** 管理端按对外账号取状态列，不加载密码哈希。 */
  User selectAdminStateByAccount(@Param("account") String account);

  /** 管理端原子更新状态并自增令牌版本。 */
  int updateStatusAndIncrementTokenVersion(
      @Param("userId") Long userId, @Param("status") UserStatus status);

  /** 更新密码哈希并自增令牌版本，原子吊销该用户全部旧令牌。 */
  int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

  /** 自增令牌版本，原子吊销该用户全部旧令牌。 */
  int incrementTokenVersion(@Param("userId") Long userId);
}
