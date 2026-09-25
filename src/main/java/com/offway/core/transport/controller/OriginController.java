package com.offway.core.transport.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.transport.controller.dto.OriginSuggestionResponse;
import com.offway.core.transport.service.OriginSuggestService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 출발지 자동완성 — 문서 계약은 {@link OriginApi}. */
@RestController
@RequestMapping("/api/v1/origins")
@RequiredArgsConstructor
public class OriginController implements OriginApi {

    private final OriginSuggestService originSuggestService;

    /**
     * {@inheritDoc}
     *
     * <p><b>검색어를 선택으로 둔다.</b> 자동완성은 입력창이 비어 있는 상태로 시작하는데, 그때 400 을
     * 내리면 화면이 열리는 순간 오류가 뜬다. 짧은 검색어와 같은 결과(빈 배열)로 답한다 — 목록 조회에서
     * 잘못된 값을 거절하지 않고 자르는 규칙과 같은 판단이다.
     */
    @Override
    @GetMapping
    public ApiResponseBody<List<OriginSuggestionResponse>> suggest(
            @RequestParam(required = false) String query) {
        return ApiResponseBody.ok(OriginSuggestionResponse.from(originSuggestService.suggest(query)));
    }
}
