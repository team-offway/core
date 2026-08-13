package com.offway.core.common.response;

/**
 * 페이지네이션 메타.
 *
 * @param page 0-based 현재 페이지
 * @param size 페이지 크기
 * @param totalElements 전체 건수
 * @param totalPages 전체 페이지 수
 */
public record PageResponse(int page, int size, long totalElements, int totalPages) {

    /** 페이지 메타를 들고 있는 조회 결과에서 — 컨트롤러마다 네 필드를 손으로 나열하지 않게. */
    public static PageResponse of(Paged paged) {
        return new PageResponse(paged.page(), paged.size(), paged.totalElements(), paged.totalPages());
    }

    /** 페이지 메타를 내려주는 조회 결과가 구현한다. service dto 가 응답 타입을 몰라도 되게 하는 접점이다. */
    public interface Paged {

        int page();

        int size();

        long totalElements();

        int totalPages();
    }

    /** Spring Data 의 페이지에서 그대로 옮긴다 — 컨트롤러마다 네 필드를 손으로 나열하지 않게. */
    public static PageResponse from(org.springframework.data.domain.Page<?> page) {
        return new PageResponse(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
