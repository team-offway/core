package com.offway.core.curation.service;

import com.offway.core.curation.domain.ThumbnailUpload;
import com.offway.core.curation.infrastructure.storage.ThumbnailStorage;
import com.offway.core.curation.infrastructure.storage.UploadTicket;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 백오피스 썸네일 업로드 자리 발급(#377).
 *
 * <p><b>트랜잭션이 없다.</b> DB 를 건드리지 않는다 — 여기서 하는 일은 "올려도 되는 값인가" 를 가리고
 * 서명된 주소를 내주는 것뿐이고, 올린 결과가 실제로 쓰이는 것은 어드민이 그 주소를 링크에 저장할 때다.
 *
 * <p>그래서 <b>버려지는 오브젝트가 생길 수 있다</b> — 주소만 받고 저장을 안 하거나, 올린 뒤 폼을 닫는
 * 경우다. 지금은 그대로 둔다. 어드민 몇 명이 쓰는 화면이라 쌓이는 양이 적고, 지우려면 "저장 안 된
 * 오브젝트" 를 판정하는 별도 배치가 필요한데 그 값이 아직 비용보다 크지 않다.
 */
@Service
@RequiredArgsConstructor
public class ThumbnailUploadService {

    private final ThumbnailStorage thumbnailStorage;

    /**
     * 이 업로드 한 건에만 쓰는 주소를 낸다.
     *
     * <p>가리는 일은 이미 끝난 값을 받는다 — 종류·크기는 도메인 규칙이라({@link ThumbnailUpload})
     * 서비스에 분기로 쌓지 않는다.
     */
    public UploadTicket issue(ThumbnailUpload upload) {
        return thumbnailStorage.presign(upload);
    }
}
