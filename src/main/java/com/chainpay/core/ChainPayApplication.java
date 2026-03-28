package com.chainpay.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChainPayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChainPayApplication.class, args);
    }
}
