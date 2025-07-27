package com.algoreport

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(classes = [AlgoReportApplication::class, com.algoreport.config.TestConfiguration::class])
@ActiveProfiles("test")
class AlgoReportApplicationTests {

    @Test
    fun contextLoads() {
        // Spring Context가 정상적으로 로드되는지 확인
    }
}