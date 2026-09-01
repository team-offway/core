-- 정책에 감사 흔적을 붙인다 (#344).
--
-- ## 왜 지금인가
--
-- 지금까지 정책을 고치는 유일한 길은 seed SQL 이었고, **git blame 이 감사 흔적이었다** — 누가 언제 왜
-- 고쳤는지가 커밋에 남았다. 백오피스로 옮기면 그 기록이 통째로 사라진다.
--
-- 배포 없이 값을 고칠 수 있게 되는 순간 **누가 언제 바꿨는지가 유일한 추적 수단**이 된다. curated_link 가
-- 같은 이유로 이 셋을 들고 있고(V20260829151940), 같은 모양으로 맞춘다.
--
-- ## 시각은 DB 가 채운다
--
-- created_at·updated_at 은 DEFAULT 와 ON UPDATE 로 DB 가 관리한다. 엔티티가 들지 않으므로 코드가
-- 빠뜨릴 수 없고, seed SQL 로 들어온 행도 값이 채워진다.
--
-- updated_by 만 애플리케이션이 채운다 — 사람 이름은 DB 가 알 수 없다. **기존 세 행은 NULL 로 남는다.**
-- 그건 "사람이 손댄 적이 없다" 는 뜻이고, 그것도 정보다.

ALTER TABLE policy
    ADD COLUMN created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '등록 시각',
    ADD COLUMN updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '마지막 수정 시각',
    ADD COLUMN updated_by VARCHAR(100) NULL COMMENT '마지막으로 고친 어드민. seed 로 들어온 행은 NULL';
