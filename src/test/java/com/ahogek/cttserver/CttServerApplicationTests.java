package com.ahogek.cttserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@Import(TestcontainersConfiguration.class)
@TestPropertySource(
        properties =
                "ctt.security.two-factor.encryption-key="
                        + "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@ActiveProfiles("test")
@SpringBootTest
class CttServerApplicationTests {

    @Test
    void contextLoads() {}
}
