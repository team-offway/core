package com.offway.core.trip.service;

import com.offway.core.trip.domain.LicensedPlace;
import com.offway.core.trip.infrastructure.localdata.PlacePoolCsvReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * 장소 풀을 부팅 시 1회 적재한다(#144).
 *
 * <p>인허가 데이터는 분기 단위로만 바뀌는 레퍼런스라 매 요청 다시 읽을 이유가 없다. 파일을 통째로 DB 에 옮겨두고,
 * 이후 조회는 전부 로컬에서 끝낸다.
 *
 * <p><b>비어 있을 때만 채운다.</b> 재기동마다 다시 넣으면 부팅이 느려지고 중복이 쌓인다. 파일을 새로 받아 갱신하려면
 * 테이블을 비우고 재기동한다 — 부분 갱신은 지금 필요하지 않다(분기 1회).
 *
 * <p>파일이 없어도 부팅을 막지 않는다. 외부 키가 없어도 뜨는 것과 같은 이유다 — 적재가 안 되면 그 조회만 비고,
 * 서버가 통째로 안 뜨는 쪽이 훨씬 위험하다(로컬 실행성 불변식).
 */
@Slf4j
@Component
public class PlacePoolLoader {

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
        try (InputStream in = poolResource.getInputStream()) {
            List<LicensedPlace> places = csvReader.read(in);
            if (places.isEmpty()) {
                log.warn("장소 풀 파일에서 읽은 장소가 없습니다: {}", poolResource.getDescription());
                return;
            }

            // 건수까지 맞아야 "이미 적재됨" 이다. 개수만 0인지 보면, 절반만 실린 DB 를 정상으로 여겨
            // 영영 고치지 못한다 — 실제로 42곳만 실린 채 재배포해도 그대로였다.
            // 파일이 갱신돼 건수가 달라진 경우도 같은 경로로 자연히 다시 채워진다.
            long existing = persistenceService.count();
            if (existing == places.size()) {
                log.info("장소 풀이 이미 적재돼 있어 건너뜁니다. count={}", existing);
                return;
            }
            if (existing > 0) {
                log.warn("장소 풀이 파일과 어긋나 다시 채웁니다. DB={} 파일={}", existing, places.size());
            }
            int inserted = persistenceService.replaceAll(places);
            if (inserted != places.size()) {
                // 전량 아니면 실패다. 절반만 실린 채 "성공" 으로 넘어가면 목록이 200 을 주면서 비어 있다.
                log.error("장소 풀이 일부만 적재됐습니다. 읽음={} 적재={}", places.size(), inserted);
                return;
            }
            log.info("장소 풀 적재 완료. count={} elapsed={}ms", inserted, elapsedMillis(startedAt));
        } catch (IOException | RuntimeException e) {
            // 적재 실패로 서버를 죽이지 않는다. 다만 조용히 넘기지도 않는다 — 이후 코스에서 후보가 빈다.
            //
            // RuntimeException 까지 잡는 이유: 여기로 올라오는 실패는 대부분 IOException 이 아니다.
            // 파일이 깨지면 리더가 UncheckedIOException, 헤더가 어긋나면 IllegalStateException,
            // 적재가 유니크·패킷 상한에 걸리면 DataAccessException 이 온다. ApplicationReadyEvent
            // 리스너에서 던진 예외는 SpringApplication.run 밖으로 전파되므로, 좁게 잡으면
            // CSV 한 줄짜리 사고가 서버 전체를 못 뜨게 만든다.
            log.error("장소 풀 적재에 실패했습니다. 인허가 후보 없이 동작합니다.", e);
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
