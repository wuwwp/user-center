package com.wwp.usercenter.model.dto;

import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import java.time.LocalDateTime;

/** 管理端用户摘要；邮箱掩码，不包含密码哈希。 */
public record UserSummaryDTO(
    String userAccount,
    String nickname,
    String emailMasked,
    UserRole role,
    UserStatus status,
    LocalDateTime createTime,
    LocalDateTime lastLoginTime) {}
