package com.wwp.usercenter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.LoginResult;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.LoginParam;
import com.wwp.usercenter.security.JwtTokenProvider;
import com.wwp.usercenter.service.AuthService;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  /** 账号不存在时也执行一次 BCrypt 比对，避免通过响应耗时枚举已注册邮箱。 */
  private final String dummyPasswordHash;

  public AuthServiceImpl(
      UserMapper userMapper, PasswordEncoder passwordEncoder, JwtTokenProvider jwtTokenProvider) {
    this.userMapper = userMapper;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenProvider = jwtTokenProvider;
    this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
  }

  @Override
  @Transactional
  public LoginResult login(LoginParam param) {
    User user =
        userMapper.selectOne(
            Wrappers.<User>lambdaQuery().eq(User::getEmail, param.email().trim().toLowerCase()));

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

    User update = new User();
    update.setId(user.getId());
    update.setLastLoginTime(LocalDateTime.now());
    userMapper.updateById(update);

    String token = jwtTokenProvider.createToken(user);
    log.info("user login, userId={}", user.getId());
    return new LoginResult(token, "Bearer", jwtTokenProvider.getExpiration().toSeconds());
  }
}
