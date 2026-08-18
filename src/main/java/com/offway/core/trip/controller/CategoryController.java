package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.CategoryResponse;
import com.offway.core.trip.service.RegionCategoryCountProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController implements CategoryApi {

    private final RegionCategoryCountProvider regionCategoryCountProvider;

    @Override
    @GetMapping
    public ApiResponseBody<CategoryResponse> categories() {
        return ApiResponseBody.ok(CategoryResponse.of(regionCategoryCountProvider.counts()));
    }
}
