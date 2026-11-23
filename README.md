# 찾았당

## 프로젝트 소개

찾았당은 화상면접·온라인 역량검사처럼 혼자 집중할 공간이 필요한 청년을 위한 **공간 대여·예약 에이전트 서비스**입니다. 안내문이나 이용 목적·날짜를 입력하면 AI가 필요한 공간 조건을 파악하고, 지역·예산·장비에 맞는 공간을 찾아 예약을 돕습니다.

## 기획 배경

온라인 면접과 역량검사를 준비할 때는 정해진 시간 동안 혼자 집중할 수 있는 공간이 필요합니다. 집에서는 함께 사는 사람이나 주변 소음으로 방해받기 쉽고, 카페에서는 대화 노출과 인터넷 연결이 신경 쓰입니다.

공공 면접실과 민간 대여 공간의 정보는 여러 사이트에 흩어져 있습니다. 가까운 공간을 찾은 뒤에도 이용 자격과 비용, 제공 장비, 예약 가능한 시간을 하나씩 비교해야 합니다. 찾았당은 **사용 목적과 일정에 맞는 공간을 찾아 예약까지 연결해**, 취업준비생이 응시 준비에 집중할 수 있도록 돕습니다.

## 대표 시나리오

### 화상면접 장소 예약

> “다음 주 금요일 오후 2시에 화상면접이 있어. 면접 안내문에 맞는 1만 원 이내의 근처 방 찾아줘.”

1. **안내문 분석**: 면접 메일 캡처에서 날짜·시간·필요 장비를 추출합니다.
2. **조건 정리**: 이용 구간과 지역·예산·보유 장비를 반영합니다.
3. **공간 탐색**: 조건에 맞는 공간 후보와 이용요금을 비교합니다.
4. **예약 승인**: 선택한 공간과 일정을 확인하고 예약을 신청합니다.
5. **결과 안내**: 예약 상태와 이용 정보를 확인합니다.

## AI 활용

- **이미지·텍스트 이해**: 안내문에서 목적·날짜·시간·장비를 추출합니다.
- **조건 정리**: 분석 결과에 사용자의 지역·예산·보유 장비를 반영합니다.
- **검색 연결**: 분석한 조건을 DB 공간 검색에 전달합니다.

## 공간 데이터와 예약 시연

서비스는 공공·민간 시설의 공간 정보를 통합하고, 요청한 날짜·시간의 예약 가능 여부를 조회한 뒤 사용자 승인을 받아 실제 예약을 신청하는 흐름을 목표로 합니다.

실제 예약에는 기관·업체별 예약 시스템 연동과 사용자 인증, 예약 권한 및 승인 절차가 필요합니다. 이번 시연에서는 공개 시설 정보를 수집·활용하고, 외부 예약 시스템을 대신하는 **DB 기반 가상 예약 환경**을 구현했습니다. 공간별 예약 가능 시간과 중복 신청을 검사하고, 신청·승인 대기·확정·취소까지의 흐름을 체험할 수 있습니다.

| 구분 | 내용 |
| --- | --- |
| 수집 시설 | 11개 시·도의 화상면접 관련 공간 33개, 지역·가격·시설·장비·자격·출처 정보 |
| 서울시 공공서비스예약 | 시설대관 목록·상세 API를 통한 공간 정보 수집과 갱신 |
| 가상 공간 | 전국 시·군·구 301곳의 공간 903개 |
| 예약 방식 | DB 기반 가상 예약, 이용 구간 중복 검사, 승인 대기·확정·취소 처리 |
| 예약 알림 | 신청 후 5~120초 사이 자동 승인, 앱 내 예약 확정 알림 |

## 기대효과

| 대상 | 기대효과 |
| --- | --- |
| 청년·취업준비생 | 공간 탐색·비교·신청 부담을 줄이고 면접과 검사 준비에 집중 |
| 공공기관 | 공공 대여 공간의 접근성을 높이고 이용자와 연결 |
| 민간시설 | 사용 목적과 이용 조건에 맞는 공간 수요와 연결 |

## 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| 프론트엔드 | React 19, TypeScript, Vite |
| 백엔드 | Java 21, Spring Boot 4.2, Spring Security, JDBC |
| 데이터베이스 | PostgreSQL 17, Docker Compose |
| AI·문자 인식 | Ollama (`qwen3.5:4b`), Windows 한국어 OCR |
| 브라우저 자동화 | Playwright |

## 배포 예정 환경

| 구분 | 구성 |
| --- | --- |
| 프론트엔드 | Vercel |
| 백엔드·AI | 상시 가동 macOS 서버, Spring Boot·Ollama |
| 데이터베이스 | macOS 서버 내 Docker PostgreSQL·영구 볼륨 |
| 외부 연결 | ngrok HTTPS 터널 |
| API 연결 | Vercel `/api` → ngrok → Spring Boot |

## 폴더 구조

```text
hckt/
├── frontend/
│   ├── src/
│   │   ├── pages/          시작·검색·예약·마이페이지
│   │   ├── App.tsx         화면 이동과 상태 관리
│   │   ├── api.ts          백엔드 API 통신
│   │   └── components.tsx  공통 UI
│   └── public/             이미지·아이콘·폰트
├── backend/src/
│   ├── main/java/kr/hckt/  인증·요청 분석·검색·예약 처리
│   ├── main/resources/     서버 설정·DB 스키마·OCR
│   └── test/               백엔드 테스트
├── compose.yaml            PostgreSQL 구성
└── .env.example            환경변수 예시
```

## 실행 방법

Windows 로컬 환경 기준으로 Node.js 24, JDK 21, Docker Desktop, Ollama를 사용합니다.

### 1. 환경변수·DB 준비

```powershell
Copy-Item .env.example .env
# .env의 DB_PASSWORD 설정
docker compose up -d db
```

DB 데이터는 Docker named volume `<Compose 프로젝트명>_postgres-data`에 저장됩니다. `docker compose down -v`는 저장 데이터를 삭제합니다.

서울시 시설 정보를 수집하려면 루트 `.env`에 `SEOUL_OPENAPI_KEY`를 설정합니다.

### 2. AI 모델 준비

```powershell
ollama pull qwen3.5:4b
```

### 3. 백엔드 실행

```powershell
cd backend
.\gradlew.bat installBrowser
.\gradlew.bat importCatalog
.\gradlew.bat bootRun
```

### 4. 프론트엔드 실행

별도 터미널에서 실행합니다.

```powershell
cd frontend
npm ci
npm run dev
```

- 프론트엔드: `http://127.0.0.1:5173`
- 백엔드: `http://127.0.0.1:8080`

### macOS 배포 서버

기존 PostgreSQL과 충돌하지 않도록 이 Mac의 배포 DB는 `55432` 포트를 사용합니다. 루트 `.env`에 다음 값을 설정합니다.

```properties
DB_PORT=55432
DB_URL=jdbc:postgresql://127.0.0.1:55432/hckt
COOKIE_SECURE=true
```

`COOKIE_SECURE=true`는 HTTPS 배포용입니다. 로컬 HTTP에서 로그인할 때는 `false`로 설정하고 백엔드를 다시 시작합니다.

```sh
docker compose up -d db
launchctl kickstart "gui/$(id -u)/kr.foundang.ollama"
./tmp/ollama/ollama pull qwen3.5:4b
cd backend
bash gradlew importCatalog
HCKT_TEST_DB=true bash gradlew test bootJar
cd ../frontend
npm ci
npm test
npm run build
```

이 Mac은 Ollama 공식 macOS 배포본을 `tmp/ollama`에 설치하고 `~/Library/LaunchAgents/kr.foundang.ollama.plist`로 실행합니다. Homebrew 0.40.17 배포본은 JSON 응답에 필요한 라이브러리가 빠져 있어 사용하지 않습니다. Ollama 로그는 `tmp/ollama.log`와 `tmp/ollama.error.log`입니다.

백엔드는 `~/Library/LaunchAgents/kr.foundang.backend.plist`에 등록되어 로그인 시 자동 실행되고, 종료되면 다시 시작합니다. 실행 파일은 `tmp/foundang.jar`, 로그는 `tmp/backend.log`와 `tmp/backend.error.log`입니다. 빌드 결과와 실행 파일을 분리하므로 새 빌드는 자동 반영되지 않습니다. 업데이트할 때 프로젝트 루트에서 서비스를 중지하고 JAR를 교체합니다.

```sh
launchctl bootout "gui/$(id -u)/kr.foundang.backend"
cp backend/build/libs/hckt-0.1.0.jar tmp/foundang.jar
launchctl bootstrap "gui/$(id -u)" "$HOME/Library/LaunchAgents/kr.foundang.backend.plist"
```

외부 공개에는 Vercel 로그인과 ngrok 인증이 필요합니다.

```sh
ngrok config add-authtoken <발급받은 토큰>
ngrok http 8080
cd frontend
npx --yes vercel login
```

ngrok의 HTTPS 주소가 발급되면 `frontend/vercel.json`에 `/api/:path*`를 해당 주소의 `/api/:path*`로 전달하는 rewrite를 설정하고 `frontend`에서 `npx --yes vercel --prod`로 배포합니다. [Vercel 외부 API 프록시 설정](https://vercel.com/docs/routing/rewrites)을 따릅니다.

GitHub 자동 배포는 Vercel 프로젝트에 이 저장소를 연결하고 Production Branch를 `main`, Root Directory를 `frontend`로 설정합니다. GitHub 연결 전의 푸시는 자동으로 다시 처리되지 않으므로 연결 후 새 커밋의 배포 상태를 확인합니다.

이 구성은 Mac이 켜져 있고 잠들지 않으며, 사용자 로그인·Docker Desktop·Ollama·백엔드·ngrok이 실행 중이어야 동작합니다.

## 팀원

**팀명: 두리번두리번**

- 제유진: 대표자, 기획 및 개발
- 김희윤: 기획 및 UI/UX 개발
