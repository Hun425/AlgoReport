package com.algoreport.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Import

/**
 * 글로벌 테스트 설정
 * 모든 테스트에서 공통으로 사용할 설정들을 포함
 */
@TestConfiguration
@Import(
    EmbeddedRedisConfig::class,
    TestConfiguration::class
)
open class GlobalTestConfiguration