package com.wwp.usercenter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.dto.RegisterResult;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.EmailScene;
import com.wwp.usercenter.model.enums.UserRole;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.LoginParam;
import com.wwp.usercenter.model.param.RegisterParam;
import com.wwp.usercenter.model.param.ResetPasswordParam;
import com.wwp.usercenter.model.param.SendEmailCodeParam;
import com.wwp.usercenter.security.JwtTokenProvider;
import com.wwp.usercenter.service.AuthService;
import com.wwp.usercenter.service.EmailSender;
import com.wwp.usercenter.service.UserCredentialService;
import com.wwp.usercenter.service.VerificationCodeService;
import com.wwp.usercenter.util.EmailUtils;
import com.wwp.usercenter.util.UserAccountUtils;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证流程实现。
 *
 * <p>注意：本类的方法刻意不加事务。BCrypt 计算耗时，放在事务里会长时间占用数据库连接； 只有 {@link UserCredentialService}
 * 的短写入方法带事务（docs/plan.md §8.2）。
 */
@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;
  private final VerificationCodeService verificationCodeService;
  private final EmailSender emailSender;
  private final UserCredentialService userCredentialService;

  /** 账号不存在时也执行一次 BCrypt 比对，避免通过响应耗时枚举已注册邮箱。 */
  private final String dummyPasswordHash;

  public AuthServiceImpl(
      UserMapper userMapper,
      PasswordEncoder passwordEncoder,
      JwtTokenProvider jwtTokenProvider,
      VerificationCodeService verificationCodeService,
      EmailSender emailSender,
      UserCredentialService userCredentialService) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.verificationCodeService = verificationCodeService;
    this.emailSender = emailSender;
    this.userCredentialService = userCredentialService;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Override
  public LoginResult login(LoginParam param) {
    User user = findByEmail(param.email());
    if (user == null) {
      passwordEncoder.matches(param.password(), dummyPasswordHash);
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (!passwordEncoder.matches(param.password(), user.getPasswordHash())) {
      log.warn("login failed, userId={}", user.getId());
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }
    if (user.getIsDelete() == DeletedFlag.DELETED) {
      throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
    }
    if (user.getStatus() == UserStatus.BANNED) {
      throw new BusinessException(ErrorCode.ACCOUNT_BANNED);
    }

    userCredentialService.recordLogin(user.getId());
    String token = jwtTokenProvider.createToken(user);
    log.info("user login, userId={}", user.getId());
    return new LoginResult(token, "Bearer", jwtTokenProvider.getExpiration().toSeconds());
  }

  @Override
  public RegisterResult register(RegisterParam param) {
    String email = EmailUtils.normalize(param.email());
    User existing = findByEmail(email);
    if (existing != null) {
      if (existing.getIsDelete() == DeletedFlag.DELETED) {
        throw new BusinessException(ErrorCode.CONFLICT, "该邮箱已注销，请使用账号恢复流程");
      }
      throw new BusinessException(ErrorCode.CONFLICT, "邮箱已注册");
    }

    verificationCodeService.verifyAndConsume(email, EmailScene.REGISTER, param.code());

    User user = new User();
    user.setUserAccount(UserAccountUtils.generate());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(param.password()));
    user.setRole(UserRole.USER);
    user.setStatus(UserStatus.NORMAL);
    user.setIsDelete(DeletedFlag.NOT_DELETED);
    user.setTokenVersion(0);
    try {
      userMapper.insert(user);
    } catch (DuplicateKeyException exception) {
      // 并发注册或 user_account 随机碰撞，由唯一索引兜底
      throw new BusinessException(ErrorCode.CONFLICT, "邮箱已注册");
    }
    log.info("user registered, userId={}", user.getId());
    return new RegisterResult(user.getUserAccount());
  }

  @Override
  public void sendEmailCode(SendEmailCodeParam param) {
    String email = EmailUtils.normalize(param.email());
    User user = findByEmail(email);
    String code = verificationCodeService.issue(email, param.scene());
    if (shouldDeliver(user, param.scene())) {
      try {
        emailSender.sendVerificationCode(email, param.scene(), code);
      } catch (MailException exception) {
        // 邮件故障不能改变对外响应，否则“可投递/不可投递”的差异会变成用户枚举信号
        log.error(
            "send verification code failed, scene={}, email={}",
            param.scene().getCode(),
            EmailUtils.mask(email),
            exception);
      }
    }
    log.info(
        "verification code issued, scene={}, email={}",
        param.scene().getCode(),
        EmailUtils.mask(email));
  }

  @Override
  public void logout(Long userId) {
    userCredentialService.revokeTokens(userId);
    log.info("user logout, userId={}", userId);
  }

  @Override
  public void resetPassword(ResetPasswordParam param) {
    String email = EmailUtils.normalize(param.email());
    verificationCodeService.verifyAndConsume(email, EmailScene.RESET, param.code());
    User user = findByEmail(email);
    if (user == null) {
      throw new BusinessException(ErrorCode.VERIFY_CODE_INVALID);
    }
    if (user.getIsDelete() == DeletedFlag.DELETED) {
      throw new BusinessException(ErrorCode.ACCOUNT_DELETED);
    }

    userCredentialService.updatePassword(user.getId(), passwordEncoder.encode(param.newPassword()));
    log.info("password reset by email code, userId={}", user.getId());
  }

  private User findByEmail(String email) {
    return userMapper.selectOne(
        Wrappers.<User>lambdaQuery().eq(User::getEmail, EmailUtils.normalize(email)));
  }

  /** 只向确实需要该场景验证码的账号投递，响应文案保持一致。 */
  private static boolean shouldDeliver(User user, EmailScene scene) {
    return switch (scene) {
      case REGISTER -> user == null;
      case RESET -> user != null && user.getIsDelete() == DeletedFlag.NOT_DELETED;
      case RECOVER -> user != null && user.getIsDelete() == DeletedFlag.DELETED;
    };
  }
}
