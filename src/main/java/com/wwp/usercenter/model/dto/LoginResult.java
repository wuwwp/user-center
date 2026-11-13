package com.wwp.usercenter.model.dto;

public record LoginResult(String token, String tokenType, long expiresIn) {}
