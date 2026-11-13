package com.wwp.usercenter.controller;

import com.wwp.usercenter.common.BaseResponse;
import com.wwp.usercenter.model.dto.CurrentUserDTO;
import com.wwp.usercenter.security.SecurityUtils;
import com.wwp.usercenter.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public BaseResponse<CurrentUserDTO> currentUser() {
    return BaseResponse.success(userService.getCurrentUser(SecurityUtils.currentUserId()));
  }
}
