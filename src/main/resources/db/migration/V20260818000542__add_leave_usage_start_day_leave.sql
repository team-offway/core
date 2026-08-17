-- 코스 차감 내역에 "첫날에 쓴 연차" 를 남긴다 (#138).
--
-- 왜 새 컬럼인가: half_day_start 는 BOOLEAN 이라 반반차를 표현할 수 없다. 값이 셋이 되면
-- 불리언 하나로는 담기지 않고, 두 개로 쪼개면 "반차이면서 반반차" 같은 있을 수 없는 조합이
-- 스키마에 남는다.
--
-- 왜 half_day_start 를 지금 지우지 않는가: 순서 의존 변경은 add → backfill → drop 3단계로
-- 나눠 배포한다(persistence-convention). 이 마이그레이션은 앞의 둘이다. 애플리케이션이
-- 새 컬럼만 쓰는 것이 배포로 확인된 뒤 별도 마이그레이션으로 지운다 — 지금 함께 지우면
-- 롤백한 이전 버전이 없는 컬럼을 읽는다.
ALTER TABLE leave_usage
    ADD COLUMN start_day_leave VARCHAR(20) NULL
        COMMENT '코스 차감 시 첫날에 쓴 연차(FULL_DAY·HALF_DAY·QUARTER_DAY). 수동 내역은 NULL';

-- 기존 코스 내역을 옮긴다. half_day_start 가 NULL 인 행(이 컬럼이 생기기 전 차감)은
-- 그때 동작이 "반차 아님" 이었으므로 FULL_DAY 다 — 그 시절 결과를 그대로 유지한다.
UPDATE leave_usage
   SET start_day_leave = CASE WHEN half_day_start = TRUE THEN 'HALF_DAY' ELSE 'FULL_DAY' END
 WHERE course_id IS NOT NULL;
