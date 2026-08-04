-- 적재된 장소 풀이 어느 파일에서 왔는지 기록한다(#144).
--
-- 재적재 여부를 건수로만 판정하면, 파일이 갱신됐는데 건수가 우연히 같은 경우 그대로 건너뛴다.
-- 잘못된 장소 정보가 조회용으로 남는데 아무도 모른다. 파일 내용의 해시를 함께 저장해
-- "같은 파일인가" 를 직접 묻는다.
--
-- 행은 항상 하나다(id=1). 적재 대상 파일이 하나뿐이라 여러 행이 생길 이유가 없고,
-- 고정 PK 로 두면 UPSERT 가 단순해진다.

CREATE TABLE place_pool_source (
    id          INT          NOT NULL,
    -- 파일 바이트의 SHA-256(hex 64자). 건수가 같아도 내용이 다르면 여기서 갈린다.
    checksum    CHAR(64)     NOT NULL,
    place_count INT          NOT NULL,
    loaded_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);
