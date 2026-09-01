package com.offway.core.curation.infrastructure.storage;

import com.offway.core.common.logging.RootCause;
import com.offway.core.curation.domain.CurationException;
import com.offway.core.curation.domain.ThumbnailUpload;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 presigned 업로드 adapter(#377).
 *
 * <p><b>자격증명이 없으면 비활성으로 뜬다.</b> 키를 부팅 조건으로 만들면 로컬·CI 가 전부 막힌다 — 키
 * 없이도 부팅되어야 한다는 것이 이 프로젝트의 불변식이다(CLAUDE.md 로컬 실행성). 그때는 발급 요청만
 * 실패하고, 어드민은 지금까지처럼 주소 붙여넣기로 등록할 수 있다.
 *
 * <p><b>서명에 종류와 크기를 함께 싣는다.</b> presigned URL 은 그 자체가 쓰기 권한이라, 조건 없이 내주면
 * URL 하나로 임의 파일을 임의 크기로 올릴 수 있다. 서명한 값과 다른 요청은 S3 가 거절한다.
 */
@Slf4j
@Component
public class S3ThumbnailStorage implements ThumbnailStorage {

    /**
     * 서명이 살아 있는 시간.
     *
     * <p>어드민이 파일을 고르고 올리는 데 걸리는 시간만 덮으면 된다. 길게 두면 화면을 떠난 뒤에도 그
     * 주소로 버킷에 쓸 수 있는 창이 그만큼 열려 있다.
     */
    private static final Duration SIGNATURE_TTL = Duration.ofMinutes(5);

    private final ThumbnailStorageProperties properties;
    private final S3Presigner presigner;

    public S3ThumbnailStorage(ThumbnailStorageProperties properties) {
        this.properties = properties;
        this.presigner = properties.isConfigured() ? presigner(properties) : null;
        if (this.presigner == null) {
            log.warn("S3 자격증명이 없어 썸네일 업로드를 비활성으로 시작합니다 — 어드민은 주소 붙여넣기로 등록합니다");
            return;
        }
        if (!properties.canDeriveDefaultUrl()) {
            // 여기서 막지 않는다 — 부팅은 되고 업로드도 성공한다. 다만 그 뒤로 이미지가 안 열리는데,
            // 저장까지 끝난 다음에 드러나는 종류라 배포 직후 로그에서 보이게 남긴다.
            log.warn("버킷 이름에 점이 있어 기본 공개 주소가 TLS 에서 막힙니다 — S3_PUBLIC_BASE_URL 을 채워 주세요");
        }
    }

    private static S3Presigner presigner(ThumbnailStorageProperties properties) {
        return S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .build();
    }

    @Override
    public UploadTicket presign(ThumbnailUpload upload) {
        if (presigner == null) {
            // 배포 설정이 빠진 상태다. 여기서 끊지 않으면 브라우저가 S3 에서 403 을 받고 나서야 알게 된다.
            throw CurationException.imageStorageUnavailable();
        }
        String objectKey = upload.objectKey(UUID.randomUUID());
        try {
            String uploadUrl = presigner.presignPutObject(PutObjectPresignRequest.builder()
                            .signatureDuration(SIGNATURE_TTL)
                            .putObjectRequest(PutObjectRequest.builder()
                                    .bucket(properties.bucket())
                                    .key(objectKey)
                                    .contentType(upload.contentType())
                                    .contentLength(upload.contentLength())
                                    .build())
                            .build())
                    .url()
                    .toString();
            // 오브젝트 키도 남기지 않는다. 키는 무작위라 그 자체가 썸네일 주소를 추측 못 하게 하는 장치인데,
            // 로그로 새면 그 성질이 로그 열람 범위만큼 사라진다. 무엇이 발급됐는지는 종류·크기로 충분하다.
            log.info("썸네일 업로드 주소 발급 type={} bytes={}", upload.contentType(), upload.contentLength());
            return UploadTicket.builder()
                    .uploadUrl(uploadUrl)
                    .publicUrl(publicUrl(objectKey))
                    .expiresIn(SIGNATURE_TTL)
                    .build();
        } catch (RuntimeException e) {
            log.warn("썸네일 업로드 주소를 발급하지 못했습니다 type={} cause={}", upload.contentType(), RootCause.of(e));
            throw CurationException.imageStorageUnavailable();
        }
    }

    private String publicUrl(String objectKey) {
        return properties.publicBaseUrlOrDefault() + "/" + objectKey;
    }

    /** 서명기는 내부에 HTTP 클라이언트를 들고 있다 — 컨텍스트가 내려갈 때 함께 닫는다. */
    @PreDestroy
    void close() {
        if (presigner != null) {
            presigner.close();
        }
    }
}
