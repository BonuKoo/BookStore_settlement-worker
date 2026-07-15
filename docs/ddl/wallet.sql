-- M2 settlement-worker — 수동 적용 DDL
-- 대상 DB: core2_spa (core-spa와 공유하는 PC1 MySQL 인스턴스, ddl-auto=none)
--
-- core2_spa에는 과거 실험 흔적인 `wallets`/`wallet_transactions` 테이블이 이미
-- 존재했으나(원본 wallet 프로젝트의 @Version 낙관적 락 스키마 그대로, 0 rows —
-- 사용자 확인 후 DROP 승인받음), M2는 원자 UPDATE+UNIQUE 멱등 방식으로 재설계하므로
-- 아래로 교체한다.
--
-- 설계 메모:
--  - wallets.seller_id UNIQUE — 판매자당 지갑 하나. version 컬럼 없음(낙관적 락 불필요,
--    creditBalance의 INSERT..ON DUPLICATE KEY UPDATE 한 문장이 동시성을 책임진다).
--  - wallet_transactions: UNIQUE(order_id, seller_id) — "이 주문의 이 판매자 몫은
--    한 번만 정산된다"를 DB가 보장. wallet_id FK를 두지 않는다(신규 판매자의 첫
--    정산은 wallets 행이 아직 없을 수 있는데, 멱등 가드가 잔액 반영보다 먼저
--    성립해야 하므로 wallets 존재 여부에 의존하지 않게 분리).

DROP TABLE IF EXISTS wallet_transactions;
DROP TABLE IF EXISTS wallets;

CREATE TABLE wallets (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    seller_id  BIGINT        NOT NULL,
    balance    DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wallets_seller_id UNIQUE (seller_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE wallet_transactions (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    order_id   VARCHAR(255)  NOT NULL,
    seller_id  BIGINT        NOT NULL,
    amount     DECIMAL(19,4) NOT NULL,
    type       VARCHAR(20)   NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_wallet_tx_order_seller UNIQUE (order_id, seller_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
