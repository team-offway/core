package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.social.SocialIdentityVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 카카오 신원 확인 전략 — 액세스 토큰으로 프로필을 조회해 회원번호를 얻는다.
 *
 * <p>Apple·Google 과 달리 <b>로그인 경로에 외부 호출이 낀다.</b> 그래서 이 호출은 트랜잭션 밖에서 끝나야 하는데,
 * {@code AuthService} 가 검증을 먼저 하고 DB 작업만 {@code UserPersistenceService} 에 위임하는 구조라 그 조건이 이미
 * 지켜진다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoIdentityVerifier implements SocialIdentityVerifier {

    private final AuthProperties authProperties;
    private final KakaoProfileClient kakaoProfileClient;

    @Override
    public boolean supports(AuthProvider provider) {
        return provider == AuthProvider.KAKAO;
    }

    @Override
    public SocialIdentity verify(AuthProvider provider, String credential) {
        // REST API 키가 없으면 카카오 앱이 등록되지 않았다는 뜻이라 어차피 토큰이 우리 것일 수 없다.
        // 호출 자체에 키가 들어가지는 않지만, 미설정을 여기서 끊어야 "왜 안 되는지" 가 code 로 전달된다.
        if (!authProperties.kakaoConfigured()) {
            log.info("REST API 키가 설정되지 않아 카카오 로그인을 받지 않는다");
            throw UserException.unsupportedProvider();
        }
        return kakaoProfileClient.fetchProfile(credential).toSocialIdentity();
    }
}
