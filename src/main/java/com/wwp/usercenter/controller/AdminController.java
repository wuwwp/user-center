package com.wwp.usercenter.controller;

import com.wwp.usercenter.model.dto.AdminPasswordResetResult;
import com.wwp.usercenter.model.dto.PageResult;
import com.wwp.usercenter.model.dto.UserSummaryDTO;
import com.wwp.usercenter.model.enums.UserStatus;
import com.wwp.usercenter.model.param.AdminUserStatusParam;
import com.wwp.usercenter.security.SecurityUtils;
import com.wwp.usercenter.service.AdminUserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminController {

  private final AdminUserService adminUserService;

  @GetMapping
  public PageResult<UserSummaryDTO> list(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
      @RequestParam(required = false) UserStatus status) {
    return adminUserService.listUsers(page, size, status);
  }

  @PutMapping("/{account}/status")
  public void changeStatus(
      @PathVariable @NotBlank @Size(max = 32) String account,
      @Valid @RequestBody AdminUserStatusParam param) {
    adminUserService.changeStatus(SecurityUtils.currentUserId(), account, param.status());
  }

  @PostMapping("/{account}/password/reset")
  public AdminPasswordResetResult resetPassword(
      @PathVariable @NotBlank @Size(max = 32) String account) {
    return adminUserService.resetPassword(SecurityUtils.currentUserId(), account);
  }
}
