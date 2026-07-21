package com.offway.core.common.response;

/**
 * 페이지네이션 메타.
 *
 * @param page 0-based 현재 페이지
 * @param size 페이지 크기
 * @param totalElements 전체 건수
 * @param totalPages 전체 페이지 수
 */
public record PageResponse(int page, int size, long totalElements, int totalPages) {}
