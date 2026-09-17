package com.offway.core.liveactivity.repository;

import com.offway.core.liveactivity.domain.PushToStartToken;
import java.util.List;
import java.util.UUID;

/** push-to-start 토큰 저장소 port(#583). 구현은 {@link PushToStartTokenRepositoryImpl}. */
public interface PushToStartTokenRepository {

    /**
     * 등록하거나 이미 있으면 갱신 시각만 고쳐 쓴다 — <b>몇 번을 불러도 결과가 같다</b>.
     *
     * <p>앱은 시작할 때마다 같은 토큰을 다시 보낸다. 행이 늘면 같은 기기에 카드를 두 번 띄운다.
     */
    void register(PushToStartToken token);

    /** 이 사람의 등록 전부 — 모든 기기 로그아웃·탈퇴. */
    int deleteByUserId(UUID userId);

    /** 이 사람의 <b>이 기기</b> 등록 — 그 기기에서만 로그아웃할 때. 토큰이 곧 기기다. */
    int deleteByUserAndToken(UUID userId, String token);

    /**
     * 같은 토큰을 쥔 <b>다른 사람</b>의 등록을 지운다 — 계정이 바뀌었다.
     *
     * <p><b>왜 필요한가.</b> 기기 하나에는 지금 한 사람만 로그인해 있다. 그런데 앞 사람이 로그아웃을
     * 안 하고(앱이 죽었거나 그냥 계정을 바꿨거나) 새 사람이 등록하면, 앞 사람의 행이 남는다. 그러면
     * 배치가 <b>앞 사람의 여행지·날짜를 지금 이 기기 잠금화면에 그린다</b> — 남의 일정이 남의 화면에
     * 뜨는 것이라 단순한 찌꺼기가 아니다.
     *
     * <p><b>토큰 단독 유니크와 다르다.</b> 그쪽은 남의 토큰을 아는 사람이 <b>그 사람의 카드를 받아
     * 보게</b> 되지만(주인을 갈아끼우므로), 이쪽은 앞 사람의 등록이 사라질 뿐이다 — 그리고 새 주인은
     * <b>자기 여행만</b> 받는다. 잃는 쪽이 있어도 새는 쪽이 없다.
     */
    int deleteOthersWithToken(UUID userId, String token);

    /**
     * 행 하나를 지운다 — APNs 가 {@code 410 Unregistered} 로 답했을 때.
     *
     * <p><b>토큰이 아니라 행 id 로 지운다.</b> 같은 토큰이 다른 소유자로도 등록돼 있을 수 있고
     * (한 기기에 두 계정), 토큰으로 지우면 남의 등록까지 함께 사라진다.
     */
    int deleteById(long id);

    /**
     * 카드를 띄울 대상 전부.
     *
     * <p><b>상한을 두지 않는다.</b> 모수가 전체 사용자가 아니라 <b>앱을 깔고 로그인한 기기</b>이고,
     * 죽은 토큰은 같은 배치가 {@code 410} 을 받아 지우므로 스스로 줄어든다. 그 전제가 깨질 만큼
     * 늘면 페이지로 끊는다.
     */
    List<PushToStartToken> findAll();
}
