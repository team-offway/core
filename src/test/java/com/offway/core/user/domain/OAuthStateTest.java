package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 웹 로그인 왕복을 잇는 값(#343).
 *
 * <p><b>이것이 조용히 깨지면 로그인 CSRF 가 열린다.</b> 값이 겹치거나 대조가 헐거워지면, 남이 만든 인가
 * 코드로 우리 어드민을 로그인시킬 수 있다. 부팅도 되고 로그인도 되므로 증상으로는 드러나지 않는다.
 */
class OAuthStateTest {

    /** 겹침을 볼 만큼은 되고 테스트가 느려지지는 않는 수. */
    private static final int SAMPLE = 1_000;

    @Test
    void 만들_때마다_다른_값이다() {
        Set<String> issued = new HashSet<>();
        IntStream.range(0, SAMPLE).forEach(i -> issued.add(OAuthState.issue().value()));

        // 겹치면 두 사람의 로그인이 서로의 왕복을 통과시킨다.
        assertEquals(SAMPLE, issued.size());
    }

    @Test
    void 같은_값이_돌아오면_우리가_시작한_로그인이다() {
        OAuthState state = OAuthState.issue();

        assertTrue(state.matches(state.value()));
    }

    @Test
    void 다른_값이_돌아오면_거절한다() {
        OAuthState state = OAuthState.issue();

        assertFalse(state.matches(OAuthState.issue().value()));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void 값이_없으면_거절한다(String candidate) {
        // 쿠키가 만료됐거나 콜백에 state 가 없는 경우다. 둘 다 정상적인 왕복이 아니다.
        assertFalse(OAuthState.issue().matches(candidate));
    }

    @Test
    void 앞부분만_같은_값도_거절한다() {
        OAuthState state = OAuthState.issue();

        assertFalse(state.matches(state.value().substring(0, state.value().length() - 1)));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void 빈_값으로는_만들_수_없다(String value) {
        // 빈 state 를 허용하면 쿠키가 없는 요청과 있는 요청이 같은 결과를 낸다.
        assertThrows(RuntimeException.class, () -> new OAuthState(value));
    }

    @Test
    void 주소에_그대로_실을_수_있는_문자만_쓴다() {
        // 인코딩이 필요한 문자가 섞이면 카카오가 돌려준 값과 우리 값이 어긋난다.
        assertTrue(OAuthState.issue().value().matches("[A-Za-z0-9_-]+"));
    }
}
