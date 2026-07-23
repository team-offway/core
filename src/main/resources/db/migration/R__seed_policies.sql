-- 정책 seed (수동 적재 · repeatable). 실데이터가 확보된 정책만 넣는다.
--   verified=TRUE : 뱃지·상세·기간·대상까지 실데이터 확보
--   verified=FALSE: 존재는 확인됐으나 상세·기간 미확정(데모 노출은 서비스에서 결정)
-- 프로그램별 참여 지역이 확정되면 region_tag 에 해당 태그를 시딩하고 PolicyType.targetTag 를 좁힌다.
-- repeatable 이므로 idempotent 하게 전량 삭제 후 재적재한다(정책은 런타임 write 없는 레퍼런스 데이터).
DELETE FROM policy;

INSERT INTO policy (id, type, name, benefit_detail, target_audience, period_start, period_end, apply_url, verified) VALUES
(1, 'REGIONAL_VOUCHER', '지역사랑 휴가지원(반값여행)',
 '여행경비의 50%를 지역화폐로 환급 · 1인 최대 10만원(청년 70%)',
 '전 국민(거주지와 다른 지역 여행 시)', '2026-04-01', '2026-08-31', NULL, TRUE),
(2, 'DIGITAL_TOURIST_CARD', '디지털관광주민증',
 '인구감소지역 가맹점·시설 할인', '전 국민', NULL, NULL, NULL, FALSE);
