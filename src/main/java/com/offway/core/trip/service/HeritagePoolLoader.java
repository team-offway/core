package com.offway.core.trip.service;

import com.offway.core.trip.domain.HeritagePlace;
import com.offway.core.trip.infrastructure.localdata.HeritagePoolCsvReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 국가유산 풀을 부팅 시 적재한다(#160).
 *
 * <p>국가유산 지정은 연 단위로만 바뀌는 레퍼런스라 매 요청 외부에서 끌어올 이유가 없다. 파일을 통째로 DB 에
 * 옮겨두고, 이후 조회는 전부 로컬에서 끝낸다.
 *
 * <p><b>파일이 바뀌었을 때만 다시 채운다.</b> 판정은 파일 내용의 체크섬으로 한다 — 건수만 비교하면 갱신된 파일의
 * 건수가 우연히 같을 때 그대로 건너뛰어, 낡은 데이터가 조회용으로 남는다. 장소 풀(#144)과 같은 형식이다.
 *
 * <p>파일이 없어도 부팅을 막지 않는다(로컬 실행성 불변식). 적재가 안 되면 그 후보만 비고, 서버가 통째로 안 뜨는
 * 쪽이 훨씬 위험하다.
 */
@Slf4j
@Component
public class HeritagePoolLoader {

    private static final String CHECKSUM_ALGORITHM = "SHA-256";

    /** 로그에 남길 체크섬 앞자리 수 — 64자를 통째로 남기지 않아도 같은지 다른지는 읽힌다. */
    private static final int SHORT_HASH_LENGTH = 12;

    private final HeritagePoolPersistenceService persistenceService;
    private final HeritagePoolCsvReader csvReader;
    private final Resource poolResource;

    public HeritagePoolLoader(
            HeritagePoolPersistenceService persistenceService,
            HeritagePoolCsvReader csvReader,
            @Value("classpath:data/heritage-pool.csv.gz") Resource poolResource) {
        this.persistenceService = persistenceService;
        this.csvReader = csvReader;
        this.poolResource = poolResource;
    }

    /**
     * 기동이 끝난 뒤 적재한다. 마이그레이션(Flyway)이 테이블을 만든 뒤여야 하고, 적재가 늦어져도 기동 자체는
     * 막지 않아야 한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void load() {
        if (!poolResource.exists()) {
            log.warn("국가유산 풀 파일이 없어 적재를 건너뜁니다: {}", poolResource.getDescription());
            return;
        }

        long startedAt = System.nanoTime();
        try {
            String checksum = checksumOf();
            Optional<String> loaded = persistenceService.loadedChecksum();
            if (loaded.filter(checksum::equals).isPresent()) {
                log.info("국가유산 풀이 이미 같은 파일로 적재돼 있어 건너뜁니다. count={}", persistenceService.count());
                return;
            }
            loaded.ifPresent(previous -> log.info(
                    "국가유산 풀 파일이 바뀌어 다시 채웁니다. 이전={} 새것={}", shortHash(previous), shortHash(checksum)));

            List<HeritagePlace> places = readPlaces();
            if (places.isEmpty()) {
                // 빈 결과로 덮으면 있던 후보까지 사라진다. 이전 적재가 낫다.
                log.warn("국가유산 풀 파일에서 실을 것이 없습니다: {}", poolResource.getDescription());
                return;
            }

            int inserted = persistenceService.replaceAll(places, checksum);
            log.info("국가유산 풀 적재 완료. count={} checksum={} elapsed={}ms",
                    inserted, shortHash(checksum), elapsedMillis(startedAt));
        } catch (IOException | RuntimeException e) {
            // 적재 실패로 서버를 죽이지 않는다. 다만 조용히 넘기지도 않는다 — 이후 코스에서 볼거리가 얇아진다.
            // ApplicationReadyEvent 리스너에서 던진 예외는 SpringApplication.run 밖으로 전파되므로,
            // 좁게 잡으면 CSV 한 줄짜리 사고가 서버 전체를 못 뜨게 만든다(#144 에서 겪었다).
            //
            // 여기서 잡아도 부분 적재는 남지 않는다 — 트랜잭션이 영속화 빈에 있어 전량 롤백된다.
            log.error("국가유산 풀 적재에 실패했습니다. 국가유산 후보 없이 동작합니다.", e);
        }
    }

    private List<HeritagePlace> readPlaces() throws IOException {
        try (InputStream in = poolResource.getInputStream()) {
            return csvReader.read(in);
        }
    }

    /** 파일 바이트의 체크섬. 내용이 한 글자만 달라도 값이 달라진다. */
    private String checksumOf() throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(CHECKSUM_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(CHECKSUM_ALGORITHM + " 를 쓸 수 없습니다", e);
        }
        try (InputStream in = poolResource.getInputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String shortHash(String checksum) {
        return checksum.length() <= SHORT_HASH_LENGTH ? checksum : checksum.substring(0, SHORT_HASH_LENGTH);
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
