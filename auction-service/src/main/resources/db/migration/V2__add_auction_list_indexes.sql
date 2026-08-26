-- 경매 목록 조회 관련 인덱스.
--
-- auction 에는 PRIMARY KEY 밖에 없었다. 부하 테스트에서
-- SELECT COUNT(a.id) FROM auction 이 1,312,749회 호출됐다.
-- 건당은 싸지만(0.3~0.9ms) 호출 수가 많아 누적 비용이 컸다.
--
-- 상태로 좁히는 목록이 대부분이라 status 를 선두에 둔다.
CREATE INDEX idx_auction_status ON auction (auction_status);

-- 마감 임박순 정렬·종료 배치 조회에 쓰인다.
CREATE INDEX idx_auction_status_end_time ON auction (auction_status, end_time);
