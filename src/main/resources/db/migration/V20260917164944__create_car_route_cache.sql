-- 자차 경로 캐시 둘(#584) — 구간 소요시간과 경유지 최적 순서.
--
-- **왜 필요한가.** 대중교통은 구간 소요시간을 transit_leg_duration 에 남겨 두고 쓴다(#107 · #469).
-- 자차만 캐시가 통째로 없어, 같은 코스를 다시 만들 때마다 TMAP 을 다시 불렀다. 그리고 그중
-- 경유지 최적화는 **일일 한도가 50** 으로 우리가 가진 것 중 가장 빡빡하다 — 2박3일 코스를 17번
-- 만들면 마른다. 마르면 직선거리로 폴백하는데 응답은 200 이라 사용자는 값이 틀린 줄 모른다.
--
-- **왜 잘 듣나.** 코스 생성에 Random 이 없어 결정적이다. 같은 지역·같은 옵션이면 넘어가는 좌표가
-- 글자 하나까지 같다. 그리고 좌표는 우리 장소 풀에서 오므로 **키 공간이 유한하다.**
--
-- **인메모리로는 안 된다.** 배포마다 비워지는데 하루에 여러 번 배포한다 — 한도 50 짜리를 그렇게
-- 두면 배포 직후 17번이면 또 마른다. 그게 상시 상태가 된다.
--
-- 좌표는 DECIMAL(10,7) 이다. CoordinateKey.SCALE 과 같은 자릿수이고 unroutable_probe 도 같다 —
-- 한쪽만 바꾸면 저장값과 조회 키가 어긋나 캐시가 조용히 아무것도 못 찾는다.

-- 구간 소요시간 — TMAP 경로(일일 한도 1,000).
--
-- **폴백은 저장하지 않는다.** 직선거리 근사는 한도가 말랐거나 좌표가 도로에 안 붙을 때 나오는 값이라,
-- 그걸 캐시하면 하루 한도가 마른 것이 재측정 주기 내내 굳는다. 저장하는 것은 TMAP 이 실제로 답한 것뿐이다.
CREATE TABLE car_leg_duration (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    from_lat    DECIMAL(10,7) NOT NULL,
    from_lng    DECIMAL(10,7) NOT NULL,
    to_lat      DECIMAL(10,7) NOT NULL,
    to_lng      DECIMAL(10,7) NOT NULL,
    -- 실측 소요시간(분). TMAP 이 답한 값만 들어온다.
    minutes     INT           NOT NULL,
    -- 잰 시각. 재측정 주기가 지나면 다시 잰다 — 도로는 느리게 변하지만 안 변하지는 않는다.
    measured_at DATETIME      NOT NULL,
    PRIMARY KEY (id),
    -- 방향이 있다. A→B 와 B→A 는 일방통행·고가 때문에 다를 수 있어 한 행으로 합치지 않는다.
    CONSTRAINT uk_car_leg UNIQUE (from_lat, from_lng, to_lat, to_lng)
);

-- 경유지 최적 순서 — TMAP 경유지최적화(**일일 한도 50**).
--
-- **키가 순서를 보존한 좌표 목록이다.** optimizeCarOrder 는 첫 점을 출발, 마지막 점을 도착으로
-- 고정하고 가운데만 최적화한다 — 같은 집합이라도 첫·끝이 다르면 결과가 다르다.
--
-- 점 개수가 3~12 로 고정돼 있어(MIN/MAX_OPTIMIZE_POINTS) 키 문자열이 280자를 안 넘는다.
-- ascii 로 두면 VARCHAR(512) 유니크 인덱스가 512바이트라 InnoDB 상한(3072) 안에 넉넉히 든다 —
-- 해시를 쓸 이유가 없고, 저장된 값을 사람이 읽을 수 있다.
CREATE TABLE car_route_order (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    -- "lat,lng;lat,lng;..." — 넘긴 순서 그대로.
    points      VARCHAR(512)  CHARACTER SET ascii NOT NULL,
    -- 최적 순서를 원본 인덱스로. "0,3,1,2" 처럼 쉼표로 잇는다.
    ordinals    VARCHAR(64)   CHARACTER SET ascii NOT NULL,
    measured_at DATETIME      NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_car_route_order_points UNIQUE (points)
);
