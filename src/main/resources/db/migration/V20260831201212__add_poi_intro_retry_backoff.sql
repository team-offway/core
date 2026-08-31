-- 빈 응답 재시도에 지수 백오프를 붙인다 (#368).
--
-- ## 무엇이 문제였나
--
-- 원본에 운영시간이 없는 장소는 받아도 모든 칸이 null 이고, 배치가 그것을 7일마다 **영원히** 다시 물었다.
-- 그런 장소가 N건이면 매일 N/7 건이 나가고 줄지 않는다 — 운영 알림에 매일 286건이 같은 모양으로 찍혔다.
-- 새로 채우는 게 아니라 같은 장소를 7일 주기로 돌려가며 다시 묻는 제자리걸음이었다.
--
-- ## 왜 "몇 번 비면 그만" 이 아닌가
--
-- 빈 값을 영구 저장으로 굳히면 원본이 나중에 채워져도 우리가 영영 모른다. 그리고 **poi_intro 를 지우는
-- 코드가 어디에도 없어서** 한 번 포기하면 되살릴 길이 없다. 그래서 포기하지 않고 **간격만 늘린다.**
--
-- ## 두 칸을 두는 이유
--
-- next_retry_at 은 empty_attempts 에서 도출되는 값이라 중복이다. 그런데도 저장하는 이유는 **일감 쿼리가
-- 그대로 쓰기 때문**이다 — 계산식(`fetched_at < NOW() - INTERVAL 7*POW(2,n) DAY`)으로 두면 인덱스를 못 타고
-- 매 회차 전체를 훑는다. empty_attempts 는 사람이 상태를 읽는 값이다("이 장소는 다섯 번 물어도 비어 있다").

ALTER TABLE poi_intro
    ADD COLUMN empty_attempts INT NOT NULL DEFAULT 0
        COMMENT '연속으로 빈 응답을 받은 횟수. 값이 채워지면 0 으로 돌아간다',
    ADD COLUMN next_retry_at DATETIME(6) NULL
        COMMENT '다음에 다시 물어볼 시각. NULL 이면 다시 물을 일이 없다(값이 채워졌다)';

-- 이미 받아 뒀는데 모든 칸이 비어 있는 행 = 지금까지 7일마다 다시 묻던 대상이다.
-- 몇 번 물었는지는 이력이 없어 모른다. 1 로 시작해 지금 동작을 그대로 이어받고, 다음 회차부터 벌어진다.
UPDATE poi_intro
   SET empty_attempts = 1,
       next_retry_at = DATE_ADD(fetched_at, INTERVAL 7 DAY)
 WHERE use_time IS NULL
   AND rest_date IS NULL
   AND parking IS NULL
   AND fee IS NULL
   AND signature_menu IS NULL
   AND menus IS NULL
   AND check_in IS NULL
   AND check_out IS NULL
   AND room_count IS NULL
   AND reservation IS NULL
   AND experience_guide IS NULL;

-- 일감 쿼리가 "때가 된 것" 만 고른다. NULL 인 행(값이 채워진 것)은 인덱스에서 빠져 스캔 대상이 아니다.
CREATE INDEX idx_poi_intro_next_retry ON poi_intro (next_retry_at);
