package com.algoreport

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * 기본 테스트 - 환경 확인용
 */
class SimpleTest : BehaviorSpec({
    
    given("간단한 테스트를 실행할 때") {
        `when`("기본 연산을 수행하면") {
            val result = 2 + 2
            
            then("올바른 결과를 반환해야 한다") {
                result shouldBe 4
            }
        }
    }
})