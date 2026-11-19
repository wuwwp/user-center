package com.wwp.usercenter.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wwp.usercenter.common.BusinessException;
import com.wwp.usercenter.common.ErrorCode;
import com.wwp.usercenter.mapper.UserMapper;
import com.wwp.usercenter.model.dto.AdminPasswordResetResult;
import com.wwp.usercenter.model.dto.PageResult;
import com.wwp.usercenter.model.dto.UserSummaryDTO;
import com.wwp.usercenter.model.entity.User;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.service.AdminUserService;
import com.wwp.usercenter.service.UserCredentialService;
import com.wwp.usercenter.util.EmailUtils;
import com.wwp.usercenter.util.PasswordGenerator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

  private static final int RESET_PASSWORD_LENGTH = 16;

  private final UserMapper userMapper;
  private final UserCredentialService userCredentialService;
  private final PasswordEncoder passwordEncoder;

  @Override
  public PageResult<UserSummaryDTO> listUsers(int page, int size, UserStatus status) {
    Page<User> pageRequest = new Page<>(page, size);
    IPage<User> result = userMapper.selectAdminUserPage(pageRequest, status);
    List<UserSummaryDTO> items =
        result.getRecords().stream().map(AdminUserServiceImpl::toSummary).toList();
    return new PageResult<>(
        items, result.getTotal(), result.getCurrent(), result.getSize(), result.getPages());
  }

  @Override
  public void changeStatus(Long actorId, String userAccount, UserStatus status) {
    User target = requireActiveTarget(userAccount);
    if (status == UserStatus.BANNED && target.getId().equals(actorId)) {
      throw new BusinessException(ErrorCode.PARAM_INVALID, "不能封禁自己");
    }
    if (target.getStatus() == status) {
      return;
    }
    int updated = userMapper.updateStatusAndIncrementTokenVersion(target.getId(), status);
    if (updated == 0) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    log.info(
        "admin changed user status, actorId={}, targetId={}, status={}",
        actorId,
        target.getId(),
        status);
  }

  @Override
  public AdminPasswordResetResult resetPassword(Long actorId, String userAccount) {
    User target = requireActiveTarget(userAccount);
    String newPassword = PasswordGenerator.generate(RESET_PASSWORD_LENGTH);
    String passwordHash = passwordEncoder.encode(newPassword);
    // BCrypt 在事务外；updatePassword 内部只做一次短事务并自增 token_version
    userCredentialService.updatePassword(target.getId(), passwordHash);
    log.info("admin reset user password, actorId={}, targetId={}", actorId, target.getId());
    return new AdminPasswordResetResult(target.getUserAccount(), newPassword);
  }

  private User requireActiveTarget(String userAccount) {
    User user = userMapper.selectAdminStateByAccount(userAccount);
    if (user == null || user.isDeleted()) {
      throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    return user;
  }

  private static UserSummaryDTO toSummary(User user) {
    return new UserSummaryDTO(
        user.getUserAccount(),
        user.getNickname(),
        EmailUtils.mask(user.getEmail()),
        user.getRole(),
        user.getStatus(),
        user.isDeleted(),
        user.getCreateTime(),
        user.getLastLoginTime());
  }
}
