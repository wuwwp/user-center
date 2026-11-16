package com.wwp.usercenter.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.service.UserCredentialService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {

  private final UserMapper userMapper;

  @Override
  @Transactional
  public void recordLogin(Long userId) {
    User update = new User();
    update.setId(userId);
    update.setLastLoginTime(LocalDateTime.now());
    userMapper.updateById(update);
  }

  @Override
  @Transactional
  public void updatePassword(Long userId, String passwordHash) {
    userMapper.update(
        null,
        Wrappers.<User>lambdaUpdate()
            .set(User::getPasswordHash, passwordHash)
            .setSql("token_version = token_version + 1")
            .eq(User::getId, userId));
  }

  @Override
  @Transactional
  public void revokeTokens(Long userId) {
    userMapper.update(
        null,
        Wrappers.<User>lambdaUpdate()
            .setSql("token_version = token_version + 1")
            .eq(User::getId, userId));
  }
}
