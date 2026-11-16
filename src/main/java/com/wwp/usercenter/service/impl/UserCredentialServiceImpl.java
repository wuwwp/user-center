package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.service.UserCredentialService;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {

  private final UserMapper userMapper;
  private final Clock clock;

  @Override
  @Transactional
  public void recordLogin(Long userId) {
    User update = new User();
    update.setId(userId);
    update.setLastLoginTime(LocalDateTime.now(clock));
    userMapper.updateById(update);
  }

  @Override
  @Transactional
  public void updatePassword(Long userId, String passwordHash) {
    userMapper.updatePassword(userId, passwordHash);
  }

  @Override
  @Transactional
  public void revokeTokens(Long userId) {
    userMapper.incrementTokenVersion(userId);
  }
}
