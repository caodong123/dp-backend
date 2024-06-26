package com.hmdp.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ShopServiceImplTest {

    @Resource
    private ShopServiceImpl shopService;

    @Test
    void saveShop2Redis() {
        shopService.saveShop2Redis(1L,10L);
    }
}