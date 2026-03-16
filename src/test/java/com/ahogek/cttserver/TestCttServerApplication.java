package com.ahogek.cttserver;

import org.springframework.boot.SpringApplication;

public class TestCttServerApplication {

    static void main(String[] args) {
        SpringApplication.from(CttServerApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
