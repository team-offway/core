package com.offway.core.trip.service;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.infrastructure.localdata.PlacePoolCsvReader;
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
 * 장소 풀을 부팅 시 적재한다(#144).
 *
 * <p>인허가 데이터는 분기 단위로만 바뀌는 레퍼런스라 매 요청 다시 읽을 이유가 없다. 파일을 통째로 DB 에 옮겨두고,
 * 이후 조회는 전부 로컬에서 끝낸다.
 *
 * <p><b>파일이 바뀌었을 때만 다시 채운다.</b> 판정은 파일 내용의 체크섬으로 한다 — 건수만 비교하면 갱신된 파일의
 * 건수가 우연히 같을 때 그대로 건너뛰어, 낡은 장소 정보가 조회용으로 남는다.
 *
 * <p><b>트랜잭션 경계는 {@link PlacePoolPersistenceService} 에 있다.</b> 여기서 예외를 잡으므로, 경계가
 * 이 안에 있으면 잡는 순간 그때까지 넣은 것이 커밋된다 — 실제로 그렇게 89곳 중 42곳만 실린 채 배포됐다.
 *
 * <p>파일이 없어도 부팅을 막지 않는다. 외부 키가 없어도 뜨는 것과 같은 이유다 — 적재가 안 되면 그 조회만 비고,
 * 서버가 통째로 안 뜨는 쪽이 훨씬 위험하다(로컬 실행성 불변식).
 */
@Slf4j
@Component
public class PlacePoolLoader {

    private static final String CHECKSUM_ALGORITHM = "SHA-256";

    /** 로그에 남길 체크섬 앞자리 수 — 64자를 통째로 남기지 않아도 같은지 다른지는 읽힌다. */
    private static final int SHORT_HASH_LENGTH = 12;

    private final PlacePoolPersistenceService persistenceService;
    private final PlacePoolCsvReader csvReader;
    private final Resource poolResource;

    public PlacePoolLoader(
            PlacePoolPersistenceService persistenceService,
            PlacePoolCsvReader csvReader,
            @Value("classpath:data/place-pool.csv.gz") Resource poolResource) {
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
            log.warn("장소 풀 파일이 없어 적재를 건너뜁니다: {}", poolResource.getDescription());
            return;
        }

        long startedAt = System.nanoTime();
        try {
            String checksum = checksumOf();
            Optional<String> loaded = persistenceService.loadedChecksum();
            if (loaded.filter(checksum::equals).isPresent()) {
                log.debug("장소 풀이 이미 같은 파일로 적재돼 있어 건너뜁니다. count={}", persistenceService.count());
                return;
            }
            loaded.ifPresent(previous -> log.debug(
                    "장소 풀 파일이 바뀌어 다시 채웁니다. 이전={} 새것={}", shortHash(previous), shortHash(checksum)));

            List<LicensedPlace> places = readPlaces();
            if (places.isEmpty()) {
                log.warn("장소 풀 파일에서 읽은 장소가 없습니다: {}", poolResource.getDescription());
                return;
            }

            int inserted = persistenceService.replaceAll(places, checksum);
            log.debug("장소 풀 적재 완료. count={} checksum={} elapsed={}ms",
                    inserted, shortHash(checksum), elapsedMillis(startedAt));
        } catch (IOException | RuntimeException e) {
            // 적재 실패로 서버를 죽이지 않는다. 다만 조용히 넘기지도 않는다 — 이후 코스에서 후보가 빈다.
            //
            // RuntimeException 까지 잡는 이유: 여기로 올라오는 실패는 대부분 IOException 이 아니다.
            // 파일이 깨지면 리더가 UncheckedIOException, 헤더가 어긋나면 IllegalStateException,
            // 적재가 유니크·패킷 상한에 걸리면 DataAccessException 이 온다. ApplicationReadyEvent
            // 리스너에서 던진 예외는 SpringApplication.run 밖으로 전파되므로, 좁게 잡으면
            // CSV 한 줄짜리 사고가 서버 전체를 못 뜨게 만든다.
            //
            // 여기서 잡아도 부분 적재는 남지 않는다 — 트랜잭션이 영속화 빈에 있어 전량 롤백된다.
            log.error("장소 풀 적재에 실패했습니다. 인허가 후보 없이 동작합니다.", e);
        }
    }

    private List<LicensedPlace> readPlaces() throws IOException {
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
