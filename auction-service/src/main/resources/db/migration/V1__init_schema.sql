-- pickbit_auction_server 초기 스키마.
-- Hibernate 가 만든 스키마를 mysqldump 로 그대로 뜬 것이다. 손으로 쓰면 컬럼 순서나
-- 인덱스명이 어긋나 ddl-auto=validate 가 기동 시 실패한다.
-- 앞으로 엔티티를 바꾸면 반드시 V2__*.sql 을 함께 커밋할 것.
-- mysqldump 는 테이블을 알파벳순으로 내보내므로 외래키가 아직 없는 테이블을 참조할 수 있다.
-- (BATCH_JOB_EXECUTION 이 BATCH_JOB_INSTANCE 보다 먼저 나오는 식)
-- 세션 범위 설정이라 마이그레이션 밖에는 영향이 없다.
SET FOREIGN_KEY_CHECKS = 0;


CREATE TABLE `auction` (
  `buy_now_price` decimal(19,2) DEFAULT NULL COMMENT '즉시 구매가 (null이면 미사용)',
  `current_price` decimal(19,2) NOT NULL COMMENT '현재 최고 입찰가',
  `final_price` decimal(19,2) DEFAULT NULL COMMENT '낙찰가',
  `minimum_bid_increment` decimal(19,2) NOT NULL COMMENT '최소 입찰 단위',
  `starting_price` decimal(19,2) NOT NULL COMMENT '시작가',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `end_time` datetime(6) NOT NULL COMMENT '경매 종료 시각',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `product_id` bigint NOT NULL COMMENT '상품 ID (product-service 참조)',
  `seller_user_id` bigint DEFAULT NULL COMMENT '판매자 사용자 ID',
  `start_time` datetime(6) NOT NULL COMMENT '경매 시작 시각',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `version` bigint NOT NULL,
  `winner_user_id` bigint DEFAULT NULL COMMENT '낙찰자 사용자 ID',
  `seller_nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '판매자 닉네임 스냅샷',
  `winner_nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '낙찰자 닉네임',
  `product_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '상품명 스냅샷',
  `product_thumbnail_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '썸네일 URL 스냅샷',
  `auction_status` enum('ACTIVE','CANCELLED','ENDED','SCHEDULED') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '경매 상태',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `auction_event` (
  `current_price` decimal(19,2) DEFAULT NULL COMMENT '현재 최고 입찰가',
  `final_price` decimal(19,2) DEFAULT NULL COMMENT '최종 낙찰가',
  `auction_id` bigint NOT NULL COMMENT '경매 ID',
  `bid_id` bigint DEFAULT NULL COMMENT '입찰 ID',
  `bid_time` datetime(6) DEFAULT NULL COMMENT '입찰 시각',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `sequence` bigint NOT NULL COMMENT '경매 내 이벤트 순번',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `bidder_nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '입찰자 닉네임',
  `winner_nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '낙찰자 닉네임',
  `auction_status` enum('ACTIVE','CANCELLED','ENDED','SCHEDULED') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '경매 상태',
  `event_type` enum('AUCTION_CANCELLED','AUCTION_ENDED','AUCTION_STARTED','BID_PLACED') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '이벤트 종류',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auction_event_auction_sequence` (`auction_id`,`sequence`),
  CONSTRAINT `FKftriicowhp8a1w1d110cgdtv1` FOREIGN KEY (`auction_id`) REFERENCES `auction` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `bid` (
  `amount` decimal(19,2) NOT NULL COMMENT '입찰 금액',
  `auction_id` bigint NOT NULL COMMENT '경매 ID',
  `bid_time` datetime(6) NOT NULL COMMENT '입찰 시각',
  `bidder_user_id` bigint DEFAULT NULL COMMENT '입찰자 사용자 ID',
  `created_date` datetime(6) NOT NULL COMMENT '생성 일시',
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'id',
  `updated_date` datetime(6) NOT NULL COMMENT '업데이트 일시',
  `bidder_nickname` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '입찰자 닉네임',
  `bid_status` enum('ACTIVE','OUTBID','WINNING') COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '입찰 상태',
  PRIMARY KEY (`id`),
  KEY `idx_bid_auction_status` (`auction_id`,`bid_status`),
  CONSTRAINT `FKhexc6i4j8i0tmpt8bdulp6g3g` FOREIGN KEY (`auction_id`) REFERENCES `auction` (`id`)
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

SET FOREIGN_KEY_CHECKS = 1;
