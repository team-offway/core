package com.offway.core.curation.event;

/**
 * 큐레이션 링크가 하나 만들어졌다(#613) — <b>팀이 켤지 정해야 한다</b>는 신호다.
 *
 * <h2>왜 이벤트인가</h2>
 *
 * <p>{@code CurationAdminService} 가 알림을 직접 보내면 <b>어드민 서비스가 통보 책임을 떠안고</b>, 알림이
 * 트랜잭션 안에서 나간다. 이벤트로 던지면 리스너가 커밋 뒤에 받는다 — 가입 알림(#610)을 이벤트로 둔 것과
 * 같은 판단이다.
 *
 * <h2>왜 링크 주소를 싣지 않나</h2>
 *
 * <p>검토는 <b>백오피스에서</b> 하는 것이고, 알림은 거기로 데려가는 역할이다. 외부 주소를 문구에 실으면
 * 디스코드가 미리보기를 펼쳐 채널이 지저분해지고, 무엇보다 <b>알림만 보고 판단하게 만든다</b> — 켤지 말지는
 * 칩 문구·노출 면·기간을 함께 봐야 정해진다.
 *
 * <p>같은 이유로 {@code id} 도 안 싣는다. 어드민이 항목을 id 로 찾는 화면이 아니고, 지금 구조로는 딥링크도
 * 못 만든다({@code /admin/} 정적 SPA 가 해시를 로그인 토큰용으로 즉시 지운다).
 *
 * @param title 어드민이 붙인 제목 — 목록에서 그것을 찾을 단서다
 * @param chipText 사용자가 화면에서 읽을 한 줄. 이 값이 어색하면 켜기 전에 고쳐야 한다
 * @param surfaces 어느 면에 내릴지({@code "HOME,REGION"})
 * @param published 만든 시점의 게시 여부. <b>기본은 꺼져 있다</b> — 켜져 있으면 이미 사용자에게 나간
 *     것이라 검토가 더 급하다
 */
public record CuratedLinkAdded(String title, String chipText, String surfaces, boolean published) {}
