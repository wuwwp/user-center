package com.wwp.usercenter.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/** 用户实体，与 user 表一一对应。仅在 mapper/service 内部使用，不直接返回前端。 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@TableName("`user`")
public class User {

  @EqualsAndHashCode.Include
  @TableId(type = IdType.AUTO)
  private Long id;

  private String userAccount;
  private String nickname;
  private String email;

  /** 密码哈希不参与 toString，避免实体被日志打印时泄露。 */
  @ToString.Exclude private String passwordHash;

  private UserRole role;
  private UserStatus status;
  private DeletedFlag isDelete;
  private Integer tokenVersion;
  private LocalDateTime lastLoginTime;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  /** 是否已逻辑注销；注销后行仍保留，供恢复流程使用。 */
  public boolean isDeleted() {
    return isDelete == DeletedFlag.DELETED;
  }

  /** 是否已被封禁。 */
  public boolean isBanned() {
    return status == UserStatus.BANNED;
  }

  /** 未注销且状态正常，可作为正常账号登录或接收验证码。 */
  public boolean isActive() {
    return !isDeleted() && status == UserStatus.NORMAL;
  }
}
