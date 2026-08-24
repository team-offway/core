package com.offway.core.user.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AuthProviderTest {

    @ParameterizedTest
    @CsvSource({
        "kakao,KAKAO",
        "KAKAO,KAKAO",
        "Kakao,KAKAO",
        "apple,APPLE",
        "google,GOOGLE",
        "'  google  ',GOOGLE"
    })
    void 경로값을_대소문자_공백_상관없이_해석한다(String pathValue, AuthProvider expected) {
        assertEquals(expected, AuthProvider.from(pathValue));
    }

    @ParameterizedTest
    @ValueSource(strings = {"naver", "facebook", "GOOGL", "kakao-talk"})
    void 지원하지_않는_provider는_USER_002다(String pathValue) {
        // Spring 기본 변환 실패(형식 오류)에 맡기면 사유가 code 로 전달되지 않는다.
        UserException exception = assertThrows(UserException.class, () -> AuthProvider.from(pathValue));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void 값이_없어도_USER_002로_끊는다(String pathValue) {
        UserException exception = assertThrows(UserException.class, () -> AuthProvider.from(pathValue));

        assertEquals(UserErrorCode.UNSUPPORTED_PROVIDER, exception.errorCode());
    }

    @Test
    void 애플_구글은_서명_검증에_필요한_값을_들고_있다() {
        assertTrue(AuthProvider.GOOGLE.oidc().isPresent());
        assertTrue(AuthProvider.APPLE.oidc().isPresent());
    }

    @Test
    void 카카오는_서명_검증_대상이_아니다() {
        // 이 빈 값이 곧 "프로필 조회로 확인한다"는 분류다.
        assertTrue(AuthProvider.KAKAO.oidc().isEmpty());
    }

    @Test
    void 애플은_ID토큰에_이름을_담지_않는다() {
        // 그래서 최초 로그인 요청의 name 이 유일한 출처가 된다.
        assertTrue(AuthProvider.APPLE
                .oidc()
                .orElseThrow()
                .nicknameClaimIfPresent()
                .isEmpty());
    }

    @Test
    void 구글은_iss_표기_두_가지를_모두_받는다() {
        // Google 은 스킴 있는 표기와 없는 표기를 모두 낸다. 하나만 허용하면 다른 표기를 받은 사용자가 전부
        // 401 이 된다 — Google 자신의 검증 라이브러리도 둘 다 받는다.
        assertEquals(
                List.of("https://accounts.google.com", "accounts.google.com"),
                AuthProvider.GOOGLE.oidc().orElseThrow().issuers());
    }

    @Test
    void 애플은_iss_표기가_하나다() {
        assertEquals(
                List.of("https://appleid.apple.com"),
                AuthProvider.APPLE.oidc().orElseThrow().issuers());
    }

    @Test
    void 구글은_ID토큰의_name_클레임에서_이름을_얻는다() {
        assertEquals(
                "name",
                AuthProvider.GOOGLE.oidc().orElseThrow().nicknameClaimIfPresent().orElseThrow());
    }

    /**
     * 사진 주소를 <b>이미 검증한 토큰에서</b> 얻는다(#308) — 사진 때문에 외부 호출을 더 하지 않는다.
     *
     * <p>클레임 이름이 틀어지면 마이 화면 아바타가 조용히 기본 아이콘으로만 남는다. 응답은 정상이고 필드도
     * 실리니 아무도 오류로 알아채지 못한다.
     */
    @Test
    void 구글은_ID토큰의_picture_클레임에서_사진을_얻는다() {
        assertEquals(
                "picture",
                AuthProvider.GOOGLE.oidc().orElseThrow().pictureClaimIfPresent().orElseThrow());
    }

    @Test
    void 애플은_ID토큰에_사진을_담지_않는다() {
        // provider 가 아예 주지 않아 서버가 할 수 있는 일이 없다 — null 이 정답이다.
        assertTrue(AuthProvider.APPLE
                .oidc()
                .orElseThrow()
                .pictureClaimIfPresent()
                .isEmpty());
    }
}
