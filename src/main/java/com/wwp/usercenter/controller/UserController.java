package com.wwp.usercenter.controller;

import com.wwp.usercenter.model.dto.CurrentUserDTO;
import com.wwp.usercenter.model.param.ChangePasswordParam;
import com.wwp.usercenter.model.param.DeleteAccountParam;
import com.wwp.usercenter.model.param.RecoverAccountParam;
import com.wwp.usercenter.security.SecurityUtils;
import com.wwp.usercenter.service.AccountLifecycleService;
import com.wwp.usercenter.service.UserService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;
  private final AccountLifecycleService accountLifecycleService;

  @GetMapping("/me")
  public CurrentUserDTO currentUser() {
    return userService.getCurrentUser(SecurityUtils.currentUserId());
  }

  @PutMapping("/me/password")
  public void changePassword(@Valid @RequestBody ChangePasswordParam param) {
    userService.changePassword(SecurityUtils.currentUserId(), param);
  }

  @DeleteMapping("/me")
  public void deleteAccount(@Valid @RequestBody DeleteAccountParam param) {
    accountLifecycleService.deleteAccount(SecurityUtils.currentUserId(), param);
  }

  @PostMapping("/me/recover")
  public void recoverAccount(@Valid @RequestBody RecoverAccountParam param) {
    accountLifecycleService.recoverAccount(param);
  }
}
