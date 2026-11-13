package com.wwp.usercenter.model.dto;

import com.wwp.usercenter.model.enums.UserRole;

public record CurrentUserDTO(String userAccount, String nickname, String email, UserRole role) {}
