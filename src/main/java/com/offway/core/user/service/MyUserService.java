package com.offway.core.user.service;

import com.offway.core.user.domain.User;
import com.offway.core.user.domain.UserException;
import com.offway.core.user.domain.UserIdentity;
import com.offway.core.user.repository.UserIdentityRepository;
import com.offway.core.user.repository.UserRepository;
import com.offway.core.user.service.dto.MyUser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 사용자 자신의 정보 조회(#282).
 *
 * <p><b>왜 필요한가.</b> 앱이 닉네임·이메일을 로그인 응답에서 받아 로컬에 저장하는 것 말고는 알 방법이 없었다.
 * 그러면 기기를 바꾸거나 앱을 다시 깔았을 때 마이페이지가 빈다 — 서버에 값이 있는데 물어볼 곳이 없었다.
 */
@Service
@RequiredArgsConstructor
public class MyUserService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    /**
     * 내 정보.
     *
     * <p><b>계정이 없으면 {@code USER-006} 이다.</b> access 토큰은 무상태라 탈퇴 후에도 만료(기본 1시간)까지
     * 서명 검증을 통과한다. 그 창에 들어온 조회를 빈 응답이나 500 으로 돌려주면 앱이 로그인 화면으로 돌아갈
     * 신호를 못 받는다 — 탈퇴 API 와 같은 규칙으로 401 을 준다.
     */
    @Transactional(readOnly = true)
    public MyUser myUser(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(UserException::withdrawnUser);
        // 연결이 없을 수 있다 — local 개발 로그인은 provider 없이 사용자만 만든다. 그때는 null 로 내린다.
        return MyUser.builder()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .provider(userIdentityRepository
                        .findFirstByUserId(userId)
                        .map(UserIdentity::getProvider)
                        .orElse(null))
                .profileImageUrl(user.getProfileImageUrl())
                .joinedAt(user.getCreatedAt())
                .build();
    }
}
