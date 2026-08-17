-- pickbit_payment_server 초기 스키마.
-- Hibernate 가 만든 스키마를 mysqldump 로 그대로 뜬 것이다. 손으로 쓰면 컬럼 순서나
-- 인덱스명이 어긋나 ddl-auto=validate 가 기동 시 실패한다.
-- 앞으로 엔티티를 바꾸면 반드시 V2__*.sql 을 함께 커밋할 것.
-- mysqldump 는 테이블을 알파벳순으로 내보내므로 외래키가 아직 없는 테이블을 참조할 수 있다.
-- (BATCH_JOB_EXECUTION 이 BATCH_JOB_INSTANCE 보다 먼저 나오는 식)
-- 세션 범위 설정이라 마이그레이션 밖에는 영향이 없다.
SET FOREIGN_KEY_CHECKS = 0;


CREATE TABLE `BATCH_JOB_EXECUTION` (
  `JOB_EXECUTION_ID` bigint NOT NULL,
  `VERSION` bigint DEFAULT NULL,
  `JOB_INSTANCE_ID` bigint NOT NULL,
  `CREATE_TIME` datetime(6) NOT NULL,
  `START_TIME` datetime(6) DEFAULT NULL,
  `END_TIME` datetime(6) DEFAULT NULL,
  `STATUS` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `EXIT_CODE` varchar(2500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `EXIT_MESSAGE` varchar(2500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LAST_UPDATED` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`JOB_EXECUTION_ID`),
  KEY `JOB_INST_EXEC_FK` (`JOB_INSTANCE_ID`),
  CONSTRAINT `JOB_INST_EXEC_FK` FOREIGN KEY (`JOB_INSTANCE_ID`) REFERENCES `BATCH_JOB_INSTANCE` (`JOB_INSTANCE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_JOB_EXECUTION_CONTEXT` (
  `JOB_EXECUTION_ID` bigint NOT NULL,
  `SHORT_CONTEXT` varchar(2500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `SERIALIZED_CONTEXT` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`JOB_EXECUTION_ID`),
  CONSTRAINT `JOB_EXEC_CTX_FK` FOREIGN KEY (`JOB_EXECUTION_ID`) REFERENCES `BATCH_JOB_EXECUTION` (`JOB_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_JOB_EXECUTION_PARAMS` (
  `JOB_EXECUTION_ID` bigint NOT NULL,
  `PARAMETER_NAME` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `PARAMETER_TYPE` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `PARAMETER_VALUE` varchar(2500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `IDENTIFYING` char(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  KEY `JOB_EXEC_PARAMS_FK` (`JOB_EXECUTION_ID`),
  CONSTRAINT `JOB_EXEC_PARAMS_FK` FOREIGN KEY (`JOB_EXECUTION_ID`) REFERENCES `BATCH_JOB_EXECUTION` (`JOB_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_JOB_EXECUTION_SEQ` (
  `ID` bigint NOT NULL,
  `UNIQUE_KEY` char(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY `UNIQUE_KEY_UN` (`UNIQUE_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_JOB_INSTANCE` (
  `JOB_INSTANCE_ID` bigint NOT NULL,
  `VERSION` bigint DEFAULT NULL,
  `JOB_NAME` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_KEY` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`JOB_INSTANCE_ID`),
  UNIQUE KEY `JOB_INST_UN` (`JOB_NAME`,`JOB_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_JOB_INSTANCE_SEQ` (
  `ID` bigint NOT NULL,
  `UNIQUE_KEY` char(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY `UNIQUE_KEY_UN` (`UNIQUE_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_STEP_EXECUTION` (
  `STEP_EXECUTION_ID` bigint NOT NULL,
  `VERSION` bigint NOT NULL,
  `STEP_NAME` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `JOB_EXECUTION_ID` bigint NOT NULL,
  `CREATE_TIME` datetime(6) NOT NULL,
  `START_TIME` datetime(6) DEFAULT NULL,
  `END_TIME` datetime(6) DEFAULT NULL,
  `STATUS` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `COMMIT_COUNT` bigint DEFAULT NULL,
  `READ_COUNT` bigint DEFAULT NULL,
  `FILTER_COUNT` bigint DEFAULT NULL,
  `WRITE_COUNT` bigint DEFAULT NULL,
  `READ_SKIP_COUNT` bigint DEFAULT NULL,
  `WRITE_SKIP_COUNT` bigint DEFAULT NULL,
  `PROCESS_SKIP_COUNT` bigint DEFAULT NULL,
  `ROLLBACK_COUNT` bigint DEFAULT NULL,
  `EXIT_CODE` varchar(2500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `EXIT_MESSAGE` varchar(2500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LAST_UPDATED` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`STEP_EXECUTION_ID`),
  KEY `JOB_EXEC_STEP_FK` (`JOB_EXECUTION_ID`),
  CONSTRAINT `JOB_EXEC_STEP_FK` FOREIGN KEY (`JOB_EXECUTION_ID`) REFERENCES `BATCH_JOB_EXECUTION` (`JOB_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_STEP_EXECUTION_CONTEXT` (
  `STEP_EXECUTION_ID` bigint NOT NULL,
  `SHORT_CONTEXT` varchar(2500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `SERIALIZED_CONTEXT` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`STEP_EXECUTION_ID`),
  CONSTRAINT `STEP_EXEC_CTX_FK` FOREIGN KEY (`STEP_EXECUTION_ID`) REFERENCES `BATCH_STEP_EXECUTION` (`STEP_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `BATCH_STEP_EXECUTION_SEQ` (
  `ID` bigint NOT NULL,
  `UNIQUE_KEY` char(1) COLLATE utf8mb4_unicode_ci NOT NULL,
  UNIQUE KEY `UNIQUE_KEY_UN` (`UNIQUE_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `inbox` (
  `attempt_count` int NOT NULL,
  `success` bit(1) NOT NULL,
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `event_version` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `processed_at` datetime(6) NOT NULL,
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `topic` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `success_event_id` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `message_body` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqsxt0bh3jl3b160x3d3mar163` (`success_event_id`),
  KEY `idx_inbox_retry` (`success`,`next_attempt_at`),
  KEY `idx_inbox_version` (`topic`,`aggregate_id`,`event_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `out_box_event` (
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `aggregate_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Aggregate ID (Partition Key)',
  `entity` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '엔티티 타입',
  `event_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '이벤트 ID',
  `event_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '이벤트 타입',
  `payload` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이벤트 페이로드 (JSON)',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='아웃박스 이벤트';
CREATE TABLE `payment` (
  `amount` decimal(19,2) NOT NULL COMMENT '결제 금액',
  `auction_id` bigint NOT NULL COMMENT '경매 ID',
  `buyer_user_id` bigint NOT NULL COMMENT '구매자 사용자 ID',
  `confirm_deadline_at` datetime(6) DEFAULT NULL COMMENT '자동 구매확정 데드라인 (결제완료 + 10d)',
  `confirmed_at` datetime(6) DEFAULT NULL COMMENT '구매확정 시각',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `paid_at` datetime(6) DEFAULT NULL COMMENT '결제(에스크로) 완료 시각',
  `payment_deadline_at` datetime(6) NOT NULL COMMENT '결제 데드라인 (낙찰 + 24h)',
  `product_id` bigint DEFAULT NULL COMMENT '상품 ID 스냅샷',
  `refunded_at` datetime(6) DEFAULT NULL COMMENT '환불 완료 시각',
  `released_at` datetime(6) DEFAULT NULL COMMENT '정산 완료 시각',
  `seller_user_id` bigint NOT NULL COMMENT '판매자 사용자 ID',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `version` bigint NOT NULL,
  `buyer_nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '구매자 닉네임 스냅샷',
  `seller_nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '판매자 닉네임 스냅샷',
  `pg_order_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PG 주문 번호 (멱등 키)',
  `product_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '상품명 스냅샷',
  `pg_payment_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'PG 결제 키 (PG사 발급)',
  `product_thumbnail_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '상품 썸네일 URL 스냅샷',
  `pg_provider` enum('TOSS_PAYMENTS') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PG 제공자',
  `status` enum('CANCELLED','DISPUTED','ESCROWED','FAILED','PG_PENDING','PURCHASE_CONFIRMED','REFUNDED','RELEASED','REQUESTED') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '결제 상태',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKkstxor5x59xhhfue4qhijln5l` (`pg_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `pg_webhook_log` (
  `processed` bit(1) NOT NULL,
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `received_at` datetime(6) NOT NULL,
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `event_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이벤트 종류',
  `pg_event_id` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PG가 발급한 이벤트 키 (멱등 키)',
  `pg_payment_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '관련 PG 결제 키',
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `provider` enum('TOSS_PAYMENTS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `raw_payload` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '원본 페이로드',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK5tff6l4hme72qfglo3998pvqi` (`pg_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `settlement` (
  `gross_amount` decimal(19,2) NOT NULL COMMENT '총 결제 금액',
  `net_seller_amount` decimal(19,2) NOT NULL COMMENT '판매자 정산액',
  `pg_fee_amount` decimal(19,2) NOT NULL COMMENT 'PG 수수료',
  `platform_fee_amount` decimal(19,2) NOT NULL COMMENT '플랫폼 수수료',
  `retry_count` int NOT NULL,
  `auction_id` bigint DEFAULT NULL COMMENT '경매 ID',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `payment_id` bigint NOT NULL COMMENT 'Payment ID',
  `product_id` bigint DEFAULT NULL COMMENT '상품 ID',
  `seller_user_id` bigint NOT NULL COMMENT '판매자 사용자 ID',
  `settled_at` datetime(6) DEFAULT NULL COMMENT '정산 완료 시각',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `product_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '상품명 스냅샷',
  `product_thumbnail_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '썸네일 URL 스냅샷',
  `failure_reason` text COLLATE utf8mb4_unicode_ci COMMENT '정산 실패 사유',
  `status` enum('COMPLETED','FAILED','PENDING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhcklg8m7jhw126s1pwnfysjp4` (`payment_id`),
  KEY `idx_settlement_seller_status` (`seller_user_id`,`status`),
  KEY `idx_settlement_status_retry` (`status`,`retry_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
