package com.offway.core.common.response;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 목록 API 의 페이지 요청 규약 — {@code page}·{@code size} 를 해석하는 한 곳.
 *
 * <p><b>왜 한 곳인가.</b> 기본값·상한을 엔드포인트마다 다시 정하면 같은 파라미터가 API 마다 다르게 동작한다.
 * 클라이언트는 그걸 문서로만 알 수 있고, 문서는 금방 낡는다.
 *
 * <p><b>상한이 핵심이다.</b> 상한이 없으면 {@code size=100000} 한 번으로 페이지네이션이 없던 때와 같은
 * 상태가 된다 — 요청 하나가 테이블 전체를 힙에 올린다(#105).
 *
 * <p>잘못된 값은 <b>거절하지 않고 자른다</b>. 목록 조회에서 음수 페이지나 과한 size 는 클라이언트 실수지 공격이
 * 아니고, 400 으로 끊으면 화면이 통째로 비는 대신 첫 페이지를 보여주는 편이 낫다.
 */
public final class Paging {

    /** 화면 한 장에 들어가는 양. 목록 카드 기준으로 잡았다. */
    public static final int DEFAULT_SIZE = 20;

    /** 한 번에 내려줄 수 있는 최대 개수. */
    public static final int MAX_SIZE = 100;

    private Paging() {
    }

    /** 정렬은 쿼리가 소유하는 경우(메서드명 {@code OrderBy}·{@code @Query}) — 여기서는 정렬을 얹지 않는다. */
    public static PageRequest of(Integer page, Integer size) {
        return of(page, size, Sort.unsorted());
    }

    public static PageRequest of(Integer page, Integer size, Sort sort) {
        return PageRequest.of(pageOf(page), sizeOf(size), sort);
    }

    private static int pageOf(Integer page) {
        return page == null ? 0 : Math.max(page, 0);
    }

    private static int sizeOf(Integer size) {
        return size == null ? DEFAULT_SIZE : Math.clamp(size, 1, MAX_SIZE);
    }
}
