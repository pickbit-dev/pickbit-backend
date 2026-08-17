# AWS EC2 단일 노드 배포 가이드

맥미니 self-hosted runner 배포를 AWS EC2 한 대로 옮기는 절차입니다.
백엔드 8개 서비스 + 프론트 + 인프라가 전부 이 인스턴스 한 대에 올라갑니다.

관련 문서: [docker-environment.md](./docker-environment.md) (포트/컨테이너 레퍼런스)

---

## 1. 인스턴스 사양과 비용

| 항목 | 값 | 비고 |
|---|---|---|
| 리전 | `ap-northeast-2` (서울) | Toss/NCP와 같은 국내 |
| 인스턴스 | **m7g.xlarge** (ARM Graviton3, 4 vCPU / 16 GB) | 아래 표 참고. 이미지가 전부 arm64 멀티아치여야 함 |
| EBS | gp3 60 GB | MySQL + Kafka 로그 + 이미지 레이어 |
| 스왑 | 4 GB 파일 | OOM 완충 |
| Elastic IP | **1개 필수** | 아래 주의사항 참고 |
| 보안그룹 | 22 (내 IP만) / 80 / 443 | 나머지는 SSH 터널 |
| OS | Ubuntu 24.04 LTS **arm64** | |

### 인스턴스 선택

| 인스턴스 | vCPU/RAM | 시간당(서울) | 월 160h | 용도 |
|---|---|---|---|---|
| t4g.xlarge | 4/16, 버스트 | ~$0.171 | ~4만원 | **지속 부하 부적합** |
| **m7g.xlarge (기본)** | 4/16, 비버스트 | ~$0.194 | ~4.5만원 | 평상시 운영 |
| m7g.2xlarge | 8/32 | ~$0.388 | ~9만원 | 1000 rps 측정 |

**t4g 를 쓰지 마세요.** 버스트 인스턴스라 기준 성능이 4 vCPU 의 40%(약 1.6 vCPU)입니다.
지속 부하에서 CPU 크레딧이 고갈되며 스로틀링되어 **측정값 자체를 신뢰할 수 없습니다.**
m7g.xlarge 는 비버스트인데도 비용이 거의 같습니다.

컨테이너 `mem_limit` 합계가 약 12.5 GB 라 16 GB 에서 평상시 운영은 가능하지만,
**1000 rps 목표 측정에는 m7g.2xlarge 를 권합니다.** 4 vCPU 를 15개 컨테이너가 나눠 쓰는
상황에서 1000 rps 는 CPU 가 먼저 천장입니다. 측정 중 CloudWatch `CPUUtilization` 이 80%를
지속하면 승격하세요.

### 비용 (서울 온디맨드 기준)

- m7g.xlarge 기준 하루 8시간 × 월 20일(160h) = **약 4.5만원**
- EBS 60 GB gp3 ≈ 월 7천원 — **인스턴스를 정지해도 계속 과금됩니다**
- 합계 **월 5만원** 선. 24/7 가동 시 약 20만원, 1년 예약 인스턴스로 약 12만원.

### 켜고 끄기

```bash
aws ec2 start-instances --instance-ids i-xxxxxxxx
aws ec2 stop-instances  --instance-ids i-xxxxxxxx
```

EBS가 남아 MySQL 데이터가 유지되고, 모든 컨테이너에 `restart: unless-stopped`가 걸려 있어
부팅되면 스택이 자동으로 올라옵니다.

> **Elastic IP를 반드시 붙이세요.** 기본 설정이면 stop/start 할 때마다 퍼블릭 IP가 바뀌어
> `pickbit.co.kr` DNS가 깨지고, Caddy가 인증서를 다시 발급받다가 Let's Encrypt 발급 한도
> (동일 도메인 주당 5회)에 걸립니다. EIP는 인스턴스에 연결된 동안 무료입니다.

> **`pickbit-deploy-caddy-data` 볼륨을 절대 지우지 마세요.** 발급받은 인증서가 여기 저장됩니다.

---

## 2. 최초 프로비저닝 (1회)

### 2-1. AWS 리소스

1. 위 사양으로 EC2 기동, Elastic IP 연결
2. **인스턴스 프로파일**에 `AmazonSSMManagedInstanceCore` 부착 (CI가 SSM으로 배포하므로 필수)
3. 보안그룹 인바운드: 22 (내 IP) / 80 (0.0.0.0/0) / 443 (0.0.0.0/0)

CI 배포는 SSM 에이전트가 AWS 쪽으로 **아웃바운드** 연결을 맺어 명령을 받아가는 구조입니다.
GitHub 러너가 인스턴스로 들어오지 않으므로 **인바운드를 추가로 열 필요가 없습니다.**
22번을 내 IP 로만 막아둔 채로 CI 배포가 동작합니다. 대신 443 아웃바운드는 열려 있어야 합니다.

#### OIDC 역할

장기 액세스 키를 쓰지 않고 GitHub Actions가 역할을 잠깐 빌려 쓰게 합니다.
여기서 가장 많이 막히므로 그대로 옮겨 쓸 수 있게 남깁니다.

**① IAM → Identity providers → Add provider → OpenID Connect**

| 항목 | 값 |
|---|---|
| Provider URL | `https://token.actions.githubusercontent.com` |
| Audience | `sts.amazonaws.com` |

**② 역할 생성 — 신뢰 정책**

`<ACCOUNT_ID>` 와 레포 경로를 본인 것으로 바꿉니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {
      "Federated": "arn:aws:iam::<ACCOUNT_ID>:oidc-provider/token.actions.githubusercontent.com"
    },
    "Action": "sts:AssumeRoleWithWebIdentity",
    "Condition": {
      "StringEquals": { "token.actions.githubusercontent.com:aud": "sts.amazonaws.com" },
      "StringLike":   { "token.actions.githubusercontent.com:sub": "repo:<OWNER>/<REPO>:*" }
    }
  }]
}
```

`sub` 조건을 빼면 **다른 사람의 레포에서도 이 역할을 빌릴 수 있습니다.** 반드시 넣으세요.

**③ 권한 정책** — 워크플로가 실제로 호출하는 API 만 담았습니다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "ssm:SendCommand",
      "Resource": [
        "arn:aws:ec2:ap-northeast-2:<ACCOUNT_ID>:instance/<INSTANCE_ID>",
        "arn:aws:ssm:ap-northeast-2::document/AWS-RunShellScript"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["ssm:GetCommandInvocation", "ssm:ListCommandInvocations"],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": "ec2:DescribeInstances",
      "Resource": "*"
    }
  ]
}
```

뒤 두 개는 리소스 단위 권한을 지원하지 않아 `*` 여야 합니다.
`ssm:SendCommand` 는 인스턴스와 문서 ARN 을 모두 적어야 하며, 하나라도 빠지면 `AccessDenied` 가 납니다.

**④ 연결 확인** — 인스턴스를 켠 상태에서

```bash
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=<INSTANCE_ID>" \
  --query 'InstanceInformationList[0].{Ping:PingStatus,Agent:AgentVersion}'
```

`PingStatus: Online` 이어야 CI 가 명령을 보낼 수 있습니다. `Online` 이 아니면 인스턴스
프로파일이 안 붙었거나 443 아웃바운드가 막힌 것입니다 — 보안그룹 인바운드 문제가 아닙니다.

### 2-2. 인스턴스 초기 설정

```bash
# 스왑 4GB
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 도커
sudo apt-get update && sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker ubuntu

# 레포 (디렉터리 이름은 .env의 COMPOSE_PROJECT_NAME으로 고정되므로 자유롭게 둬도 됨)
sudo mkdir -p /opt/pickbit && sudo chown ubuntu:ubuntu /opt/pickbit
git clone <repo-url> /opt/pickbit/pickbit-backend
cd /opt/pickbit/pickbit-backend

# 앱 컨테이너보다 먼저 네트워크가 있어야 한다
docker network create pickbit-deploy || true
```

### 2-3. 설정과 시크릿

```bash
cd /opt/pickbit/pickbit-backend
cp .env.example .env
vi .env          # GHCR_OWNER 를 본인 GitHub 계정으로

mkdir -p secrets && chmod 700 secrets
# application-secret.example.yml 이 필요한 키 전체 목록입니다.
cp application-secret.example.yml secrets/application-deploy-secret.yml
vi secrets/application-deploy-secret.yml
openssl rand -base64 24 > secrets/mysql-root-password.txt
openssl rand -base64 24 > secrets/grafana-admin-password.txt
chmod 600 secrets/*
```

`secrets/`는 gitignore 대상이며 **CI는 시크릿을 전혀 건드리지 않습니다.** 인스턴스에만 둡니다.

GHCR 패키지가 private이면 1회 로그인:
```bash
echo <GITHUB_PAT> | docker login ghcr.io -u <github-username> --password-stdin
```

### 2-4. DNS

Elastic IP로 A 레코드 3개:
- `api.pickbit.co.kr`
- `pickbit.co.kr`
- `www.pickbit.co.kr`

Caddy가 Let's Encrypt 인증서를 자동 발급하므로 **80/443이 열려 있고 DNS 전파가 끝난 뒤에**
스택을 올려야 합니다.

### 2-5. GitHub 설정

Settings → Secrets and variables → Actions

| 위치 | 이름 | 값 |
|---|---|---|
| Secrets | `AWS_DEPLOY_ROLE_ARN` | OIDC 역할 ARN |
| Secrets | `EC2_INSTANCE_ID` | `i-xxxxxxxx` |
| **Variables** | `AWS_REGION` | `ap-northeast-2` |
| Secrets (프론트 레포) | `NEXT_PUBLIC_TOSS_CLIENT_KEY` | Toss 클라이언트 키 |

> `AWS_REGION` 만 **Variables 탭**입니다. 워크플로가 `vars.AWS_REGION` 으로 읽기 때문에
> Secrets 에 넣으면 빈 문자열이 되고, 리전 없이 AWS 를 호출하다 실패합니다.
> 두 탭이 같은 화면에 있어 헷갈리기 쉽습니다.

---

### 2-6. 최초 배포 순서

0~2장을 마쳤다면 아래 순서로 올립니다. **순서가 중요합니다.**

| 순서 | 할 일 | 이유 |
|---|---|---|
| 1 | 프론트 레포 CI 를 돌려 `pickbit-frontend` 이미지를 GHCR 에 올린다 | `caddy` 가 `frontend` 에 `depends_on` 이라 이미지가 없으면 caddy 가 안 뜬다 |
| 2 | **인스턴스를 끈 채로** 배포 브랜치(`main`)에 머지 | 이미지 8개는 빌드·푸시되고, 배포는 자동으로 건너뛴다 |
| 3 | 인스턴스를 켜고 **3장 부트스트랩을 수동 실행** | CI 는 부트스트랩을 못 한다 (아래 참고) |
| 4 | 4장 확인 절차 | |

**2단계가 요령입니다.** 워크플로는 인스턴스가 `running` 이 아니면 배포를 경고만 남기고
건너뜁니다. 그래서 인스턴스를 끈 채로 머지하면 **이미지는 GHCR 에 확보되면서, 스키마가 없는
상태로 배포가 돌아 실패하는 것은 피할 수 있습니다.**

> **CI 는 최초 기동을 대신할 수 없습니다.** SSM 으로 실행되는 건 `pull` + `up -d --no-deps`
> 뿐이라 스키마를 만들지 않습니다. 빈 DB 에 `validate` 가 걸려 DB 를 쓰는 서비스 6개가
> 전부 기동에 실패합니다. 첫 기동만큼은 반드시 인스턴스에서 직접 하세요.

---

## 3. 최초 기동 — 스키마 부트스트랩

**이 절차를 건너뛰면 DB를 쓰는 서비스 6개가 전부 기동에 실패합니다.**

Flyway/Liquibase가 없고 `docker/mysql/init/01-create-databases.sql`은 **빈 데이터베이스만**
만듭니다(테이블 없음). 평소에는 `ddl-auto=validate`로 도는데, 빈 스키마에 validate를 걸면
`SchemaManagementException`이 납니다. 최초 1회만 Hibernate에게 스키마를 만들게 합니다.

```bash
cd /opt/pickbit/pickbit-backend

# 1) 부트스트랩 모드로 전환
sed -i 's/^# SPRING_JPA_HIBERNATE_DDL_AUTO=update/SPRING_JPA_HIBERNATE_DDL_AUTO=update/' .env
sed -i 's/^# BATCH_JDBC_INITIALIZE_SCHEMA=always/BATCH_JDBC_INITIALIZE_SCHEMA=always/' .env

# 2) 전체 기동
docker compose -f docker-compose.deploy.yml pull
docker compose -f docker-compose.deploy.yml up -d

# 3) 앱 8개가 healthy 가 될 때까지 대기 (JVM 기동에 1~2분)
watch -n 5 'docker compose -f docker-compose.deploy.yml ps'

# 4) 아웃박스 테이블이 생긴 뒤에 Debezium 커넥터를 등록한다.
#    (테이블보다 먼저 등록하면 스냅샷이 빈 테이블을 잡는다)
docker compose -f docker-compose.deploy.yml up kafka-connect-init

# 5) 다시 validate 로 되돌리고 재기동
sed -i 's/^SPRING_JPA_HIBERNATE_DDL_AUTO=update/# SPRING_JPA_HIBERNATE_DDL_AUTO=update/' .env
sed -i 's/^BATCH_JDBC_INITIALIZE_SCHEMA=always/# BATCH_JDBC_INITIALIZE_SCHEMA=always/' .env
docker compose -f docker-compose.deploy.yml up -d
```

이후 스키마가 바뀌는 변경을 배포할 때는 같은 절차를 반복하거나, Flyway를 도입하세요
(백로그 권장 항목).

---

## 4. 평소 운영

### 배포
`main`에 푸시하면 GitHub Actions가 변경된 서비스만 ARM 러너에서 빌드해 GHCR에 올리고,
SSM으로 EC2에서 `pull` + `up -d --no-deps` 를 실행합니다. **인스턴스가 꺼져 있으면
배포는 경고만 남기고 건너뜁니다** — 켠 뒤 워크플로를 수동 실행하세요.

### 수동 배포
```bash
cd /opt/pickbit/pickbit-backend
docker compose -f docker-compose.deploy.yml pull
docker compose -f docker-compose.deploy.yml up -d
```

특정 서비스만:
```bash
docker compose -f docker-compose.deploy.yml up -d --no-deps auction-service
```

### 롤백
`.env`의 `BACKEND_IMAGE_TAG` 또는 `FRONTEND_IMAGE_TAG`를 과거 커밋 SHA로 바꾸고 `up -d`.
백엔드/프론트가 별도 레포라 태그가 분리되어 있어 한쪽만 되돌릴 수 있습니다.

### 관리 UI 접근 (SSH 터널)

Consul UI와 Grafana는 **루프백에만 바인딩**되어 있습니다. Consul은 ACL이 없어서 UI에 닿는
누구나 서비스를 등록/해제할 수 있기 때문입니다.

```bash
ssh -i pickbit.pem \
    -L 23000:localhost:23000 \
    -L 28500:localhost:28500 \
    ubuntu@<Elastic-IP>
```

- Grafana: http://localhost:23000 (`admin` / `secrets/grafana-admin-password.txt`)
- Consul UI: http://localhost:28500

### 대시보드
Grafana → Dashboards → Pickbit → **Pickbit Overview**. 프로비저닝으로 자동 등록됩니다.

| 패널 | 보는 것 |
|---|---|
| 서비스 연결 상태 | 8개 서비스가 살아 있는지 (`up`). DOWN 이면 빨간색 |
| 전체 처리량 | 초당 요청 수. 1000 rps 목표를 여기서 확인 |
| 5xx 에러율 | 1% 넘으면 빨간색 |
| p95 / p99 응답 시간 | 히스토그램 버킷에서 계산 (인스턴스가 늘어나도 정확히 합산됨) |
| 엔드포인트별 p95 | 어느 API 가 느린지 |
| 5xx 발생 엔드포인트 | 어떤 API 가 500 을 내는지 |
| HikariCP 커넥션 대기 | 0 이 아니면 커넥션 풀 부족 |
| JVM 힙 사용률 | 90% 넘으면 OOM 위험 |
| Kafka 소비 지연 | 컨슈머가 생산 속도를 따라가는지 |
| ERROR 로그 | 메트릭에서 이상이 보이면 바로 원인 확인 |

Prometheus 는 각 서비스의 `/actuator/prometheus` 를 15초마다 긁습니다. 보관은 15일 / 4GB 입니다.

> `/actuator` 전체가 아니라 **`/actuator/health` 와 `/actuator/info` 만** 게이트웨이 공개
> 경로입니다. 전체를 열면 `/actuator/prometheus` 로 내부 메트릭이 인증 없이 새어 나갑니다.
> Prometheus 는 내부 네트워크에서 각 서비스를 직접 긁으므로 영향받지 않습니다.

### 로그 조회
Grafana → Explore → Loki 데이터소스. 앱 컨테이너는 JSON으로 stdout에 로그를 쓰고
Grafana Alloy가 도커 소켓에서 수집합니다.

```logql
{container="pickbit-deploy-auction-service"}
{job="docker"} |= "ERROR"
{application="payment-service", level="ERROR"}
```

deploy에서는 **파일 로그를 쓰지 않습니다.** 예전에는 같은 한 줄이 stdout + `FILE` +
`ERROR_FILE` + Loki 청크로 네 번 기록됐고, 파일 어펜더에 용량 상한이 없었습니다.
지금은 stdout 한 곳으로만 나가고 Loki가 14일 보관합니다. 파일 로그는 develop 프로파일에만
남아 있습니다.

컨테이너 로그는 Docker json-file 드라이버로 **컨테이너당 20MB × 3개(최대 60MB)** 상한이
걸려 있습니다. 이 설정이 없으면 로그가 무한히 커져 EBS를 채우고, 디스크가 차면 MySQL과
Kafka가 쓰기에 실패한 뒤 `restart: unless-stopped` 때문에 재시작 루프에 빠집니다.

---

## 5. 배포 후 검증

```bash
# 1. 컨테이너 상태
docker compose -f docker-compose.deploy.yml ps

# 2. 앱 헬스체크 (compose healthcheck 가 이미 돌고 있음)
docker inspect --format='{{.Name}} {{.State.Health.Status}}' \
  $(docker ps -q --filter "name=pickbit-deploy-") | sort
```

3. **Consul 등록** — 터널 후 `localhost:28500` 에서 8개 서비스가 전부 passing
4. **공개 조회가 비로그인으로 열리는지** (빈 홈페이지 방지)
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://api.pickbit.co.kr/api/products   # 200
   ```
5. **내부 API가 막혔는지**
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://api.pickbit.co.kr/api/internal/products/1   # 403
   ```
6. **라우팅 테이블이 안 새는지**
   ```bash
   curl -s -o /dev/null -w '%{http_code}\n' https://api.pickbit.co.kr/actuator/gateway/routes   # 404
   ```
7. **Kafka/CDC 왕복** — 회원가입 1건을 만든 뒤
   ```bash
   docker exec pickbit-deploy-kafka /opt/kafka/bin/kafka-console-consumer.sh \
     --bootstrap-server kafka:9092 --topic AuthAccount-topic --from-beginning --max-messages 1
   ```
   이벤트가 보이고 user-service DB에 사용자 row가 생겼는지 확인
8. **핵심 루프 수동 E2E** — 회원가입 → 상품 등록(이미지 업로드 후 NCP 스토리지 확인) →
   경매 생성 → 다른 계정으로 입찰(실시간 반영 확인) → 낙찰 → Toss 테스트 결제 →
   에스크로 → 구매확정 → `settlement` 테이블에 PENDING row 생성 확인
9. **배치** — `SETTLEMENT_BATCH_CRON` 주기(기본 10분) 후 settlement가 COMPLETED로 전이하는지
10. **재기동 내성** — EC2 stop → start 후 손대지 않고 스택이 복구되고 MySQL 데이터가 남아
    있는지. **"필요할 때만 켜기" 운영의 핵심 검증입니다.**
11. **HTTPS** — `https://pickbit.co.kr`, `https://api.pickbit.co.kr` 인증서 유효,
    `www` → apex 리다이렉트

---

## 6. 메모리 예산

m7g.xlarge 16 GB 기준. 모든 컨테이너에 `mem_limit` 이 걸려 있어 커널 OOM killer 가 임의의
컨테이너를 고르는 일을 막습니다. 트래픽이 몰리는 서비스와 그렇지 않은 서비스를 구분해
배분했습니다.

| 컨테이너 | 상한 |
|---|---|
| gateway / product / auction (트래픽 집중) | 900m × 3 |
| payment | 768m |
| auth / user | 640m × 2 |
| notification / file | 512m × 2 |
| MySQL (`innodb-buffer-pool=1G`) | 2048m |
| Redis (경매 상태 + 입찰 스트림 포함) | 1200m |
| Kafka / Kafka Connect (각 `Xmx512m`) | 900m × 2 |
| Loki / Grafana / Alloy | 320m / 320m / 256m |
| 프론트 (Next standalone) | 400m |
| Consul / Caddy | 256m / 128m |
| **합계** | **약 12.5 GB** |

auth-service 가 640m 로 줄어든 것은 게이트웨이가 JWT 를 직접 검증하게 되면서 인증 트래픽이
더 이상 auth-service 를 거치지 않기 때문입니다.

이미지 빌드는 EC2가 아니라 GitHub Actions에서 하므로 빌드가 운영 중인 스택의 메모리를
잡아먹지 않습니다.

---

## 7. 자동 정리

디스크를 채울 수 있는 항목은 전부 상한이 걸려 있습니다.

| 대상 | 방식 | 보관 |
|---|---|---|
| 컨테이너 로그 | Docker json-file 로테이션 | 20MB × 3 (컨테이너당) |
| Loki 청크 | compactor retention | 14일 |
| Kafka 로그 | 시간 + 용량 상한 | 168h / 파티션당 2GB |
| MySQL binlog | `binlog-expire-logs-seconds` | 3일 |
| `out_box_event` | 보관 기간 스케줄러 | 7일 |
| `inbox` | 보관 기간 스케줄러 | 30일 |
| `auction_event` | 보관 기간 스케줄러 | 30일 |
| `pg_webhook_log` | 보관 기간 스케줄러 | 90일 |
| 도커 이미지 | 배포 시 `prune -af --filter until=168h` | 7일 |

테이블 정리는 매일 04:30 에 돌며 청크(기본 1000행)로 나눠 지웁니다. 한 번에 지우면 락이
오래 잡히고 binlog 가 한꺼번에 불어나 Debezium 에 부담을 주기 때문입니다.
대상과 기간은 각 서비스 `application.yml` 의 `maintenance.retention.targets` 에 있습니다.

> **아웃박스 보관 기간(7일)은 binlog 보관 기간(3일)보다 길어야 합니다.** Debezium 이 아직
> 읽지 않은 행을 지우면 이벤트가 유실됩니다.

정리 로그 확인:
```logql
{container=~"pickbit-deploy-.*-service"} |= "보관 기간 정리"
```

---

## 8. 자주 겪는 문제

**전 서비스가 `SchemaManagementException`으로 죽는다**
→ 3장 스키마 부트스트랩을 안 했습니다. 빈 DB에 `validate`를 건 상태입니다.

**`SettlementBatchScheduler`가 매번 실패한다**
→ Spring Batch 메타 테이블이 없습니다. `BATCH_JDBC_INITIALIZE_SCHEMA=always`로 1회 기동.

**게이트웨이는 떴는데 라우팅이 404**
→ Consul에 서비스가 등록되기까지 몇 초 걸립니다. `ConsulRouteDefinitionLocator`가
HeartbeatEvent에 반응해 라우트를 갱신하므로 잠시 기다리거나 Consul UI에서 등록 상태 확인.

**Kafka 토픽에 이벤트가 안 올라온다**
→ Debezium 커넥터 상태 확인:
```bash
docker exec pickbit-deploy-kafka-connect curl -s localhost:8083/connectors?expand=status
```
아웃박스 테이블이 생기기 전에 등록됐다면 커넥터를 지우고 다시 등록하세요.

**Caddy가 인증서를 못 받는다**
→ DNS가 Elastic IP를 가리키는지, 보안그룹 80/443이 열렸는지 확인. 반복 실패 시
Let's Encrypt 한도에 걸렸을 수 있으니 몇 시간 뒤 재시도.

**stop/start 후 도메인이 안 열린다**
→ Elastic IP를 안 붙였습니다. 퍼블릭 IP가 바뀐 상태입니다.

**CI 배포가 매번 건너뛴다**
→ 인스턴스가 `running` 이 아니거나 `EC2_INSTANCE_ID` 가 틀렸습니다. Actions 로그에
"인스턴스가 running 이 아니라 배포를 건너뜁니다" 경고가 남습니다. 켠 뒤 워크플로를 수동 실행하세요.

**`Credentials could not be loaded` / `Not authorized to perform sts:AssumeRoleWithWebIdentity`**
→ OIDC 신뢰 정책의 `sub` 가 레포와 다릅니다. `repo:<OWNER>/<REPO>:*` 형식이 정확한지 확인하세요.
IAM 에 OIDC 공급자가 등록되지 않은 경우에도 같은 오류가 납니다.

**AWS 호출이 리전 없이 실패한다**
→ `AWS_REGION` 을 Secrets 에 넣었습니다. **Variables** 탭이어야 합니다 (`vars.AWS_REGION`).

**SSM `InvalidInstanceId`**
→ 인스턴스 프로파일에 `AmazonSSMManagedInstanceCore` 가 없거나 443 아웃바운드가 막혔습니다.
`aws ssm describe-instance-information` 으로 `PingStatus` 를 먼저 확인하세요.
보안그룹 **인바운드**와는 무관합니다.

**frontend 이미지를 pull 하지 못한다**
→ 프론트 레포 CI 가 아직 안 돌았거나, GHCR 패키지가 private 인데 인스턴스에서
`docker login ghcr.io` 를 안 했습니다. caddy 가 frontend 에 의존하므로 HTTPS 진입이 함께 막힙니다.
