-- 기간을 날짜 두 개로 다 못 적는 정책을 위한 보충 문구(#217).
--
-- 반값여행은 지자체별로 신청·여행 기간이 다르다. 그렇다고 period_start·period_end 를 NULL 로 두면 안 된다 —
-- Policy.isActiveOn 에서 NULL 은 "모른다" 가 아니라 "상시" 라, 사업이 끝나도 뱃지가 영영 남는다.
--
-- 그래서 날짜에는 사업 전체의 바깥 경계를 넣어 만료가 걸리게 하고, "지자체별로 다르다" 는 사실은
-- 이 문구로 따로 말한다. 사용자에게 그대로 보이는 값이라 apply_url 과 함께 읽히도록 짧게 쓴다.
ALTER TABLE policy
    ADD COLUMN period_note VARCHAR(200) NULL COMMENT '기간 보충 문구. 지자체별로 다른 경우 등';
