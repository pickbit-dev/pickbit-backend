# 상품 등록부터 경매까지 프론트 연동 흐름

이 문서는 프론트엔드에서 상품 등록, 경매 생성, 입찰, 실시간 알림을 구현할 때 알아야 하는 전체 흐름을 정리한 문서입니다.

---

## 1. 전체 흐름

```text
상품 이미지 업로드
-> 상품 등록
-> 상품 상태 ACTIVE
-> 판매자가 해당 상품으로 경매 생성
-> 경매 상태 SCHEDULED
-> 상품 상태 AUCTION_SCHEDULED
-> 경매 시작 시간이 되면 자동으로 ACTIVE
-> 상품 상태 IN_AUCTION
-> 경매 상세 화면에서 WebSocket 구독
-> 입찰은 REST API로 요청
-> 입찰 성공/경매 종료는 WebSocket으로 실시간 수신
-> 경매 결과에 따라 상품 상태 변경
```

---

## 2. 상품 이미지 업로드

상품 등록 전에 이미지를 먼저 업로드합니다.

```http
POST /api/files/images
Content-Type: multipart/form-data
```

요청 예시:

```text
files: [image1.jpg, image2.jpg]
```

응답으로 받은 이미지 URL을 상품 등록 요청의 `images`에 포함합니다.

```text
이미지 업로드
-> imageUrl 응답 수신
-> 상품 등록 요청에 imageUrl 포함
```

---

## 3. 상품 등록

상품을 등록합니다.

```http
POST /products
Header: nickname: {sellerNickname}
Content-Type: application/json
```

요청 예시:

```json
{
  "name": "아이폰 15",
  "description": "상태 좋은 아이폰입니다.",
  "startingPrice": 10000,
  "productCondition": "GOOD",
  "categoryId": 1,
  "images": [
    {
      "imageUrl": "https://example.com/thumb.jpg",
      "imageType": "THUMBNAIL",
      "sortOrder": 0
    },
    {
      "imageUrl": "https://example.com/detail.jpg",
      "imageType": "DETAIL",
      "sortOrder": 1
    }
  ]
}
```

등록 성공 후 상품 상태는 `ACTIVE`입니다.

```text
ProductStatus.ACTIVE
```

`ACTIVE` 상태의 의미:

```text
판매 가능
수정 가능
삭제 가능
경매 등록 가능
```

---

## 4. 경매 생성

판매자는 `ACTIVE` 상태의 상품으로 경매를 생성할 수 있습니다.

```http
POST /auctions
Header: nickname: {sellerNickname}
Content-Type: application/json
```

요청 예시:

```json
{
  "productId": 1,
  "startingPrice": 10000,
  "buyNowPrice": 100000,
  "minimumBidIncrement": 1000,
  "startTime": "2026-05-01 10:00:00",
  "endTime": "2026-05-01 18:00:00"
}
```

경매 생성 조건:

```text
상품이 존재해야 함
상품 상태가 ACTIVE여야 함
요청자가 상품 판매자여야 함
같은 상품에 예정/진행 중인 경매가 없어야 함
종료 시간이 시작 시간보다 늦어야 함
```

경매 생성 성공 후 상태:

```text
AuctionStatus.SCHEDULED
ProductStatus.AUCTION_SCHEDULED
```

프론트 처리:

```text
경매 예정 상태로 표시
입찰 버튼 비활성화
상품 수정/삭제 버튼 숨김 또는 비활성화
```

---

## 5. 경매 시작

경매 시작 시간이 되면 백엔드 스케줄러가 자동으로 경매를 시작합니다.

```text
AuctionStatus.SCHEDULED -> AuctionStatus.ACTIVE
ProductStatus.AUCTION_SCHEDULED -> ProductStatus.IN_AUCTION
```

프론트는 경매 상세 조회 결과의 상태에 따라 버튼을 제어하면 됩니다.

```text
SCHEDULED: 입찰 버튼 비활성화
ACTIVE: 입찰 버튼 활성화
ENDED: 입찰 버튼 비활성화
CANCELLED: 입찰 버튼 비활성화
```

---

## 6. 경매 상세 화면 WebSocket 구독

경매 상세 화면에 진입하면 WebSocket을 연결하고 해당 경매 topic을 구독합니다.

WebSocket endpoint:

```text
/ws
```

구독 topic:

```text
/topic/auctions/{auctionId}
```

예시:

```text
/topic/auctions/10
```

이 topic은 경매방처럼 동작합니다. 같은 경매 상세 화면을 보고 있는 사용자들은 모두 같은 topic을 구독하고, 입찰이 성공할 때마다 같은 이벤트를 받습니다.

---

## 7. 입찰 요청

입찰은 WebSocket으로 보내지 않고 REST API로 요청합니다.

```http
POST /auctions/{auctionId}/bids
Header: nickname: {bidderNickname}
Content-Type: application/json
```

요청 예시:

```json
{
  "bidAmount": 15000
}
```

입찰 성공 조건:

```text
경매 상태가 ACTIVE여야 함
경매 종료 시간이 지나지 않아야 함
판매자 본인은 입찰할 수 없음
첫 입찰이면 시작가 이상이어야 함
두 번째 입찰부터는 현재가 + 최소 입찰 단위 이상이어야 함
```

백엔드는 같은 경매에 동시에 입찰이 들어와도 Redis lock으로 하나씩 처리합니다.

---

## 8. 입찰 성공 WebSocket 이벤트

입찰이 성공하면 백엔드가 해당 경매 topic으로 이벤트를 발행합니다.

topic:

```text
/topic/auctions/{auctionId}
```

일반 입찰 성공 이벤트 예시:

```json
{
  "auctionStatus": "ACTIVE",
  "currentPrice": 15000,
  "bidderNickname": "bidder1",
  "bidTime": "2026-05-01T10:03:12",
  "winnerNickname": null,
  "finalPrice": null
}
```

프론트 처리:

```text
현재가 갱신
최근 입찰자 표시
입찰 이력 새로고침 또는 화면에 새 항목 추가
```

---

## 9. 즉시 구매가 발생한 경우

입찰 금액이 `buyNowPrice` 이상이면 경매가 즉시 종료됩니다.

```text
AuctionStatus.ACTIVE -> AuctionStatus.ENDED
ProductStatus.IN_AUCTION -> ProductStatus.AUCTION_COMPLETED
```

WebSocket 이벤트 예시:

```json
{
  "auctionStatus": "ENDED",
  "currentPrice": 100000,
  "bidderNickname": null,
  "bidTime": null,
  "winnerNickname": "bidder1",
  "finalPrice": 100000
}
```

프론트 처리:

```text
입찰 버튼 비활성화
낙찰자 표시
최종 낙찰가 표시
경매 종료 UI로 전환
```

---

## 10. 경매 시간이 끝난 경우

경매 종료 시간이 지나면 백엔드 스케줄러가 자동 종료 처리합니다.

입찰자가 있는 경우:

```text
AuctionStatus.ACTIVE -> AuctionStatus.ENDED
ProductStatus.IN_AUCTION -> ProductStatus.AUCTION_COMPLETED
```

입찰자가 없는 경우, 즉 유찰:

```text
AuctionStatus.ACTIVE -> AuctionStatus.ENDED
ProductStatus.IN_AUCTION -> ProductStatus.ACTIVE
```

유찰 WebSocket 이벤트 예시:

```json
{
  "auctionStatus": "ENDED",
  "currentPrice": null,
  "bidderNickname": null,
  "bidTime": null,
  "winnerNickname": null,
  "finalPrice": null
}
```

프론트 처리:

```text
입찰 버튼 비활성화
유찰 상태 표시
낙찰자가 없음을 표시
```

유찰된 상품은 다시 `ACTIVE` 상태가 되므로, 이후 다시 경매 등록이 가능합니다.

---

## 11. 경매 취소

경매가 아직 시작 전이면 판매자가 경매를 취소할 수 있습니다.

```http
DELETE /auctions/{auctionId}
Header: nickname: {sellerNickname}
```

취소 가능한 상태:

```text
AuctionStatus.SCHEDULED
```

취소 후 상태:

```text
AuctionStatus.CANCELLED
ProductStatus.ACTIVE
```

프론트 처리:

```text
SCHEDULED 상태에서만 취소 버튼 표시
취소 후 상품은 다시 경매 등록 가능 상태로 표시
```

---

## 12. 상품 상태 정리

```text
ACTIVE: 판매 가능, 경매 등록 가능
AUCTION_SCHEDULED: 경매 예정, 상품 수정/삭제 제한
IN_AUCTION: 경매 진행 중, 상품 수정/삭제 제한
AUCTION_COMPLETED: 경매 낙찰 완료
INACTIVE: 비활성
DELETED: 삭제
```

상품 상세 화면 권장 처리:

```text
ACTIVE: 경매 생성 버튼 표시
AUCTION_SCHEDULED: 경매 예정 표시, 수정/삭제 숨김 또는 비활성화
IN_AUCTION: 경매 중 표시, 경매 상세로 이동 버튼 표시
AUCTION_COMPLETED: 낙찰 완료 표시
INACTIVE: 비활성 표시
DELETED: 노출하지 않음
```

---

## 13. 경매 상태 정리

```text
SCHEDULED: 경매 예정
ACTIVE: 경매 진행 중
ENDED: 경매 종료
CANCELLED: 경매 취소
```

경매 상세 화면 권장 처리:

```text
SCHEDULED: 시작 예정 시간 표시, 입찰 버튼 비활성화
ACTIVE: 현재가 표시, 입찰 버튼 활성화, WebSocket 구독
ENDED: 낙찰자/최종가 표시, 입찰 버튼 비활성화
CANCELLED: 취소된 경매 표시
```

---

## 14. 대표 입찰 실패 메시지

프론트에서 사용자에게 보여줄 수 있는 대표 실패 상황입니다.

```text
입찰가가 부족합니다.
이미 종료된 경매입니다.
ACTIVE 상태의 경매에만 입찰할 수 있습니다.
판매자는 본인 상품에 입찰할 수 없습니다.
입찰 처리 중입니다. 잠시 후 다시 시도해주세요.
```

---

## 15. 프론트가 기억하면 되는 핵심

```text
상품 등록 후 상품은 ACTIVE
ACTIVE 상품만 경매 생성 가능
경매 생성 후 경매는 SCHEDULED
상품은 AUCTION_SCHEDULED
시작 시간이 되면 경매는 ACTIVE
상품은 IN_AUCTION
입찰은 REST API로 요청
실시간 갱신은 WebSocket topic 구독
구독 topic은 /topic/auctions/{auctionId}
경매 종료/즉시구매/유찰도 WebSocket으로 수신
상품 상태 변경은 이벤트 기반이라 아주 짧은 지연이 있을 수 있음
```
