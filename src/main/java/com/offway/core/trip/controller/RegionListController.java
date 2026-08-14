package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.common.response.PageResponse;
import com.offway.core.trip.controller.dto.RegionListResponse;
import com.offway.core.trip.domain.Category;
import com.offway.core.trip.service.RegionListService;
import com.offway.core.trip.service.dto.RegionList;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionListController implements RegionListApi {

    private final RegionListService regionListService;

    @Override
    @GetMapping
    public ApiResponseBody<RegionListResponse> regions(
            @RequestParam(required = false) Category category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        RegionList regions = regionListService.list(category, page, size);
        return ApiResponseBody.ok(RegionListResponse.from(regions), PageResponse.of(regions));
    }
}
