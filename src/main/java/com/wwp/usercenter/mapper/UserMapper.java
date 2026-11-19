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

  /** 注销/恢复前读取生命周期需要的列，不加载密码哈希。 */
  User selectLifecycleStateById(@Param("userId") Long userId);

  /** 恢复前按邮箱读取状态列，不加载密码哈希。 */
  User selectRecoverStateByEmail(@Param("email") String email);

  /** 审计写入时读取账号快照。 */
  User selectAccountById(@Param("userId") Long userId);

  /** 管理端原子更新状态并自增令牌版本。 */
  int updateStatusAndIncrementTokenVersion(
      @Param("userId") Long userId, @Param("status") UserStatus status);

  /** 逻辑注销并自增令牌版本；只允许正常状态、未注销账号。 */
  int softDeleteAccount(@Param("userId") Long userId);

  /** 恢复已注销账号、设置新密码并自增令牌版本；只允许正常状态。 */
  int recoverAccount(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

  /** 更新密码哈希并自增令牌版本，原子吊销该用户全部旧令牌。 */
  int updatePassword(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);

  /** 自增令牌版本，原子吊销该用户全部旧令牌。 */
  int incrementTokenVersion(@Param("userId") Long userId);
}
