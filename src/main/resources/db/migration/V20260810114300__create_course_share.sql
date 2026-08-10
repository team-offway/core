-- 코스 공유 링크(#143) — 추측 불가능한 토큰으로 인증 없이 코스를 읽게 한다.
--
-- **토큰을 course 컬럼이 아니라 별도 테이블로 둔다.** 코스는 hard delete 라, 토큰이 코스에 붙어 있으면
-- 코스가 지워질 때 함께 사라져 "없는 링크" 와 "삭제된 코스" 를 구분할 수 없다. 공유 행을 남겨두면
-- 그 행 자체가 묘비가 되어 추가 컬럼 없이 둘이 갈린다(410 Gone).
--
-- course_id 에 FK 를 걸지 않는다(영속성 규약). 애초에 코스가 지워져도 이 행은 남아야 하므로
-- 참조 무결성이 목적에 어긋난다.
CREATE TABLE course_share (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    -- URL-safe base64 22자(128비트). 순번을 노출하면 숫자를 바꿔가며 남의 코스를 훑을 수 있다.
    share_token  VARCHAR(32)  NOT NULL,
    course_id    BIGINT       NOT NULL,
    created_at   DATETIME     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_course_share_token UNIQUE (share_token),
    -- 코스 하나에 링크 하나. 공유 버튼을 여러 번 눌러도 같은 링크가 나가야 카톡에 뿌린 링크가 안 죽는다.
    CONSTRAINT uk_course_share_course UNIQUE (course_id)
);
