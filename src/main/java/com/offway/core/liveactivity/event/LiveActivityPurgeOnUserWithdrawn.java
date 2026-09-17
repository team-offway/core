package com.offway.core.liveactivity.event;

import com.offway.core.liveactivity.repository.LiveActivityTokenRepository;
import com.offway.core.liveactivity.repository.PushToStartTokenRepository;
import com.offway.core.user.event.UserWithdrawn;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 탈퇴하면 그 사람의 잠금화면 등록을 지운다 — {@code liveactivity} 가 자기 표를 스스로 치운다.
 *
 * <p>FK 를 두지 않는 규약이라 DB 가 대신 지워주지 않고, 행은 <b>없는 사용자를 가리킨 채</b> 남는다.
 * 남으면 자정 배치가 매일 그 행을 집어 들어 코스를 못 찾고 종료를 쏜다 — 아무에게도 닿지 않는 발송이
 * 한도만 쓴다.
 *
 * <p>알림(#280)이 이 리스너를 빠뜨려 뒤늦게 발견된 적이 있다. 표를 새로 만들 때 함께 만든다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveActivityPurgeOnUserWithdrawn {

    private final LiveActivityTokenRepository liveActivityTokenRepository;
    private final PushToStartTokenRepository pushToStartTokenRepository;

    /**
     * 표가 둘이라 <b>둘 다</b> 지운다(#583).
     *
     * <p>띄우기 토큰이 남으면 더 나쁘다 — 갱신 토큰은 카드가 죽으면 같이 죽지만, 띄우기 토큰은
     * 앱을 지울 때까지 살아서 <b>탈퇴한 사람의 기기에 매일 새 카드를 만든다.</b>
     */
    @EventListener
    public void on(UserWithdrawn event) {
        int cards = liveActivityTokenRepository.deleteByUserId(event.userId());
        int devices = pushToStartTokenRepository.deleteByUserId(event.userId());
        log.info("탈퇴 정리 — 잠금화면 등록 {}건 · 띄우기 토큰 {}건 삭제", cards, devices);
    }
}
