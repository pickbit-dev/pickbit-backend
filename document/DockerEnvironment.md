# Pickbit Docker 환경 가이드

## 1. 목적

Pickbit 백엔드는 `develop`, `deploy` 환경을 Docker Compose로 실행할 수 있게 구성되어 있다.

Docker 스택에는 백엔드 애플리케이션, MySQL, Redis, Consul, Kafka, Kafka Connect, Kafka UI, ELK가 포함된다. 외부로 노출되는 포트는 `3306`, `6379`, `8080`, `8500`, `9200`, `5601` 같은 기본 포트를 피하고 Pickbit 전용 포트 대역을 사용한다.

## 2. 포트 정책

애플리케이션 컨테이너 내부 포트는 `18080-18087` 대역을 사용한다.

인프라 서비스는 기본 컨테이너 포트는 유지하되, develop은 디버깅용 host port를 노출하고 deploy는 외부 노출을 최소화한다. 같은 서버에서 develop/deploy를 동시에 실행할 수 있도록 deploy의 host port는 develop과 분리한다.

| 구성요소 | 컨테이너 포트 | develop 호스트 포트 | deploy 호스트 포트 |
| --- | ---: | ---: | ---: |
| Caddy HTTP/HTTPS | `80`, `443` | 미사용 | `80`, `443` |
| gateway-service | `18080` | `18080` | 내부 전용 |
| auth-service | `18081` | `18081` | 내부 전용 |
| user-service | `18082` | `18082` | 내부 전용 |
| product-service | `18083` | `18083` | 내부 전용 |
| file-service | `18084` | `18084` | 내부 전용 |
| auction-service | `18085` | `18085` | 내부 전용 |
| payment-service | `18086` | `18086` | 내부 전용 |
| notification-service | `18087` | `18087` | 내부 전용 |
| MySQL | `3306` | `13306` | 내부 전용 |
| Redis | `6379` | `16379` | 내부 전용 |
| Consul UI/API | `8500` | `18500` | `28500` |
| Kafka external listener | `19092` | `19092` | 내부 전용 |
| Kafka UI | `8080` | `19090` | 미노출 |
| Kafka Connect | `8083` | `18088` | 내부 전용 |
| Elasticsearch HTTP | `9200` | `19200` | 내부 전용 |
| Elasticsearch transport | `9300` | `19300` | 내부 전용 |
| Logstash TCP | `5000` | `15000` | 내부 전용 |
| Logstash monitoring | `9600` | `19600` | 내부 전용 |
| Kibana | `5601` | `15601` | `25601` |

deploy 환경에서 MySQL, Redis, Kafka, Kafka Connect, Elasticsearch, Logstash, 개별 백엔드 서비스는 Docker 내부 네트워크 전용이다. 외부 접속은 Gateway를 기준으로 한다.

## 3. 시크릿 파일

실제 비밀번호, API key, OAuth secret, JWT secret은 Git에 커밋하지 않는다.

커밋되는 템플릿 파일:

```text
application-secret.example.yml
```

커밋 금지 파일:

```text
secrets/application-develop-secret.yml
secrets/application-deploy-secret.yml
secrets/mysql-root-password.txt
```

`secrets/` 디렉터리는 `.gitignore` 처리되어 있다.

시크릿 파일 생성 예시:

```bash
mkdir -p secrets
cp application-secret.example.yml secrets/application-develop-secret.yml
cp application-secret.example.yml secrets/application-deploy-secret.yml
printf 'replace-me\n' > secrets/mysql-root-password.txt
```

생성 후 `replace-me` 값을 실제 값으로 바꾼다.

Spring Boot 서비스는 profile에 따라 아래 파일을 import한다.

```text
develop: ./secrets/application-develop-secret.yml
deploy: ./secrets/application-deploy-secret.yml
```

Docker 컨테이너에서는 `./secrets` 디렉터리가 `/app/secrets`로 read-only mount된다.

중요 시크릿 항목:

| 키 | 용도 |
| --- | --- |
| `*_MYSQL_URL`, `*_MYSQL_USERNAME`, `*_MYSQL_PASSWORD` | 서비스별 DB 연결 정보 |
| `JWT_SECRET` | JWT 서명 키 |
| `OAUTH_REDIRECT_BASE_URL` | OAuth redirect 기준 Gateway URL |
| `FRONTEND_OAUTH_CALLBACK_URL` | 프론트 OAuth callback URL |
| `FRONTEND_OAUTH_SIGNUP_URL` | 프론트 OAuth signup URL |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth 인증 정보 |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | Kakao OAuth 인증 정보 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | Naver OAuth 인증 정보 |
| `OPENAI_API_KEY` | 상품 AI OpenAI key |
| `NCP_STORAGE_*` | Object Storage 인증 정보 |
| `TOSS_*` | Toss Payments 설정 |

## 4. Develop 환경

develop 환경은 로컬 개발과 디버깅을 위한 구성이다. 각 백엔드 서비스 포트와 Kafka UI, Kibana, Elasticsearch, Kafka Connect를 외부에 노출한다.

최초 실행 전 시크릿 파일을 만든다.

```bash
mkdir -p secrets
cp application-secret.example.yml secrets/application-develop-secret.yml
printf 'replace-me\n' > secrets/mysql-root-password.txt
```

실행:

```bash
docker compose -f docker-compose.develop.yml up --build
```

주요 접속 URL:

| 도구 | URL |
| --- | --- |
| Gateway | `http://localhost:18080` |
| Consul UI | `http://localhost:18500` |
| Kafka UI | `http://localhost:19090` |
| Kibana | `http://localhost:15601` |
| Elasticsearch | `http://localhost:19200` |
| Kafka Connect | `http://localhost:18088` |

일반 클라이언트 트래픽은 Gateway를 통해 호출하는 것을 기준으로 한다. 개별 서비스 포트는 디버깅용이다.

## 5. Deploy 환경

deploy 환경은 단일 서버 Docker 배포를 위한 구성이다.

최초 실행 전 deploy 시크릿 파일을 만든다.

```bash
mkdir -p secrets
cp application-secret.example.yml secrets/application-deploy-secret.yml
printf 'replace-me\n' > secrets/mysql-root-password.txt
```

실행:

```bash
docker compose -f docker-compose.deploy.yml up --build -d
```

deploy 환경에서 외부 노출되는 구성:

| 구성요소 | URL |
| --- | --- |
| Caddy HTTPS | `https://pickbit.co.kr`, `https://api.pickbit.co.kr` |
| Consul UI | `http://<server-host>:28500` |
| Kibana | `http://<server-host>:25601` |

Gateway를 포함한 나머지 서비스와 인프라는 `pickbit-deploy` Docker 내부 네트워크에서만 통신한다. 외부 HTTP/HTTPS 트래픽은 Caddy가 `80`, `443` 포트에서 받는다. `api.pickbit.co.kr`은 내부 Gateway로 전달하고, `pickbit.co.kr`은 같은 Docker 네트워크의 프론트 컨테이너 `frontend:3000`으로 전달한다.

도메인 DNS는 아래 A 레코드를 서버 공인 IP로 연결한다.

```text
pickbit.co.kr      -> 서버 공인 IP
www.pickbit.co.kr  -> 서버 공인 IP
api.pickbit.co.kr  -> 서버 공인 IP
```

공유기 포트포워딩은 외부 `80`, `443`을 맥미니 내부 IP의 `80`, `443`으로 연결한다. Caddy는 Let's Encrypt 인증서를 자동으로 발급하고 갱신한다.

## 6. ELK 로그 연동

로그 흐름:

```text
Spring Boot service -> Logstash TCP appender -> Logstash -> Elasticsearch -> Kibana
```

각 서비스는 다음 값을 가진다.

```text
LOGSTASH_DESTINATION=logstash:5000
ENV=develop 또는 deploy
LOG_PATH=/logs
```

Logstash는 다음 index 패턴으로 Elasticsearch에 저장한다.

```text
pickbit-<environment>-YYYY.MM.dd
```

예시:

```text
pickbit-develop-2026.05.21
pickbit-deploy-2026.05.21
```

Kibana에서는 Data View를 아래 패턴으로 만들면 된다.

```text
pickbit-*
```

주요 로그 필드:

```text
application, environment, host, thread, logger, level, message, stack trace
```

## 7. Kafka와 Debezium

Compose 실행 시 기본 topic을 생성한다.

```text
AuthAccount-topic
Product-topic
User-topic
```

Kafka Connect는 Debezium Connect 이미지로 실행된다. `kafka-connect-init` 일회성 컨테이너가 아래 스크립트를 실행해 connector를 등록한다.

```text
docker/kafka-connect/register-connectors.sh
```

Connector 정의 위치:

```text
docker/kafka-connect/connectors
```

develop 환경에서는 Kafka UI에서 topic과 connector 상태를 확인할 수 있다.

```text
http://localhost:19090
```

## 8. Docker 이미지 빌드 방식

모든 Java 서비스는 루트 `Dockerfile`을 공유한다.

빌드 대상 서비스는 `SERVICE_NAME` build arg로 지정한다.

```yaml
build:
  context: .
  dockerfile: Dockerfile
  args:
    SERVICE_NAME: auth-service
```

Dockerfile은 선택된 서비스의 boot jar와 공통 `library` 모듈만 빌드한다.

## 9. 자주 쓰는 명령어

develop compose 검증:

```bash
docker compose -f docker-compose.develop.yml config
```

deploy compose 검증:

```bash
docker compose -f docker-compose.deploy.yml config
```

develop 시작:

```bash
docker compose -f docker-compose.develop.yml up --build
```

deploy 시작:

```bash
docker compose -f docker-compose.deploy.yml up --build -d
```

develop 중지:

```bash
docker compose -f docker-compose.develop.yml down
```

deploy 중지:

```bash
docker compose -f docker-compose.deploy.yml down
```

볼륨까지 삭제:

```bash
docker compose -f docker-compose.develop.yml down -v
```

볼륨 삭제는 DB, Kafka, Redis, Elasticsearch 데이터를 삭제하므로 의도한 경우에만 사용한다.

## 10. 운영 주의사항

`secrets/application-deploy-secret.yml`과 `secrets/mysql-root-password.txt`에는 강한 값을 사용한다.

운영 성격의 환경에서는 Gateway만 공개 트래픽을 받게 한다.

MySQL, Redis, Kafka, Kafka Connect, Logstash, Elasticsearch는 외부에 직접 노출하지 않는다.

중요한 유지보수 전에는 Docker volume을 백업한다.

```text
pickbit-deploy-mysql-data
pickbit-deploy-redis-data
pickbit-deploy-kafka-data
pickbit-deploy-elasticsearch-data
```

deploy profile은 JPA `ddl-auto=validate`를 기준으로 한다. 새 서버에서 테이블이 아직 없으면 부팅이 실패할 수 있다. 운영에서는 별도 마이그레이션 절차를 두는 것이 안전하다.

## 11. 트러블슈팅

서비스가 Consul을 찾지 못하면 컨테이너 내부에서 `consul-server:8500`을 사용하고 있는지 확인한다. 호스트 포트 `18500`은 브라우저 접속용이다.

서비스가 MySQL 또는 Redis에 붙지 못하면 Docker 내부 주소가 각각 `mysql`, `redis`인지 확인한다. 컨테이너 내부에서 `localhost`는 자기 자신이다.

Kibana에 로그가 보이지 않으면 먼저 Logstash 로그를 확인한다.

```bash
docker compose -f docker-compose.develop.yml logs -f logstash
```

Kafka Connect connector 등록이 안 되면 init 컨테이너를 다시 실행한다.

```bash
docker compose -f docker-compose.develop.yml up kafka-connect-init
```

시크릿 파일을 찾지 못한다면 컨테이너에 `/app/secrets`가 mount되었는지 확인한다.

```bash
docker compose -f docker-compose.develop.yml exec auth-service ls -l /app/secrets
```
