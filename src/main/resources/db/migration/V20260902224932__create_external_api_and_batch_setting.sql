-- 외부 연동을 배포 없이 조절한다(#403).
--
-- 지금은 캐시를 끄거나 배치를 멈추려면 코드를 고쳐 배포해야 한다. 그런데 이 판단은 대개
-- "오늘 한도가 마를 것 같다" 처럼 그 자리에서 내려야 하는 것이라, 배포를 기다리면 이미 늦다.
--
-- 값이 없으면 기본값이다 — 행을 만들지 않는 것이 곧 "건드리지 않았다" 라, 지금 동작이 그대로 유지된다.
-- FK 는 두지 않는다(프로젝트 규약). api·name 은 코드의 enum·상수와 짝이고 참조 무결성은 서비스가 본다.

CREATE TABLE external_api_setting (
    api           VARCHAR(40)  NOT NULL,
    -- 인메모리 캐시를 쓸지. 끄면 매번 실호출한다 — 한도를 태우는 대신 항상 최신을 본다.
    cache_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    -- 배치가 하루에 쓸 수 있는 상한. NULL 이면 무제한(지금 동작).
    -- 배치가 한도를 다 태우면 그날 사용자 요청이 먼저 죽는다 — 그걸 막으려는 값이다.
    batch_limit   INT          NULL,
    updated_at    DATETIME     NOT NULL,
    updated_by    VARCHAR(100) NULL,
    PRIMARY KEY (api)
);

-- 배치는 API 단위가 아니라 이름 단위다 — 한 API 를 여러 배치가 나눠 쓴다.
--
-- batch_run 에 컬럼을 더하지 않은 이유: 그 표는 "언제 돌았나" 라는 사실을 담고, 이 표는 "돌아도
-- 되나" 라는 의사를 담는다. 섞으면 한 번도 안 돈 배치를 미리 꺼 둘 수 없다(그 표에는 행이 없다).
CREATE TABLE batch_setting (
    name       VARCHAR(100) NOT NULL,
    enabled    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at DATETIME     NOT NULL,
    updated_by VARCHAR(100) NULL,
    PRIMARY KEY (name)
);
