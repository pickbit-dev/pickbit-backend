# 경매 실시간 이벤트 프론트 연동 흐름

## 1. 전체 구조

경매 상세 화면은 REST API와 WebSocket을 함께 사용한다.

```text
REST API
- 경매 상세 조회
- 이벤트 히스토리 조회
- 입찰 요청
- 재연결 후 누락 이벤트 보정

WebSocket
- 실시간 경매 이벤트 수신
```

WebSocket은 실시간 알림용이고, 이벤트 조회 API는 과거 이벤트 및 누락 이벤트 복구용이다.

## 2. 페이지 진입 흐름

경매 상세 페이지에 진입하면 아래 순서로 처리한다.

```text
1. 경매 상세 조회
2. 최근 이벤트 조회
3. WebSocket 연결
4. topic 구독
```

### 2.1 경매 상세 조회

```http
GET /api/auctions/{auctionId}
```

이 응답은 화면의 기준 상태다.

```ts
const auction = await getAuctionDetail(auctionId);
setAuction(auction);
```

화면 상태 예:

```text
현재가
경매 상태
종료 시간
판매자
낙찰자
```

## 3. 이벤트 히스토리 조회

최근 이벤트를 조회한다.

```http
GET /api/auctions/{auctionId}/events?limit=50
```

응답 예:

```json
[
  {
    "eventId": 1,
    "eventType": "AUCTION_STARTED",
    "auctionId": 10,
    "bidId": null,
    "auctionStatus": "ACTIVE",
    "currentPrice": 10000,
    "bidderNickname": null,
    "bidTime": null,
    "winnerNickname": null,
    "finalPrice": null,
    "createdAt": "2026-06-02T16:00:00"
  },
  {
    "eventId": 2,
    "eventType": "BID_PLACED",
    "auctionId": 10,
    "bidId": 55,
    "auctionStatus": "ACTIVE",
    "currentPrice": 12000,
    "bidderNickname": "bidder1",
    "bidTime": "2026-06-02T16:05:00",
    "winnerNickname": null,
    "finalPrice": null,
    "createdAt": "2026-06-02T16:05:00"
  }
]
```

프론트는 마지막 이벤트 ID를 저장한다.

```ts
setEvents(events);
setLastEventId(events.at(-1)?.eventId ?? null);
```

## 4. WebSocket 연결

SockJS + STOMP를 사용한다.

Develop endpoint:

```text
http://192.168.20.70:18080/api/auctions/ws
```

Deploy endpoint:

```text
https://api.pickbit.co.kr/api/auctions/ws
```

구독 topic:

```text
/topic/auctions/{auctionId}
```

예시:

```ts
import SockJS from "sockjs-client";
import { Client } from "@stomp/stompjs";

const client = new Client({
  webSocketFactory: () =>
    new SockJS(`${API_BASE_URL}/api/auctions/ws`),
  reconnectDelay: 3000,
  onConnect: () => {
    client.subscribe(`/topic/auctions/${auctionId}`, (message) => {
      const event = JSON.parse(message.body);
      handleAuctionEvent(event);
    });
  },
});

client.activate();
```

## 5. WebSocket 이벤트 타입

```ts
type AuctionEvent = {
  eventId: number;
  eventType: "AUCTION_STARTED" | "BID_PLACED" | "AUCTION_ENDED";
  auctionId: number;
  bidId: number | null;
  auctionStatus: "SCHEDULED" | "ACTIVE" | "ENDED" | "CANCELLED";
  currentPrice: number | null;
  bidderNickname: string | null;
  bidTime: string | null;
  winnerNickname: string | null;
  finalPrice: number | null;
  createdAt: string;
};
```

## 6. 이벤트 처리

중복 이벤트를 방지해야 한다.

```ts
function handleAuctionEvent(event: AuctionEvent) {
  setEvents((prev) => {
    if (prev.some((item) => item.eventId === event.eventId)) {
      return prev;
    }
    return [...prev, event];
  });

  setLastEventId(event.eventId);

  if (event.eventType === "AUCTION_STARTED") {
    setAuction((prev) => ({
      ...prev,
      auctionStatus: "ACTIVE",
      currentPrice: event.currentPrice,
    }));
  }

  if (event.eventType === "BID_PLACED") {
    setAuction((prev) => ({
      ...prev,
      auctionStatus: event.auctionStatus,
      currentPrice: event.currentPrice,
    }));
  }

  if (event.eventType === "AUCTION_ENDED") {
    setAuction((prev) => ({
      ...prev,
      auctionStatus: "ENDED",
      currentPrice: event.currentPrice,
      finalPrice: event.finalPrice,
      winnerNickname: event.winnerNickname,
    }));
  }
}
```

## 7. 입찰 요청

입찰은 WebSocket으로 보내지 않고 REST API로 보낸다.

```http
POST /api/auctions/{auctionId}/bids
```

예시:

```ts
await fetch(`${API_BASE_URL}/api/auctions/${auctionId}/bids`, {
  method: "POST",
  headers: {
    "Content-Type": "application/json",
  },
  credentials: "include",
  body: JSON.stringify({
    bidAmount: Number(bidAmount),
  }),
});
```

권장 흐름:

```text
입찰 POST 성공
-> WebSocket BID_PLACED 이벤트 수신
-> 화면 현재가 갱신
```

입찰 성공 직후 프론트가 낙관적으로 현재가를 바꾸기보다는, WebSocket 이벤트를 기준으로 반영하는 것이 안전하다.

## 8. 재연결 시 누락 이벤트 보정

WebSocket이 끊긴 동안 발생한 이벤트는 REST API로 조회한다.

```http
GET /api/auctions/{auctionId}/events?afterEventId={lastEventId}&limit=100
```

예시:

```ts
async function syncMissedEvents() {
  if (!lastEventId) return;

  const response = await fetch(
    `${API_BASE_URL}/api/auctions/${auctionId}/events?afterEventId=${lastEventId}&limit=100`
  );

  const missedEvents = await response.json();
  missedEvents.forEach(handleAuctionEvent);
}
```

추천 순서:

```text
WebSocket reconnect
-> topic subscribe
-> missed events 조회
-> 화면 보정
```

구독을 먼저 하는 이유는 missed events 조회 중 새 이벤트가 발생해도 WebSocket으로 받을 수 있기 때문이다.

## 9. 페이지 초기화 예시

```ts
async function initAuctionPage(auctionId: number) {
  const auction = await getAuctionDetail(auctionId);
  setAuction(auction);

  const events = await getAuctionEvents(auctionId, { limit: 50 });
  setEvents(events);
  setLastEventId(events.at(-1)?.eventId ?? null);

  connectWebSocket();
}
```

## 10. 이벤트 렌더링 예시

```ts
function renderEvent(event: AuctionEvent) {
  if (event.eventType === "AUCTION_STARTED") {
    return `경매가 시작되었습니다. 현재가 ${event.currentPrice}원`;
  }

  if (event.eventType === "BID_PLACED") {
    return `${event.bidderNickname}님이 ${event.currentPrice}원에 입찰했습니다.`;
  }

  if (event.eventType === "AUCTION_ENDED") {
    if (event.winnerNickname) {
      return `경매가 종료되었습니다. 낙찰자 ${event.winnerNickname}, 낙찰가 ${event.finalPrice}원`;
    }
    return "경매가 유찰되었습니다.";
  }

  return "알 수 없는 이벤트입니다.";
}
```

## 11. 핵심 상태

프론트는 최소 아래 상태를 관리하면 된다.

```ts
auction
events
lastEventId
stompClient
connectionStatus
```

## 12. 요약

```text
GET /api/auctions/{id}
GET /api/auctions/{id}/events?limit=50
SockJS connect /api/auctions/ws
SUBSCRIBE /topic/auctions/{id}
POST /api/auctions/{id}/bids
on reconnect: GET /events?afterEventId={lastEventId}
```

WebSocket은 실시간 전달용이고, 이벤트 조회 API는 히스토리 및 누락 보정용이다.
