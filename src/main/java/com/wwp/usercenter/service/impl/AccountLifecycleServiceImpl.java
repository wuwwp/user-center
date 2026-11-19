package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.param.DeleteAccountParam;
import com.wwp.usercenter.model.param.RecoverAccountParam;
import com.wwp.usercenter.service.AccountLifecycleService;
import com.wwp.usercenter.service.LoginAttemptService;
import com.wwp.usercenter.service.PasswordPolicyService;
import com.wwp.usercenter.service.VerificationCodeService;
import com.wwp.usercenter.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLifecycleServiceImpl implements AccountLifecycleService {

  private final UserMapper userMapper;
  private final VerificationCodeService verificationCodeService;
  private final PasswordPolicyService passwordPolicyService;
  private final PasswordEncoder passwordEncoder;
  private final LoginAttemptService loginAttemptService;

  @Override
  public void deleteAccount(Long userId, DeleteAccountParam param) {
    User user = userMapper.selectLifecycleStateById(userId);
    if (user == null || user.isDeleted()) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (user.getRole() == UserRole.ADMIN) {
      throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    if (!user.isActive()) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
    }

    verificationCodeService.verifyAndConsume(user.getEmail(), EmailScene.DELETE, param.code());
    // 并发下已被其他请求注销时，按幂等成功处理；旧令牌已因版本自增失效
    if (userMapper.softDeleteAccount(userId) == 0) {
      return;
    }
    log.info("user deleted account, userId={}", userId);
  }

  @Override
  public void recoverAccount(RecoverAccountParam param) {
    String email = EmailUtils.normalize(param.email());
    passwordPolicyService.ensureNotWeak(param.newPassword());
    verificationCodeService.verifyAndConsume(email, EmailScene.RECOVER, param.code());

    User user = userMapper.selectRecoverStateByEmail(email);
    if (user == null || !user.isDeleted()) {
      // 验证码通过后仍用统一错误，避免把“邮箱是否存在/是否已注销”变成枚举信号
      throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
    }
    if (user.isBanned()) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
    }

    String passwordHash = passwordEncoder.encode(param.newPassword());
    if (userMapper.recoverAccount(user.getId(), passwordHash) == 0) {
      throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
    }
    loginAttemptService.clearAccountFailures(email);
    log.info("user recovered account, userId={}", user.getId());
  }
}
