-- pickbit_user_server 초기 스키마.
-- Hibernate 가 만든 스키마를 mysqldump 로 그대로 뜬 것이다. 손으로 쓰면 컬럼 순서나
-- 인덱스명이 어긋나 ddl-auto=validate 가 기동 시 실패한다.
-- 앞으로 엔티티를 바꾸면 반드시 V2__*.sql 을 함께 커밋할 것.
-- mysqldump 는 테이블을 알파벳순으로 내보내므로 외래키가 아직 없는 테이블을 참조할 수 있다.
-- (BATCH_JOB_EXECUTION 이 BATCH_JOB_INSTANCE 보다 먼저 나오는 식)
-- 세션 범위 설정이라 마이그레이션 밖에는 영향이 없다.
SET FOREIGN_KEY_CHECKS = 0;


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
CREATE TABLE `user_penalty` (
  `score_after` int NOT NULL,
  `score_delta` int NOT NULL,
  `auction_id` bigint DEFAULT NULL,
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `payment_id` bigint DEFAULT NULL,
  `product_id` bigint DEFAULT NULL,
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `user_id` bigint NOT NULL,
  `source_event_id` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason` enum('PAYMENT_CANCELLED_BEFORE_PAYMENT','PAYMENT_FAILED_NO_PAYMENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKtg91ahgg9yj8b3d7iyfqmw7vg` (`source_event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `users` (
  `trust_score` int NOT NULL,
  `account_id` bigint NOT NULL,
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `nickname` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1yov8c5ew74vlt8qra6cewq99` (`account_id`),
  UNIQUE KEY `UK2ty1xmrrgtn89xt7kyxx6ta7h` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
