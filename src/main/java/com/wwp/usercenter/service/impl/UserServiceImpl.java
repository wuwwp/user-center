package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.CurrentUserDTO;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.model.param.ChangePasswordParam;
import com.wwp.usercenter.service.UserCredentialService;
import com.wwp.usercenter.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final UserCredentialService userCredentialService;

  @Override
  public CurrentUserDTO getCurrentUser(Long userId) {
    User user = userMapper.selectById(userId);
    if (user == null || user.getIsDelete() == DeletedFlag.DELETED) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    return new CurrentUserDTO(
        user.getUserAccount(), user.getNickname(), user.getEmail(), user.getRole());
  }

  @Override
  public void changePassword(Long userId, ChangePasswordParam param) {
    User user = userMapper.selectById(userId);
    if (user == null || user.getIsDelete() == DeletedFlag.DELETED) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    if (!passwordEncoder.matches(param.oldPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "原密码错误");
    }
    if (passwordEncoder.matches(param.newPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.PARAM_INVALID, "新密码不能与原密码相同");
    }

    // BCrypt 编码在事务外完成，事务只包住一次更新
    userCredentialService.updatePassword(user.getId(), passwordEncoder.encode(param.newPassword()));
    log.info("password changed, userId={}", userId);
  }
}
