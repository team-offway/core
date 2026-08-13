package com.offway.core.common.logging;

/**
 * 로그 한 줄에 실을 자기 요약을 스스로 내는 DTO.
 *
 * <p>매핑 로직을 DTO 자신에 두는 프로젝트 규약(CLAUDE.md)의 연장이다 — {@code from(도메인)} 이 있는 자리에
 * {@code logSummary()} 도 둔다. 별도 Mapper·Formatter 빈을 만들지 않는다.
 *
 * <p>구현하지 않은 DTO 는 요약을 내지 않는다 — {@link RequestLoggingFilter} 가 {@code req=[...]}·
 * {@code res=[...]} 조각을 통째로 뺀다(기본 요약으로 떨어지는 것이 아니다). 시끄러운 엔드포인트부터 붙이고
 * 나머지는 필요해질 때 붙인다.
 */
public interface LogSummary {

    /** 예: {@code 추천=20건 (76:영월1.00 7:삼척0.94 …외 15건)} */
    String logSummary();
}
