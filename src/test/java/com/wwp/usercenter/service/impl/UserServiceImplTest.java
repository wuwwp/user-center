package com.wwp.usercenter.service.impl;

import com.wwp.usercenter.model.User;
import com.wwp.usercenter.service.UserService;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestConstructor;

@SpringBootTest
@RequiredArgsConstructor
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class UserServiceImplTest {

    private final UserService userService;

    @Test
    void addUser(){
        User user = new User();
        user.setUsername("wwp");
        user.setUserAccount("123");
        user.setAvatarUrl("https://kkimgs.yisou.com/ims?kt=url&at=ori&key=aHR0cDovL3hpYW95YXppLjU4aXFiNHouY24vdXBsb2Fkcy9pbWFnZS8yMDIxMDUxMy8xNjIwOTE0MzU3MTA4MDUxLmpwZw==&sign=yx:SUdsj77M5ce9sGXcmZcvfTf_qe8=&tv=0_0");
        user.setUserPassword("123456");
        user.setPhone("123");
        user.setEmail("123");
        boolean result = userService.save(user);
        System.out.println(user.getId());
        Assertions.assertTrue(result);

    }
}