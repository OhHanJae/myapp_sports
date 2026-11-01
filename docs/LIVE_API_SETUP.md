# MATCHDAY 실제 경기 데이터 연결 (Cloudflare Worker + PandaScore)

## 1. 데이터 흐름

PandaScore -> 개인 Cloudflare Worker -> HTTPS JSON 피드 -> phone 앱 (캐시) -> Wear OS Data Layer -> watch 앱/타일.

**PandaScore 비밀 토큰은 Android 코드·GitHub·URL 어디에도 넣지 마세요.** Cloudflare Worker의 `PANDASCORE_TOKEN` Secret으로만 보관합니다.

## 2. Worker 점검

Cloudflare Dashboard > Workers & Pages > 배포한 MATCHDAY Worker > Settings > Variables and Secrets에서 다음 2개가 Secret인지 확인하세요.

- `PANDASCORE_TOKEN`: PandaScore에서 발급한 API 토큰
- `FEED_ACCESS_KEY`: PandaScore 토큰과 다른 별도의 긴 임의 문자열

브라우저에서 `https://<worker-subdomain>.workers.dev/health`를 열어 `ok: true`, `keyConfigured: true`인지 확인합니다. 이것만으로 PandaScore 응답 성공까지 보장되지는 않습니다.

그다음 **개인 환경에서만** `https://<worker-subdomain>.workers.dev/feed.json?key=<FEED_ACCESS_KEY>`를 열어 `schemaVersion: 1`, `isDemo: false`, `games`, `standings`, `series` 필드를 확인합니다. 링크를 공개 채팅/이슈/PR에 붙이지 마세요. 접근 키가 브라우저 기록 등에 남으므로 공유 시 키를 교체하세요.

## 3. Android 연결

1. Android Studio에서 `phone` 앱을 설치하고 실행합니다.
2. 오른쪽 상단 톱니바퀴 **설정 > 데이터 동기화**에서 위의 전체 HTTPS `feed.json?key=...` 주소 입력.
3. **피드 저장 및 새로고침** 누르기. 인터넷 연결이 있으면 JSON을 검증하고 저장합니다.
4. '데이터 갱신 완료'와 데모 경고가 사라졌는지 확인한 뒤 **LoL > LCK / MSI / EWC / 롤드컵 / FST** 화면을 확인합니다.
5. 동일 계정/서명으로 연결된 워치의 MATCHDAY 앱 또는 타일을 열면 최신 동기화 데이터를 확인할 수 있습니다.

백그라운드 갱신은 WorkManager의 **30분 주기(최소 보장 X)**, 화면 상단 새로고침은 즉시 호출합니다. 워치 타일 갱신 시각 또한 OS에 의해 지연될 수 있습니다.

## 4. 데이터가 없는 경우

- **KBO**: PandaScore가 제공하지 않습니다. 별도 KBO 공급원 연결 전에는 가짜 점수/순위를 생성하면 안 됩니다. 해당 종목에 경기/순위가 없다고 표시되는 것이 정상입니다.
- **LCK·국제대회**: 대회/시즌/유료 플랜/토너먼트 구조에 따라 standings/brackets가 빈 배열일 수 있습니다. 경기 일정 성공이 순위 및 대진 제공을 보장하지 않습니다.
- **실시간**: 점수는 Worker 캐시(보통 5분)와 API 제공 시간, 폰·워치 동기화 지연이 있습니다. 초단위 실시간 중계용이 아닙니다.
- **오류/오프라인**: 네트워크·인증 실패 시 앱은 마지막 정상 저장 데이터를 유지합니다. 새 피드가 성공하기 전에는 데모 경고를 해제하지 않습니다.
- **로고**: LoL 팀은 제공업체의 HTTPS 이미지 URL을 사용할 수 있습니다. 이미지를 사용할 권리와 제공 정책을 확인하세요.

## 5. 보안과 개인정보

- 저장소에 Worker 접근 키/토큰, `.dev.vars`, 로컬 속성값을 절대 커밋하지 마세요.
- 지금 앱의 전체 피드 URL(`?key=...`)은 **기기 SharedPreferences에 저장**됩니다. 개인 개발 테스트에는 이용 가능하지만 생산 배포 전에는 Android Keystore 기반 암호화 저장과 URL 경로/쿼리 로깅 금지, 키 회전 정책을 적용하세요.
- 공개 저장소에는 `https://YOUR-WORKER.workers.dev/feed.json?key=YOUR-PRIVATE-KEY` 형태의 예시만 남깁니다.

## 6. 문제 해결

- **403**: `FEED_ACCESS_KEY` 불일치 (PandaScore 토큰을 넣으면 안 됨)
- **502**: Worker -> PandaScore 연결 실패, Cloudflare Worker Logs 확인
- **JSON 파싱 실패**: `schemaVersion`이 1인지, 상대 팀/대진에 없는 ID가 없는지 점검
- **실제 데이터인데 빈 순위/대진**: 해당 대회의 PandaScore standings/brackets endpoint 지원 여부 확인
- **워치가 이전 결과 표시**: 휴대폰을 먼저 새로고침하고 동일 앱 ID·서명/페어링, Wear Data Layer 연결 확인

⚠️ 이 저장소의 기존 스크린샷·예제 JSON은 데모용입니다. 실제 경기 결과와 무관합니다.
