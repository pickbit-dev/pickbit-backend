-- pickbit_product_server 초기 스키마.
-- Hibernate 가 만든 스키마를 mysqldump 로 그대로 뜬 것이다. 손으로 쓰면 컬럼 순서나
-- 인덱스명이 어긋나 ddl-auto=validate 가 기동 시 실패한다.
-- 앞으로 엔티티를 바꾸면 반드시 V2__*.sql 을 함께 커밋할 것.
-- mysqldump 는 테이블을 알파벳순으로 내보내므로 외래키가 아직 없는 테이블을 참조할 수 있다.
-- (BATCH_JOB_EXECUTION 이 BATCH_JOB_INSTANCE 보다 먼저 나오는 식)
-- 세션 범위 설정이라 마이그레이션 밖에는 영향이 없다.
SET FOREIGN_KEY_CHECKS = 0;


CREATE TABLE `category` (
  `active` bit(1) NOT NULL COMMENT '카테고리 활성화 여부',
  `sort_order` int NOT NULL COMMENT '카테고리 정렬 순서',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '카테고리명',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '카테고리 설명',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK46ccwnsi9409t36lurvtyljak` (`name`)
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
CREATE TABLE `product` (
  `starting_price` decimal(19,2) NOT NULL COMMENT '상품 시작가',
  `category_id` bigint DEFAULT NULL COMMENT '카테고리 ID',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `seller_user_id` bigint DEFAULT NULL COMMENT '판매자 계정 ID',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `view_count` bigint NOT NULL COMMENT '조회수',
  `seller_nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '판매자 닉네임',
  `name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품명',
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품 설명',
  `product_condition` enum('FAIR','GOOD','LIKE_NEW','NEW','POOR') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품 컨디션',
  `product_status` enum('ACTIVE','AUCTION_COMPLETED','AUCTION_SCHEDULED','DELETED','INACTIVE','IN_AUCTION','SOLD','TRADE_IN_PROGRESS') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품 상태',
  PRIMARY KEY (`id`),
  KEY `FK1mtsbur82frn64de7balymq9s` (`category_id`),
  CONSTRAINT `FK1mtsbur82frn64de7balymq9s` FOREIGN KEY (`category_id`) REFERENCES `category` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `product_images` (
  `sort_order` int NOT NULL COMMENT '이미지 정렬 순서',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `product_id` bigint NOT NULL COMMENT '상품 ID',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `image_url` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품 이미지 URL',
  `image_type` enum('DETAIL','THUMBNAIL') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품 이미지 유형',
  PRIMARY KEY (`id`),
  KEY `FKi8jnqq05sk5nkma3pfp3ylqrt` (`product_id`),
  CONSTRAINT `FKi8jnqq05sk5nkma3pfp3ylqrt` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
