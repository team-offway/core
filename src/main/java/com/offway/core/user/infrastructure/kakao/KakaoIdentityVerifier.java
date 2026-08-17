package com.offway.core.user.infrastructure.kakao;

import com.offway.core.user.config.AuthProperties;
import com.offway.core.user.domain.AuthProvider;
import com.offway.core.user.domain.SocialIdentity;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.infrastructure.social.SocialIdentityVerifier;
import java.util.List;
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
        List<String> allowedAppIds = authProperties.audiencesOf(AuthProvider.KAKAO);
        // 앱 번호가 없으면 "우리 앱 토큰인가" 를 물을 수가 없다 — 남의 앱 토큰을 받아주느니 provider 를 닫는다.
        // Apple·Google 에서 audience 가 비었을 때와 같은 판단이다(NimbusOidcVerifier).
        if (allowedAppIds.isEmpty()) {
            log.info("앱 번호가 설정되지 않아 카카오 로그인을 받지 않는다");
            throw UserException.unsupportedProvider();
        }
        verifyIssuedToUs(credential, allowedAppIds);
        return kakaoProfileClient.fetchProfile(credential).toSocialIdentity();
    }

    /**
     * 이 액세스 토큰이 <b>우리 카카오 앱에서 발급된 것인지</b> 확인한다.
     *
     * <p>프로필 조회만으로는 답할 수 없는 질문이다. {@code /v2/user/me} 는 토큰이 유효하기만 하면 그 주인을
     * 돌려주므로, 이 확인이 없으면 <b>다른 카카오 앱에서 발급된 토큰을 그대로 우리 서버에 던져 그 사용자로
     * 로그인</b>할 수 있다. 남의 앱 토큰을 손에 넣을 수 있는 사람(그 앱 개발자·그 앱과 연동된 서버)이 우리
     * 서비스의 아무 계정이나 가져가는 경로가 된다.
     *
     * <p>Apple·Google 에서 {@code aud} 가 막는 것과 정확히 같은 자리다. 그래서 실패도 같은 code 로 내린다 —
     * 토큰 자체는 진짜지만 <b>우리 것이 아니라</b> 무효다.
     */
    private void verifyIssuedToUs(String credential, List<String> allowedAppIds) {
        KakaoTokenInfo tokenInfo = kakaoProfileClient.fetchTokenInfo(credential);
        if (tokenInfo.issuedByAnyOf(allowedAppIds)) {
            return;
        }
        // 앱 번호는 비밀이 아니고, 어느 앱에서 온 토큰인지가 이 경고의 전부다. 토큰은 남기지 않는다.
        log.warn("다른 카카오 앱에서 발급된 액세스 토큰으로 로그인 시도 appId={}", tokenInfo.appId());
        throw UserException.invalidIdToken(null);
    }
}
