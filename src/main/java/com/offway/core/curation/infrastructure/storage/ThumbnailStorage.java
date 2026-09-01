package com.offway.core.curation.infrastructure.storage;

import com.offway.core.curation.domain.ThumbnailUpload;

/**
 * 썸네일을 올릴 자리를 내주는 port(#377). 구현은 {@code S3ThumbnailStorage}.
 *
 * <p><b>바이트를 받지 않는다.</b> 파일을 서버로 받아 올리면 이미지가 앱 메모리를 지나가고 업로드가 끝날
 * 때까지 요청 스레드를 잡는다. EC2 한 대에 MySQL 이 동거하는 형편이라 그 여유가 없다 — 대신 브라우저가
 * 직접 올릴 수 있는 <b>서명된 주소</b>만 내준다.
 */
public interface ThumbnailStorage {

    /**
     * 이 업로드 한 건에만 쓰는 주소를 낸다.
     *
     * @param upload 종류·크기가 이미 검증된 값 — 그대로 서명에 실려 S3 가 다른 요청을 거절한다
     * @throws com.offway.core.curation.domain.CurationException 자격증명이 없어 서명할 수 없는 경우
     */
    UploadTicket presign(ThumbnailUpload upload);
}
