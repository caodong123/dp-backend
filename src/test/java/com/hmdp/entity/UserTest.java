package com.hmdp.entity;

import com.hmdp.service.IUserService;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.List;

@SpringBootTest
public class UserTest {

    @Resource
    private IUserService userService;

    @Test
    public void testList() {
        List<User> list = userService.list();
        for (User user : list) {
            System.out.println(user);
        }
    }
}
