package com.offway.core.liveactivity.service;

import com.offway.core.liveactivity.domain.PushToStartToken;
import com.offway.core.liveactivity.repository.PushToStartTokenRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 잠금화면 카드를 <b>띄울 수 있는</b> 주소를 받아 둔다(#583).
 *
 * <p>여기서 카드를 띄우지 않는다 — 주소를 보관하는 데까지다. 실제 띄우기는
 * {@link LiveActivityStarter} 가 낮에 한다.
 *
 * <p><b>로그에 토큰을 남기지 않는다.</b> 이 값을 아는 쪽은 그 기기 잠금화면에 카드를 만들 수 있어
 * 비밀값에 준한다(로깅 규약).
 *
 * <p>외부 호출이 없어 트랜잭션이 짧다 — 전부 DB 만 만진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushToStartService {

    /** 등록·갱신 시각 기준 시간대. 서비스가 한국 여행을 다루므로 사용자 로캘과 무관하게 KST 다. */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final PushToStartTokenRepository pushToStartTokenRepository;

    /**
     * 토큰을 등록하거나 이미 있으면 갱신 시각만 고쳐 쓴다.
     *
     * <p><b>코스를 묻지 않는다.</b> 이 토큰은 기기 단위라 어느 여행과도 묶이지 않는다 — 무엇을
     * 띄울지는 배치가 그날 정한다. 그래서 {@link LiveActivityService#register} 와 달리 코스 소유
     * 확인이 없다. 확인할 대상 자체가 없다.
     *
     * <p><b>있는지 먼저 보지 않는다.</b> 조회 후 분기하면 앱이 두 번 동시에 보낼 때 둘 다 "없다" 를
     * 읽고 하나가 유니크 제약에 걸린다. 판정은 제약을 쥔 DB 가 한 문장 안에서 한다.
     */
    @Transactional
    public void register(UUID userId, String token) {
        PushToStartToken registration =
                PushToStartToken.register(userId, token, LocalDateTime.now(SERVICE_ZONE));
        // **앞사람의 등록을 먼저 치운다.** 기기 하나에는 지금 한 사람만 로그인해 있다. 앞사람이
        // 로그아웃을 안 하고 계정을 바꿨으면 그 행이 남는데, 그러면 배치가 **앞사람의 여행지·날짜를
        // 지금 이 기기 잠금화면에 그린다.** 앱이 해제를 안 불러도 여기서 끊긴다.
        int evicted = pushToStartTokenRepository.deleteOthersWithToken(userId, registration.getToken());
        pushToStartTokenRepository.register(registration);
        if (evicted > 0) {
            // 계정이 바뀐 기기다. 남의 일정이 남의 화면에 뜰 뻔한 자리라 조용히 넘기지 않는다.
            log.info("잠금화면 띄우기 토큰 등록 — 같은 기기의 앞선 계정 등록 {}건을 정리했습니다", evicted);
            return;
        }
        log.info("잠금화면 띄우기 토큰 등록");
    }

    /**
     * 등록을 지운다 — <b>토큰을 함께 보내면 그 기기만</b>.
     *
     * <p>로그아웃이 이미 그렇게 갈린다(#389) — refresh 를 실으면 그 기기만, 안 실으면 전부 끊는다.
     * 여기서도 같은 기준을 쓴다. 폰에서 로그아웃했다고 태블릿 잠금화면의 카드까지 끊으면, 사용자에게는
     * "아무것도 안 했는데 사라졌다" 로 보인다.
     *
     * <p><b>지울 것이 없어도 성공이다.</b> 원한 상태("이 기기에 카드가 생기지 않는다")가 이미 이뤄져
     * 있고, 로그아웃 화면이 404 를 띄울 이유가 없다.
     *
     * @param token 이 기기의 토큰. <b>{@code null} 이면 이 사람의 모든 기기</b>를 해제한다
     */
    @Transactional
    public void unregister(UUID userId, String token) {
        if (token == null || token.isBlank()) {
            int deleted = pushToStartTokenRepository.deleteByUserId(userId);
            log.info("잠금화면 띄우기 해제 — 토큰을 안 실어 모든 기기 deleted={}", deleted);
            return;
        }
        int deleted = pushToStartTokenRepository.deleteByUserAndToken(userId, token);
        log.info("잠금화면 띄우기 해제 — 이 기기만 deleted={}", deleted);
    }
}
