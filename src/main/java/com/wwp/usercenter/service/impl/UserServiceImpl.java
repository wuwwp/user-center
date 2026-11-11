package com.wwp.usercenter.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wwp.usercenter.model.User;
import com.wwp.usercenter.service.UserService;
import com.wwp.usercenter.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2026-11-11 19:44:40
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

}




