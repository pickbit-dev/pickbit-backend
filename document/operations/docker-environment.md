# Pickbit Docker 환경 가이드

## 1. 목적

Pickbit 백엔드는 `develop`, `deploy` 환경을 Docker Compose로 구성한다.

develop 스택은 로컬 개발용 인프라만 실행한다. deploy 스택은 백엔드 애플리케이션, 프론트엔드, MySQL, Redis, Consul, Kafka, Kafka Connect, Loki 기반 로깅, Caddy를 실행한다. 외부로 노출되는 포트는 `3306`, `6379`, `8080`, `8500` 같은 기본 포트를 피하고 Pickbit 전용 포트 대역을 사용한다.

> deploy 환경은 AWS EC2 단일 노드에서 돌아간다. 프로비저닝, 배포/롤백 절차는
> [aws-ec2-deployment.md](./aws-ec2-deployment.md)를 참고한다.
> 스키마는 Flyway가 기동 시 적용하므로 별도 부트스트랩 절차가 없다.
>
> **관측 스택은 ELK에서 Loki + Grafana로 교체되었다.** 16GB 인스턴스에서 Elasticsearch +
> Logstash + Kibana가 약 4GB를 차지해 부담이 컸다. develop 스택은 기존 ELK를 그대로 쓴다.

## 2. 포트 정책

애플리케이션 컨테이너 내부 포트는 `18080-18087` 대역을 사용한다.

인프라 서비스는 기본 컨테이너 포트는 유지하되, develop은 디버깅용 host port를 노출하고 deploy는 외부 노출을 최소화한다. 같은 서버에서 develop/deploy를 동시에 실행할 수 있도록 deploy의 host port는 develop과 분리한다.

| 구성요소 | 컨테이너 포트 | develop 호스트 포트 | deploy 호스트 포트 |
| --- | ---: | ---: | ---: |
| Caddy HTTP/HTTPS | `80`, `443` | 미사용 | `80`, `443` |
| gateway-service | `18080` | Docker 미사용 | 내부 전용 |
| auth-service | `18081` | Docker 미사용 | 내부 전용 |
| user-service | `18082` | Docker 미사용 | 내부 전용 |
| product-service | `18083` | Docker 미사용 | 내부 전용 |
| file-service | `18084` | Docker 미사용 | 내부 전용 |
| auction-service | `18085` | Docker 미사용 | 내부 전용 |
| payment-service | `18086` | Docker 미사용 | 내부 전용 |
| notification-service | `18087` | Docker 미사용 | 내부 전용 |
| frontend (Next.js) | `3000` | Docker 미사용 | 내부 전용 |
| MySQL | `3306` | `13306` | `127.0.0.1:23306` |
| Redis | `6379` | `16379` | `127.0.0.1:26379` |
| Consul UI/API | `8500` | `18500` | `127.0.0.1:28500` |
| Kafka external listener | `19092` | `19092` | 내부 전용 |
| Kafka UI | `8080` | `19090` | 미노출 |
| Kafka Connect | `8083` | `18088` | 내부 전용 |
| Loki | `3100` | 미사용 | 내부 전용 |
| Grafana | `3000` | 미사용 | `127.0.0.1:23000` |
| Elasticsearch HTTP | `9200` | `19200` | 미사용 (deploy에서 제거) |
| Elasticsearch transport | `9300` | `19300` | 미사용 (deploy에서 제거) |
| Logstash TCP | `5000` | `15000` | 미사용 (deploy에서 제거) |
| Logstash monitoring | `9600` | `19600` | 미사용 (deploy에서 제거) |
| Kibana | `5601` | `15601` | 미사용 (deploy에서 제거) |

deploy 환경에서 Kafka, Kafka Connect, Loki, 프론트엔드, 개별 백엔드 서비스는 Docker 내부 네트워크 전용이다. 외부 접속은 Caddy를 기준으로 한다.

MySQL, Redis, Consul UI, Grafana는 **루프백에만 바인딩**한다. `127.0.0.1:` 접두사가 없으면 `0.0.0.0`에 붙는데, Docker의 iptables 규칙은 호스트 방화벽을 우회하므로 보안그룹만 열리면 그대로 인터넷에 노출된다. 접근은 SSH 터널을 쓴다.

특히 두 가지는 노출되면 피해가 크다. **Redis는 `requirepass`가 없어** 인증 없이 경매 상태·입찰 스트림은 물론 auth-service가 저장하는 refresh token까지 읽고 쓸 수 있다. Consul은 ACL이 없어 UI에 닿는 누구나 서비스를 등록/해제할 수 있다.

```bash
ssh -i pickbit.pem \
    -L 23000:localhost:23000 \
    -L 23306:localhost:23306 \
    -L 26379:localhost:26379 \
    -L 28500:localhost:28500 \
    ubuntu@<Elastic-IP>
```

DataGrip·RedisInsight 같은 GUI는 대개 SSH 터널이 내장돼 있어 위 명령 없이도 붙는다. 다만 **접속 정보의 Host/Port는 EC2 입장에서** 적어야 한다 (`localhost:23306`). 도구가 SSH로 들어간 뒤 그 서버에서 다시 연결하기 때문이다.

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
| `FRONTEND_OAUTH_LINK_URL` | 프론트 OAuth 계정 연결 URL |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | Google OAuth 인증 정보 |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | Kakao OAuth 인증 정보 |
| `NAVER_CLIENT_ID`, `NAVER_CLIENT_SECRET` | Naver OAuth 인증 정보 |
| `OPENAI_API_KEY` | 상품 AI OpenAI key |
| `NCP_STORAGE_*` | Object Storage 인증 정보 |
| `TOSS_*` | Toss Payments 설정 |

## 4. Develop 환경

develop 환경은 로컬 개발과 디버깅을 위한 인프라 전용 구성이다. 백엔드 애플리케이션 컨테이너는 실행하지 않고 MySQL, Redis, Consul, Kafka, Kafka Connect, Kafka UI, ELK만 실행한다. 각 Spring Boot 서비스는 IDE 또는 Gradle로 직접 실행한다.

최초 실행 전 시크릿 파일을 만든다.

```bash
mkdir -p secrets
cp application-secret.example.yml secrets/application-develop-secret.yml
printf 'replace-me\n' > secrets/mysql-root-password.txt
```

실행:

```bash
docker compose -f docker-compose.develop.yml up -d
```

주요 접속 URL:

| 도구 | URL |
| --- | --- |
| Consul UI | `http://localhost:18500` |
| Kafka UI | `http://localhost:19090` |
| Kibana | `http://localhost:15601` |
| Elasticsearch | `http://localhost:19200` |
| Kafka Connect | `http://localhost:18088` |

Spring Boot 서비스는 로컬에서 직접 실행한다. 예를 들어 Gateway는 IDE에서 실행하거나 `./gradlew :gateway-service:bootRun`으로 실행한 뒤 `http://localhost:18080`으로 접근한다.

Docker로 특정 백엔드 서비스만 develop 네트워크에 붙여 실행해야 할 때는 모듈별 develop compose를 사용한다. 먼저 인프라를 실행한 뒤 필요한 서비스만 실행한다.

```bash
docker compose -f docker-compose.develop.yml up -d
docker compose -f docker/compose/services/auth-service.develop.yml up --build -d
```

모듈별 develop compose는 `pickbit-develop` 외부 네트워크에 연결되고 해당 서비스 포트만 host에 노출한다. 기본 로컬 개발 방식은 IDE 또는 Gradle 직접 실행이며, 모듈별 develop compose는 선택적으로 사용한다.

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

개별 백엔드 서비스만 운영 네트워크에 재배포할 때는 모듈별 deploy compose를 사용한다. 모듈별 deploy compose는 host port를 노출하지 않고 `pickbit-deploy` 외부 네트워크에만 연결한다.

```bash
docker compose -f docker/compose/services/payment-service.deploy.yml up --build -d
```

GitHub Actions의 자동 서비스별 배포도 모듈별 deploy compose를 사용한다. 수동 `all` 배포는 루트 `docker-compose.deploy.yml`로 인프라를 포함한 전체 stack을 배포한다.

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

Docker 컨테이너에서 실행되는 서비스는 `LOGSTASH_DESTINATION=logstash:5000`을 사용한다. IntelliJ 또는 Gradle로 develop 프로필을 로컬 실행할 때는 `LOGSTASH_DESTINATION`을 지정하지 않아도 기본값으로 `localhost:15000`을 사용한다.

```text
develop 로컬 실행: localhost:15000
develop Docker 실행: logstash:5000
deploy Docker 실행: logstash:5000
```

명시적으로 `LOGSTASH_DESTINATION`을 지정하면 해당 값이 우선된다.

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
