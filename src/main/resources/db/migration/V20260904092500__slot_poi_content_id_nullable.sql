-- 교통 거점 칸(도착·출발)은 장소 식별자가 없다(#415).
--
-- 대중교통 코스의 첫 칸·끝 칸에 역·터미널·항구가 들어간다. 이 칸은 장소 풀이 아니라 우리 DB 의
-- 교통 지점이라 TourAPI 콘텐츠 ID 가 없고, 장소 상세(GET /api/v1/pois/{id})로도 이어지지 않는다.
--
-- 가짜 id 를 채워 NOT NULL 을 지키는 쪽을 택하지 않았다. 접두어 없는 값은 PlaceOrigin 이 TourAPI 로
-- 읽어 실린 적 없는 출처를 응답에 적고(#399), 앱이 그 id 로 상세를 부르면 404 를 받는다.
--
-- 제약을 푸는 방향이라 기존 행은 그대로 유효하다. 무엇이 필수인지는 SlotKind.hasPlace() 가 소유한다.
ALTER TABLE slot
    MODIFY COLUMN poi_content_id VARCHAR(64) NULL;
