package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.CurrentUserDTO;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.DeletedFlag;
import com.wwp.usercenter.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserMapper userMapper;

  @Override
  public CurrentUserDTO getCurrentUser(Long userId) {
    User user = userMapper.selectById(userId);
    if (user == null || user.getIsDelete() == DeletedFlag.DELETED) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    return new CurrentUserDTO(
        user.getUserAccount(), user.getNickname(), user.getEmail(), user.getRole());
  }
}
