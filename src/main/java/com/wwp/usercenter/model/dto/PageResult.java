package com.wwp.usercenter.model.dto;

import java.util.List;

/** 稳定分页响应，不直接暴露 MyBatis-Plus 的 Page 结构。 */
public record PageResult<T>(List<T> items, long total, long page, long size, long totalPages) {}
