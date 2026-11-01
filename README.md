# MATCHDAY — KBO + LoL 경기 일정 / 순위 / 토너먼트 (Android + Wear OS)

**Kotlin / Jetpack Compose / Wear Compose / Wear Tiles / Wear Data Layer** 로 작성한 Android Studio 프로젝트입니다.

- **휴대폰**: KBO(파란색) · LoL(초록색), 오늘/일정/대진/순위/설정, 삼성 라이온즈·Gen.G 우선 표시.
- **LoL 대회**: LCK, MSI, EWC, 롤드컵(WORLDS), FST. 대회별로 일정·순위·대진 전환.
- **대진**: KBO 와일드카드 → 준플레이오프 → 플레이오프 → 한국시리즈; LoL 승자조/패자조/결승; 시리즈 승수와 개별 게임 점수 확인.
- **워치**: 오늘/일정/대진/순위를 40mm 화면용 독립 UI로 제공. KBO / LoL **타일 2종** 별도 설치.
- **색/테마**: KBO 블루, LoL 그린. 화이트 / 다크 / 기기 설정(기본).
- **알림**: 기본 OFF, 경기 시작 30분 전/시작 시 선택 가능(폰, WorkManager).
- **로고/팀 이미지**: 26개 팀에 대한 내장 임시 배지 PNG 포함. 실제 공식 로고는 제공되지 않으며 피드 `logoUrl`을 통해 별도 연결 가능.

## 실제 LoL 경기 API (PandaScore + Cloudflare Worker)

앱 설정에서 이미 배포한 **MATCHDAY Cloudflare Worker**의 피드를 등록하면 데모를 실제 LoL 경기 데이터로 교체합니다. PandaScore 토큰은 앱이나 GitHub에 저장하지 않습니다.

1. Worker의 `/health`에서 `keyConfigured: true` 확인.
2. 앱 **설정 → 데이터 동기화**에서 `https://YOUR-WORKER.workers.dev/feed.json?key=YOUR-PRIVATE-ACCESS-KEY` 형식의 개인 피드 URL 입력.
3. **피드 저장 및 새로고침** → **LoL → LCK / MSI / EWC / 롤드컵 / FST** 일정·순위·대진 확인.
4. 폰에 저장된 피드는 Wear OS Data Layer로 워치 앱/타일에 동기화합니다.

**주의**: KBO 데이터는 PandaScore에 포함되지 않습니다. 별도 KBO API가 연결되기 전에는 실제 경기/순위를 보여줄 수 없습니다. 일부 국제대회는 순위/대진 API가 제공되지 않을 수 있습니다. 민감한 접근 키가 포함된 URL을 GitHub나 이슈에 업로드하지 마세요.

웹 배포/오류 대응 및 데이터 제공 범위: [docs/LIVE_API_SETUP.md](docs/LIVE_API_SETUP.md).

> **실제 데이터 v2 업데이트:** 워치 타일은 응원팀 오늘 경기 → 다른 팀 오늘 경기 → 가장 가까운 미래 경기 순으로 선택합니다. Cloudflare Worker에서 KBO 정규시즌 실제 일정/결과/순위, 비공식 경기 게이트웨이가 제공하는 포스트시즌 일정·결과, PandaScore LoL 데이터를 수집하도록 개선했습니다. **Cloudflare 편집기에서 별도 재배포가 필요합니다.** [Worker v2 업데이트 가이드](docs/WORKER_V2_UPDATE.md). KBO 포스트시즌은 비공식 일정 게이트웨이가 반환한 확인 가능한 경기로만 구성합니다. 게이트웨이 응답이 없으면 대진은 미제공으로 표시합니다.

## 시작하기

1. 이 폴더의 `settings.gradle.kts`를 **Android Studio에서 열기**.
2. Gradle Sync 및 Android SDK API 36 설치.
3. `phone` 모듈 선택 → S24 Ultra 등 폰에서 Run / Debug.
4. `wear` 모듈 선택 → Galaxy Watch 연결 후 Run / Debug.
5. 워치 타일 목록에서 `KBO 오늘 경기`, `LoL 오늘 경기` 추가.

Windows PowerShell:

```powershell
.\gradlew.bat :phone:assembleDebug :wear:assembleDebug
```

세부 가이드: [docs/BUILD_AND_DEBUG.md](docs/BUILD_AND_DEBUG.md)

## 데이터가 실제 경기인가요?

**아닙니다. 제공된 예제 피드는 모두 가상 테스트 데이터이며 앱 상단에 DEMO 경고가 나타납니다.** 실제 기록처럼 보이지 않게 표시하였습니다.

- 개발 중에는 오프라인으로 모든 화면을 즉시 눌러 볼 수 있습니다.
- 실시간 피드를 사용하려면 별도 경기 데이터 제공자/백엔드가 [`docs/FEED_FORMAT.md`](docs/FEED_FORMAT.md)의 JSON 계약대로 URL을 제공해야 합니다.
- 폰 앱 설정에서 **HTTPS JSON URL**을 저장하면 요청·파싱·캐시·워치 전송을 실행합니다.
- 공개 API의 지속성/정확성/라이선스 문제 때문에 데이터 제공자가 없는 상태에서 라이브라고 표시하지 않습니다.

## 주요 폴더

```text
Matchday/
├─ core/                       # 데이터 모델, JSON 검증기 및 파서 (순수 JVM Kotlin)
├─ phone/                      # Android Compose 앱, 알림, HTTPS 동기화
├─ wear/                       # Wear OS Compose 앱 + KBO/LoL 타일 2개
├─ feeds/example.json          # 가상 샘플 데이터 및 외부 feed 형식
├─ previews/                   # 디자인 목업 PNG + 브라우저 프리뷰
├─ docs/                       # 빌드/디버깅 가이드 및 데이터 계약
├─ .github/workflows/          # GitHub Actions 디버그 APK 빌드 워크플로
├─ gradlew, gradlew.bat         # 로컬 Gradle 실행 스크립트
└─ settings.gradle.kts         # 3개 모듈을 합친 Android Studio 루트
```

## 개발 및 검증 상태

- 공통 Kotlin 모델/파서 `kotlinc`로 컴파일 및 테스트 피드 검증 완료.
- Android SDK와 Gradle 저장소에 접근할 수 없는 현재 생성 환경에서는 **APK 빌드/실기기 디버깅은 미검증**입니다.
- GitHub에 업로드하면 Actions가 실제 APK 빌드를 시도하도록 설정했습니다. CI 빌드 결과를 확인해야 완전한 빌드 보증이 가능합니다.
- `gradle-wrapper.jar`는 이 환경에서 오프라인으로 컴파일한 경량 다운로드 부트스트랩이며, **공식 Gradle Wrapper JAR이 아닙니다**. 최초 실행 시 Gradle 8.13을 공식 HTTPS 배포처에서 내려받아 실행합니다. 필요하면 SDK가 준비된 PC에서 `gradle wrapper --gradle-version 8.13`으로 교체하세요.

## 저작권/상표

KBO 및 각 구단, LoL 및 각 구단의 명칭·상표권은 각 소유자에게 있습니다. 포함된 임시 배지 이미지는 실제 공식 로고를 복제하지 않는 개발용 그래픽입니다. 사용자가 제공하는 외부 URL의 로고/일정/스코어는 사용 권한을 확인하세요.
