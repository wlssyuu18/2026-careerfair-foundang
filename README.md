# 찾았당 · 청년 생활자원 예약 에이전트


```text
hckt/
├── frontend/src/
│   ├── pages/          시작하기·홈/예약 흐름·예약내역/계정
│   ├── App.tsx         로그인 상태·화면 이동·작업 폴링
│   ├── api.ts          세션·CSRF를 포함한 서버 통신
│   ├── components.tsx  공통 UI
│   ├── logic.ts        날짜·비밀번호·링크 검사
│   └── styles.css      Notion 색상과 모바일/데스크톱 레이아웃
├── backend/src/
│   ├── main/java/kr/hckt/  인증·계정·작업·Ollama·브라우저 실행
│   ├── main/resources/    설정·DB 스키마
│   └── test/              예약 경계 검사
├── compose.yaml       로컬 PostgreSQL
└── .env.example       로컬 설정 예시
```

## 실행

Node 24, JDK 21, Docker Desktop, [Windows용 Ollama](https://ollama.com/download/windows)가 필요하다. 모든 서버는 같은 PC에서 실행한다. 공간 검색과 지원하는 예약 양식 처리는 화면을 띄우지 않는 Chromium에서 수행한다.

프로젝트 루트에서 `.env.example`을 `.env`로 복사하고 `DB_PASSWORD`를 변경한다.

```powershell
cd C:\Users\User\workspace\hckt
Copy-Item .env.example .env
docker compose up -d db
ollama pull qwen3.5:4b
```

Ollama 앱을 실행해 서버를 켠다.
모델 다운로드는 약 3.3~4GB이며 실행 메모리는 별도로 필요하다.
메모리 부족 시 모델을 임의로 바꾸지 말고 PC 사양에 맞게 다시 선택한다.

백엔드 터미널:

```powershell
cd C:\Users\User\workspace\hckt\backend
.\gradlew.bat installBrowser
.\gradlew.bat bootRun
```

프론트 터미널:

```powershell
cd C:\Users\User\workspace\hckt\frontend
npm install
npm run dev
```

표시된 Vite 주소(기본 `http://127.0.0.1:5173`)에서 회원가입한다. Vite가 `/api`를 로컬 Spring 서버로 전달한다. Spring은 `backend`에서 실행할 때 `../.env`를 읽는다.

## 구현한 흐름

- 실제 이메일·비밀번호 계정, BCrypt, HttpOnly 세션, CSRF, 로그인 실패 잠금, 사용자별 작업 조회.
- 온보딩·예약 조건·생년월일·로그인·홈·캡처 인식 실패/완료·조건 수정·추천·승인·예약내역·계정·로그아웃 확인.
- 명확한 목적·요일·시간·금액을 포함한 짧은 텍스트는 모델 호출 없이 정리한다. 복잡한 문장과 이미지만 로컬 모델로 분석하며 입력 분석의 응답 길이·문맥 크기·대기 시간을 제한한다. 캡처 원본은 DB나 파일에 저장하지 않고, 링크·회의 ID·암호·연락처를 추출 결과에서 마스킹한다. 원본 이미지 픽셀 자체를 수정하는 기능은 없다.
- 입력 → 별도 분석 화면 → 내용 확인으로 이동한다. 금요일·다음 주는 한국 날짜 기준으로 계산하고, 만원은 10,000원으로 변환한다. 요청의 보유 장비는 추가 대여 장비와 분리한다. 종료 시각이 없으면 직접 선택하도록 남긴다.
- 시간 선택은 앱 색상에 맞춘 선택 창을 사용한다. 홈의 공공 포털 표시는 탐색 대상을 안내하며 외부 이동 링크가 아니다. 사용자는 앱 안에서 조건·후보·최종 승인을 확인한다.
- Ollama가 현재 DOM 관찰을 바탕으로 이동·클릭·입력·선택 도구를 결정한다. 고정 데모 응답이나 기관 자원 조회 API를 사용하지 않는다.
- 공유누리 검색 화면에서 시작하고, 조회 실패·검색 완료·검색 단계 인증 요구가 발생하면 서울시 포털도 확인한다. 실제 브라우저는 백그라운드에서 실행한다. 후보는 방문한 상세 화면에서 이름·비용·장비·자격 근거를 확인한 경우만 저장한다. 실시간 가능 여부와 이용 자격 충족을 임의로 확정하지 않는다.
- 사용자 승인 전에는 쓰기 요청을 막는다. 공유누리·서울시 페이지에서 확인한 검색·지역 선택·예약 달력 조회용 POST만 주소별로 별도 허용한다. 제출 전 일시·총액·장비·취소 조건과 양식 지문을 확인한다.
- 사용자는 조건 확인 → 공간 검색 목록 → 공간 선택 → 최종 예약 승인 → 결과 확인 순서로 이용한다. 브라우저 창과 도구 실행 로그, 직접 조작 버튼은 표시하지 않는다. 표준 POST 양식만 최종 승인 후 한 번 제출한다. 외부 로그인·CAPTCHA·파일 첨부·별도 결제·비표준 AJAX 예약이 필요한 시설은 앱 예약 미지원으로 표시하고 다른 공간을 선택하게 한다. 이 시설들을 승인 한 번으로 예약하려면 별도의 인증·예약 연동이 필요하다.
- 신청 전 매진은 같은 조건으로 대안을 다시 탐색한다. 제출 이후 실패·응답 단절은 중복 신청 방지를 위해 결과 확인 필요로 남긴다. 새 공간·금액은 새 승인을 받는다.
- 예약번호와 상태 원문을 확인하여 접수·기관 승인 대기·확정을 구분한다. 앱 기록 삭제는 외부 예약 취소가 아니다.

## 검사

```powershell
# frontend
npm test
npm run build

# backend
.\gradlew.bat test
```

이번 프론트 수정은 사용자가 제공한 01~10 화면 PNG를 기준으로 온보딩·홈·촬영·추출 결과·추천·승인 하단 창·마이페이지 구조를 맞췄다. 서비스명은 '찾았당', 예산 입력은 숫자+만원을 유지한다. 원본 Figma SVG 26개와 Noto Sans KR 폰트는 public/figma에 보관하며 폰트 라이선스도 포함했다. 온보딩 안내문은 작동 방식 예시이고, 예약 화면의 이름·가격·일정·상태는 서버 데이터로 표시한다.

검증: 프론트 테스트 9개, 타입 검사와 프로덕션 빌드 통과. 로컬 Chromium에서 10개 화면을 캡처하고 이미지 분석 단계 전환, 후보 필터, 예산 저장, 최종 승인 전 제출 방지와 승인 1회 호출을 테스트 응답으로 확인했다. 실제 예약 사이트에 신청을 제출한 검증은 아니다. 이번 디자인 수정에서 백엔드는 변경하지 않았다.

휴대폰 등록·30분 전 푸시 알림·GPS는 연결하지 않았다. 지역은 전국 법정동 목록을 검색한 뒤 전체 지역명을 선택한다. 이미지를 서버에 보관하지 않는 정책과 앱 내 예약 일정 표시는 현재 기능에 맞게 표시한다. 시설별 자격을 확인했다고 가정하거나, 원문에 없는 준비 시간·거리·예약 가능 여부를 만들어 표시하지 않는다. 촬영하기는 카메라 권한을 받아 실시간 영상을 표시하고, 셔터를 누르면 한 장을 캡처해 바로 분석한다. 카메라 사용에는 HTTPS 또는 localhost가 필요하며, 촬영 화면을 닫거나 분석 화면으로 이동하면 카메라를 해제한다. 앨범·파일 선택은 촬영 화면을 거치지 않고 바로 분석한다. 실시간 영상 OCR과 플래시는 지원하지 않는다.

### 지역 검색 자료

`frontend/src/region-data.ts`는 [더주소 법정동 코드 목록](https://thejuso.kr/download?dataset=codes&family=jurisdiction)의 2026-10-30 행정안전부 자료에서 시도·시군구·읍면동 명칭을 합친 5,351개 지역이다. 외부 검색 요청이나 API 키 없이 검색한다. 행정구역 개편 시 같은 자료의 최신 JSON에서 지역명을 다시 생성해야 한다. 기존 저장 지역은 자동 변환하지 않으며 지역 수정 시 목록에서 다시 선택한다.

### 달력과 이미지 분석

예약일과 생년월일 설정은 앱 전용 달력을 사용한다. 월 이동·연도 선택·방향키 이동과 날짜 범위 제한을 지원한다.

Windows에서 한국어 OCR을 먼저 실행해 연도가 포함된 일정 한 개와 시작·종료 시간을 명확하게 읽으면 이미지 모델 호출을 생략한다. 휴대폰 상태 표시줄 시각은 일정 시작 시각으로 사용하지 않는다. 날짜가 여러 개이거나 항목을 읽지 못했거나 Windows 한국어 OCR이 없으면 기존 이미지 모델로 분석한다. 원본과 인식 텍스트를 파일에 저장하지 않는다. OCR은 최대 6초 후 기존 경로로 넘긴다. 테스트용 선명한 안내문의 OCR 단계는 이 PC에서 약 0.5초였으며, 실제 이미지 전체 처리 시간은 글자 크기와 화질에 따라 달라진다. 백엔드 코드 적용에는 실행 중인 `bootRun` 재시작이 필요하다.

### 전국 공간·물품 카탈로그 DB

`backend/src/main/resources/schema.sql`에 전국 공공·민간 공간과 대여 물품을 저장하는 구조가 있다. 기존 회원과 사용자별 작업·예약 기록은 유지한다. 스키마는 `bootRun` 시작 시에도 적용되며 반복 실행할 수 있다.

| 테이블/뷰 | 저장하는 정보 |
| --- | --- |
| `resource_source` | 제공처와 조회·예약 연동 방식, 활성화 여부, 마지막 동기화 시각 |
| `resource` | 실제 공간/물품, 공공/민간 구분, 지역·주소·좌표, 장비, 공간 수용 인원 |
| `resource_listing` | 제공처별 상품 ID·원문·예약 링크, 요금과 단위, 재고, 자격·취소 조건·수집 시각 |
| `resource_availability` | 특정 상품의 정확한 시작·종료 시각·요청 수량에 대한 조회 결과, 잔여 수량·총액·근거·유효기간 |
| `current_resource_availability` | 만료·비활성·조회 미지원 제공처의 결과를 `UNKNOWN`으로 바꿔 보여주는 조회용 뷰 |

공간은 `kind=SPACE`, 대여 물품은 `kind=ITEM`이다. 운영 구분은 `PUBLIC`, `PRIVATE`, `UNKNOWN`이며 제공처 이름만으로 운영 주체를 추정하지 않는다. 예를 들어 면접실·회의실·스터디룸과 노트북·웹캠·마이크·면접 정장을 각각 `category`로 구분할 수 있다. `sido/sigungu/eupmyeondong`은 수집 시 전체 지역명으로 정규화하고, 확인한 법정동 코드가 있으면 10자리 `region_code`도 넣는다. 세종처럼 시군구가 없는 지역은 빈 문자열을 쓴다.

같은 실제 자원의 여러 제공처 상품은 `resource_id`로 연결한다. 동일 제공처의 `external_id`는 중복 등록할 수 없다. 서로 다른 상품을 이름이 비슷하다는 이유만으로 같은 자원에 자동 병합하지 않는다.

요금을 모르면 `NULL`을 저장한다. `0`은 확인한 무료 요금이다. `price_unit`은 `HOUR/DAY/BOOKING/ITEM/UNKNOWN`으로 구분하고 원문의 요금 문구도 남긴다. 시간·수량·추가 요금이 다르면 단순 금액 비교로 전체 예산 충족을 단정하지 않는다. 해당 요청의 실제 총액은 잔여 조회 시 `total_price`에 따로 기록한다.

공유누리·서울시 공공서비스예약·네이버 예약은 **제공처 등록 정보만** 들어 있다. 기본값은 비활성·조회/예약 미연동이다. 실제 전국 시설·업체·재고, API 인증키, 실시간 잔여 정보는 포함하지 않는다. 인증키는 서버 환경 설정에서 관리하고 이 테이블에 넣지 않는다. 확인된 연동을 구현한 뒤에만 해당 제공처를 활성화한다. 현재 화면의 검색을 DB 검색으로 전환하거나 수집 작업을 실행하는 기능은 이 DB 구성에 포함하지 않는다.

사용자 검색에서는 지역·종류·장비 인덱스로 후보를 추린다. 아래처럼 **정확한 요청 구간과 수량**에 해당하는 잔여 조회만 합친다. `:...`은 JDBC 바인딩 매개변수다.

```sql
SELECT r.id, r.name, r.kind, r.sector, r.address, r.equipment,
       l.id AS listing_id, l.source_code, l.source_url, l.booking_url,
       l.price_amount, l.price_unit, l.price_quote, l.fetched_at,
       s.availability_method, s.booking_method,
       COALESCE(a.status, 'UNKNOWN') AS availability,
       a.checked_at, a.expires_at,
       CASE WHEN a.status = 'AVAILABLE' THEN a.remaining_quantity END AS remaining_quantity,
       CASE WHEN a.status = 'AVAILABLE' THEN a.total_price END AS total_price
FROM resource r
JOIN resource_listing l ON l.resource_id = r.id AND l.active
JOIN resource_source s ON s.code = l.source_code AND s.enabled
LEFT JOIN current_resource_availability a
  ON a.listing_id = l.id
 AND a.starts_at = :starts_at AND a.ends_at = :ends_at
 AND a.requested_quantity = :quantity
WHERE r.active AND r.kind = :kind AND r.sido = :sido
  AND (:sigungu = '' OR r.sigungu = :sigungu)
  AND (:sector = 'ALL' OR r.sector = :sector)
  AND r.equipment @> CAST(:equipment AS TEXT[])
  AND l.booking_channel = 'ONLINE'
ORDER BY r.id, l.source_code
LIMIT 30;
```

같은 자원의 여러 상품은 `resource_id` 단위로 묶어서 표시한다.

잔여 상태는 `AVAILABLE/UNAVAILABLE/UNKNOWN/ERROR`이다. `AVAILABLE`에는 원문 근거와 만료 시각이 필요하다. 시간은 `TIMESTAMPTZ`에 저장하고 화면에서는 한국 시간으로 표시한다. 조회 실패 시 이전의 `AVAILABLE`을 재사용하지 않고 `ERROR/UNKNOWN`으로 덮어쓴다. 같은 요청 결과를 갱신할 때는 다음 조건을 사용해 늦게 도착한 이전 조회가 최신 결과를 덮지 않게 한다.

```sql
INSERT INTO resource_availability
  (listing_id, starts_at, ends_at, requested_quantity, status,
   remaining_quantity, total_price, evidence, failure_reason, checked_at, expires_at)
VALUES
  (:listing_id, :starts_at, :ends_at, :quantity, :status,
   :remaining_quantity, :total_price, :evidence, :failure_reason, :checked_at, :expires_at)
ON CONFLICT (listing_id, starts_at, ends_at, requested_quantity) DO UPDATE SET
  status = EXCLUDED.status, remaining_quantity = EXCLUDED.remaining_quantity,
  total_price = EXCLUDED.total_price, evidence = EXCLUDED.evidence,
  failure_reason = EXCLUDED.failure_reason, checked_at = EXCLUDED.checked_at,
  expires_at = EXCLUDED.expires_at
WHERE resource_availability.checked_at <= EXCLUDED.checked_at;
```

이 캐시는 재고를 확보하거나 외부 예약을 생성하지 않는다. 최종 승인 직전에 다시 조회하고, 실제 예약 서버의 접수 결과·예약번호로 결과를 확정해야 한다. 예약 연동 미지원·전화/방문 전용 상품은 앱에서 자동 예약 가능으로 표시하지 않는다.

실제 PostgreSQL 통합 검사는 `HCKT_TEST_DB=true`를 설정한 뒤 `backend`에서 `gradlew.bat test --offline`로 실행한다. 테스트용 자원·상품·잔여 정보는 롤백되며 실재 시설로 등록되지 않는다.
