package com.offway.core.notification.infrastructure.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FCM 초기화(#270).
 *
 * <p><b>키가 없으면 {@code null} 을 돌려주고 부팅을 막지 않는다.</b> 이 프로젝트의 불변식은 "designated
 * 브랜치는 local 프로파일에서 시크릿 없이 부팅 가능" 이다(CLAUDE.md). 키를 부팅 조건으로 만들면 로컬
 * 개발과 CI 스모크가 전부 막힌다. 발송이 비활성이라는 사실은 {@link PushResult#DISABLED} 로 드러난다.
 *
 * <p><b>키를 받는 길을 둘 둔다.</b> 운영은 환경변수로 넘기는 편이 안전하고(파일을 서버에 두지 않는다),
 * 로컬은 파일 경로가 편하다. 둘 다 있으면 환경변수가 이긴다 — 배포 환경의 값이 개발자 파일에 가려지면
 * 원인을 찾기 어렵다.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    /** 이 앱의 FirebaseApp 이름. 기본 인스턴스를 쓰면 테스트가 서로의 초기화를 밟는다. */
    private static final String APP_NAME = "offway";

    /** 서비스 계정 JSON 을 통째로 base64 로 넣는다 — 환경변수에 개행이 섞이지 않게. */
    @Value("${offway.fcm.credentials-base64:}")
    private String credentialsBase64;

    /** 서비스 계정 JSON 파일 경로. 로컬에서 쓴다. */
    @Value("${offway.fcm.credentials-path:}")
    private String credentialsPath;

    /**
     * @return 키가 없거나 읽을 수 없으면 {@code null} — 그때 발송은 비활성이다
     */
    @Bean
    public FirebaseMessaging firebaseMessaging() {
        try (InputStream credentials = openCredentials()) {
            if (credentials == null) {
                log.warn("FCM 서비스 계정 키가 없습니다 — 푸시 발송 없이 뜹니다"
                        + " (offway.fcm.credentials-base64 또는 offway.fcm.credentials-path)");
                return null;
            }
            return FirebaseMessaging.getInstance(firebaseApp(credentials));
        } catch (IOException | RuntimeException e) {
            // 키가 깨졌다고 서버를 못 뜨게 하지 않는다. 발송만 죽고 나머지는 정상이어야 한다.
            log.error("FCM 초기화에 실패해 푸시 발송 없이 뜹니다", e);
            return null;
        }
    }

    /** 이미 초기화된 앱이 있으면 그것을 쓴다 — 컨텍스트가 여러 번 뜨는 테스트에서 중복 초기화로 죽는다. */
    private FirebaseApp firebaseApp(InputStream credentials) throws IOException {
        return FirebaseApp.getApps().stream()
                .filter(app -> APP_NAME.equals(app.getName()))
                .findFirst()
                .orElseGet(() -> FirebaseApp.initializeApp(options(credentials), APP_NAME));
    }

    private FirebaseOptions options(InputStream credentials) {
        try {
            return FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentials))
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("FCM 서비스 계정 키를 읽지 못했습니다", e);
        }
    }

    private InputStream openCredentials() throws IOException {
        if (!credentialsBase64.isBlank()) {
            return new ByteArrayInputStream(Base64.getDecoder().decode(credentialsBase64.trim()));
        }
        if (!credentialsPath.isBlank()) {
            Path path = Path.of(credentialsPath);
            if (!Files.isReadable(path)) {
                log.warn("FCM 키 파일을 읽을 수 없습니다 — 경로를 확인하세요");
                return null;
            }
            return new ByteArrayInputStream(Files.readString(path, StandardCharsets.UTF_8)
                    .getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }
}
