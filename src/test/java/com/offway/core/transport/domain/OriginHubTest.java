package com.offway.core.transport.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.offway.core.common.geo.Coordinate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 출발지 제안 한 줄의 표시 이름·검색 매칭·중복 접기(#590). */
class OriginHubTest {

    private static final Coordinate SEOUL_STATION = new Coordinate(37.553261, 126.969133);
    private static final Coordinate DONG_SEOUL = new Coordinate(37.53460761, 127.09418832);

    private static OriginHub train(String rawName, String code, OriginSido sido, Coordinate at) {
        return OriginHub.of(OriginHubType.TRAIN_STATION, code, rawName, sido, at);
    }

    private static OriginHub bus(String rawName, String code, OriginSido sido, Coordinate at) {
        return OriginHub.of(OriginHubType.BUS_TERMINAL, code, rawName, sido, at);
    }

    @ParameterizedTest
    @CsvSource({
        // 원본에 종류가 없으면 접미를 붙인다
        "서울,서울역",
        "청량리,청량리역",
        "용산,용산역",
        // 이미 '역' 으로 끝나면 그대로
        "천안아산역,천안아산역",
    })
    void 기차역_표시_이름은_접미_규칙이_만든다(String rawName, String expected) {
        assertEquals(expected, train(rawName, "C1", OriginSido.SEOUL, SEOUL_STATION).displayName());
    }

    @ParameterizedTest
    @CsvSource({
        "동서울,동서울터미널",
        "서울남부,서울남부터미널",
        "대전복합,대전복합터미널",
        // 원본이 이미 '터미널' 로 끝나면 그대로 — 붙이면 '수원터미널터미널' 이 된다
        "수원터미널,수원터미널",
        // '역' 으로 끝나는 버스 정류 지점에도 붙이지 않는다 — '천안아산역터미널' 은 틀린 말이다
        "천안아산역,천안아산역",
    })
    void 버스_표시_이름도_접미_규칙이_만든다(String rawName, String expected) {
        assertEquals(expected, bus(rawName, "C1", OriginSido.SEOUL, SEOUL_STATION).displayName());
    }

    @Test
    void 별칭이_붙은_허브는_규칙_대신_별칭의_표시_이름을_쓴다() {
        OriginHub hub = bus("서울경부", "NAEK010", OriginSido.SEOUL, SEOUL_STATION);

        assertEquals("고속버스터미널(경부·영동)", hub.displayName());
        assertTrue(hub.curated());
    }

    @Test
    void 규칙으로만_만든_허브는_별칭이_없다() {
        OriginHub hub = train("청량리", "NAT130126", OriginSido.SEOUL, SEOUL_STATION);

        assertFalse(hub.curated());
        assertTrue(hub.aliases().isEmpty());
    }

    @Test
    void 표시_이름이_검색어로_시작하면_가장_높은_등급이다() {
        OriginHub hub = train("서울", "NAT010000", OriginSido.SEOUL, SEOUL_STATION);

        assertEquals(Optional.of(OriginHub.Match.NAME_PREFIX), hub.match(SearchableName.of("서울역")));
        assertEquals(Optional.of(OriginHub.Match.NAME_PREFIX), hub.match(SearchableName.of("서울")));
    }

    @Test
    void 이름_안쪽에_들면_그_아래_등급이다() {
        // "서울" 을 친 사람의 첫 줄은 서울역이어야 한다 — 동서울터미널은 그 뒤다.
        OriginHub hub = bus("동서울", "NAEK030", OriginSido.SEOUL, DONG_SEOUL);

        assertEquals(Optional.of(OriginHub.Match.NAME), hub.match(SearchableName.of("서울")));
        assertTrue(OriginHub.Match.NAME_PREFIX.compareTo(OriginHub.Match.NAME) < 0);
    }

    @Test
    void 이름에_그_말이_없어도_지역으로_걸린다() {
        // 서울의 주요 역 대부분이 이름에 '서울' 을 담지 않는다 — 이 API 가 있는 이유다.
        OriginHub hub = train("청량리", "NAT130126", OriginSido.SEOUL, SEOUL_STATION);

        assertEquals(Optional.of(OriginHub.Match.AREA), hub.match(SearchableName.of("서울")));
    }

    @Test
    void 별칭_허브가_이름_시작_허브보다_위로_가지_않는다() {
        // 별칭이 붙었다고 위로 올리면, 별칭을 더할 때마다 "서울" 검색의 첫 줄이 뒤집힌다.
        OriginHub station = train("서울", "NAT010000", OriginSido.SEOUL, SEOUL_STATION);
        OriginHub curated = bus("서울경부", "NAEK010", OriginSido.SEOUL, SEOUL_STATION);

        assertEquals(Optional.of(OriginHub.Match.NAME_PREFIX), station.match(SearchableName.of("서울")));
        assertEquals(Optional.of(OriginHub.Match.NAME), curated.match(SearchableName.of("서울")));
    }

    @Test
    void 별칭으로도_걸린다() {
        OriginHub hub = bus("서울경부", "NAEK010", OriginSido.SEOUL, SEOUL_STATION);

        // "고속버스터미널" 은 표시 이름(고속버스터미널(경부·영동))의 시작이라 더 높은 등급이다.
        assertEquals(Optional.of(OriginHub.Match.NAME_PREFIX), hub.match(SearchableName.of("고속버스터미널")));
        // "강남터미널" 은 표시 이름에 없고 별칭에만 있다.
        assertEquals(Optional.of(OriginHub.Match.NAME), hub.match(SearchableName.of("강남터미널")));
        // 원본 이름으로도 걸린다 — 업계 표기를 아는 사람이 칠 수 있다.
        assertEquals(Optional.of(OriginHub.Match.NAME), hub.match(SearchableName.of("서울경부")));
    }

    @Test
    void 아무것도_안_걸리면_비어_있다() {
        OriginHub hub = train("청량리", "NAT130126", OriginSido.SEOUL, SEOUL_STATION);

        assertTrue(hub.match(SearchableName.of("부산")).isEmpty());
    }

    @Test
    void 같은_좌표_같은_종류면_중복_키가_같다() {
        // TAGO 가 같은 터미널에 노선별 코드를 따로 준다 — 동서울이 5건이다.
        OriginHub a = bus("동서울", "NAEK030", OriginSido.SEOUL, DONG_SEOUL);
        OriginHub b = bus("동서울", "NAI0511601", OriginSido.SEOUL, DONG_SEOUL);

        assertEquals(a.dedupeKey(), b.dedupeKey());
    }

    @Test
    void 종류가_다르면_같은_좌표라도_접지_않는다() {
        // 역 앞 정류소가 같은 좌표를 가질 수 있는데, 다른 수단이라 접으면 안 된다.
        OriginHub station = train("수원", "NAT010415", OriginSido.GYEONGGI, SEOUL_STATION);
        OriginHub terminal = bus("수원", "NAEK999", OriginSido.GYEONGGI, SEOUL_STATION);

        assertFalse(station.dedupeKey().equals(terminal.dedupeKey()));
    }

    @Test
    void 접을_때_별칭이_붙은_쪽이_대표로_남는다() {
        // 서울경부와 서울고속버스터미널(경부)이 같은 좌표다 — 별칭이 붙은 쪽이 남아야
        // 화면에 '고속버스터미널(경부·영동)' 로 보인다.
        OriginHub curated = bus("서울경부", "NAEK010", OriginSido.SEOUL, SEOUL_STATION);
        OriginHub plain = bus("서울고속버스터미널(경부)", "NAI0654501", OriginSido.SEOUL, SEOUL_STATION);

        assertEquals(curated, curated.preferOver(plain));
        assertEquals(curated, plain.preferOver(curated));
    }

    @Test
    void 둘_다_별칭이_없으면_짧은_이름이_남는다() {
        OriginHub shortName = bus("동서울", "NAEK030", OriginSido.SEOUL, DONG_SEOUL);
        OriginHub longName = bus("동서울터미널(직통)", "NAEK031", OriginSido.SEOUL, DONG_SEOUL);

        assertEquals(shortName, shortName.preferOver(longName));
        assertEquals(shortName, longName.preferOver(shortName));
    }

    @Test
    void 이름_길이가_같으면_코드_순으로_정해_결과가_흔들리지_않는다() {
        OriginHub first = bus("동서울", "NAEK030", OriginSido.SEOUL, DONG_SEOUL);
        OriginHub second = bus("동서울", "NAEK031", OriginSido.SEOUL, DONG_SEOUL);

        assertEquals(first, first.preferOver(second));
        assertEquals(first, second.preferOver(first));
    }
}
