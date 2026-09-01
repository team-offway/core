package com.offway.core.curation.controller;

import com.offway.core.common.response.ApiResponseBody;
import com.offway.core.curation.controller.dto.ThumbnailUploadRequest;
import com.offway.core.curation.controller.dto.ThumbnailUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 백오피스 — 이미지 업로드 자리 발급 문서 계약(#377). 매핑은 구현체({@link AdminUploadController})가 소유한다.
 *
 * <p><b>서버가 파일을 받지 않는다.</b> 브라우저가 S3 로 직접 올리고, 서버는 그 한 건에만 쓰는 서명된
 * 주소를 내줄 뿐이다. 이미지 바이트가 앱 메모리를 지나가지 않고 업로드 시간이 요청 스레드를 잡지도 않는다.
 */
@Tag(name = "어드민 — 이미지 업로드", description = "백오피스가 쓰는 썸네일을 S3 로 직접 올리기 위한 자리")
public interface AdminUploadApi {

    @Operation(
            summary = "썸네일 업로드 자리 발급",
            description =
                    """
                    받은 주소로 브라우저가 **`PUT`** 하면 된다. 그때 요청의 `Content-Type` 과 실제 크기가
                    발급 요청에 적은 값과 **같아야** 한다 — 두 값이 서명에 들어 있어 다르면 S3 가 거절한다.

                    올리고 나면 `publicUrl` 을 큐레이션 링크의 `thumbnailUrl` 로 저장한다. 저장까지 해야
                    화면에 나가고, 발급만 받고 저장하지 않으면 아무 일도 일어나지 않는다.

                    **파일명은 보내지 않는다.** 확장자는 `contentType` 에서 뽑아 서버가 정한다.
                    """)
    @ApiResponse(responseCode = "200", description = "발급 성공")
    @ApiResponse(
            responseCode = "400",
            description = "허용하지 않는 이미지 종류(CURATION-007) · 크기가 0 이하이거나 5MB 초과(CURATION-008) · "
                    + "contentType 이 비었거나 contentLength 가 0 이하(Bean Validation)")
    @ApiResponse(responseCode = "401", description = "자격증명 없음")
    @ApiResponse(responseCode = "403", description = "어드민이 아님 — 일반 사용자 토큰이거나 Basic 자격증명")
    @ApiResponse(
            responseCode = "502",
            description = "저장소 자격증명이 배포에 없어 서명할 수 없음(CURATION-009). "
                    + "이때 어드민은 주소 붙여넣기로 등록한다")
    ApiResponseBody<ThumbnailUploadResponse> issue(ThumbnailUploadRequest request);
}
