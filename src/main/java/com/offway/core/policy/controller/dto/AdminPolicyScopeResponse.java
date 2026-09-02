package com.offway.core.policy.controller.dto;

import com.offway.core.policy.service.dto.PolicyScope;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 분류를 고르면 어느 지역에 뜨는가(#393).
 *
 * <p>정책명은 싣지 않는다 — 화면이 이미 분류 이름 표를 들고 있고(POLICY_TYPES), 여기서 또
 * 내리면 같은 문구가 두 곳이 된다. 뱃지 문구는 서버가 소유하는 값이라 함께 싣는다.
 *
 * @param regionCount 곳 수. 목록에서 이 숫자만 보여주고, 어느 지역인지는 펼쳐 본다
 * @param regions 대상 지역 전부 — 어드민이 "이게 완도에 뜨나" 를 여기서 답한다
 */
public record AdminPolicyScopeResponse(
        @Schema(example = "STAY_FESTA") String type,
        @Schema(example = "숙박 할인") String badgeText,
        @Schema(description = "이 분류가 보는 지역 태그", example = "STAY_FESTA") String tag,
        @Schema(example = "85") int regionCount,
        List<RegionBrief> regions) {

    public record RegionBrief(
            @Schema(example = "17") long id,
            @Schema(example = "전남 완도군") String name) {
    }

    public static List<AdminPolicyScopeResponse> from(List<PolicyScope> scopes) {
        return scopes.stream().map(AdminPolicyScopeResponse::from).toList();
    }

    public static AdminPolicyScopeResponse from(PolicyScope scope) {
        return new AdminPolicyScopeResponse(
                scope.type().name(),
                scope.type().badgeText(),
                scope.type().targetTag().name(),
                scope.regionCount(),
                scope.regions().stream()
                        .map(region -> new RegionBrief(region.getId(), region.shortName()))
                        .toList());
    }
}
