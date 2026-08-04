package com.offway.core.trip.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.common.response.PageResponse;
import com.offway.core.trip.controller.dto.RegionPlacesResponse;
import com.offway.core.trip.domain.PlaceCategory;
import com.offway.core.trip.domain.PlaceKind;
import com.offway.core.trip.service.RegionPlaceService;
import com.offway.core.trip.service.dto.RegionPlaces;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@RequiredArgsConstructor
public class RegionPlaceController implements RegionPlaceApi {

    private final RegionPlaceService regionPlaceService;

    @Override
    @GetMapping("/{regionId}/places")
    public ApiResponseBody<RegionPlacesResponse> places(
            @PathVariable Long regionId,
            @RequestParam PlaceKind kind,
            @RequestParam(required = false) PlaceCategory category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        RegionPlaces places = regionPlaceService.places(regionId, kind, category, page, size);
        return ApiResponseBody.ok(
                RegionPlacesResponse.from(kind, places),
                new PageResponse(places.page(), places.size(), places.totalElements(), places.totalPages()));
    }
}
