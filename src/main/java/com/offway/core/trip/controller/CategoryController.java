package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.trip.controller.dto.CategoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController implements CategoryApi {

    @Override
    @GetMapping
    public ApiResponseBody<CategoryResponse> categories() {
        return ApiResponseBody.ok(CategoryResponse.of());
    }
}
