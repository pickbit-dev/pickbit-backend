-- 상품 목록 조회 관련 인덱스.
--
-- 부하 테스트(상품 71,750건)에서 잰 값이다. 기대만큼 크지는 않다:
--
--   COUNT(*) WHERE product_status != 'DELETED'
--     인덱스 없음 : 25.7ms  (Table scan, 71,750행)
--     인덱스 있음 : 23.0ms  (index scan)   -> 약 10%
--
-- != 는 사실상 전 구간을 읽으므로 인덱스로는 여기까지가 한계다. 목록 조회의 진짜 비용은
-- "매 요청마다 센다"는 것이고 그건 애플리케이션에서 카운트를 캐싱해 해결한다.
-- 그래도 테이블 스캔이 인덱스 스캔으로 바뀌어 버퍼 풀 압박이 줄고, 데이터가 더 늘었을 때
-- 악화 속도가 완만해진다.
CREATE INDEX idx_product_status ON product (product_status);

-- 최신순 정렬이 붙는 목록에서 파일소트를 없앤다.
CREATE INDEX idx_product_status_created ON product (product_status, created_date);

-- 내 판매목록(/api/products/me/selling)은 판매자로 먼저 좁히므로 이쪽이 선두여야 한다.
-- 부하 테스트에서 이 경로의 COUNT 도 호출당 4,869행을 훑고 있었다.
CREATE INDEX idx_product_seller_status ON product (seller_user_id, product_status);
