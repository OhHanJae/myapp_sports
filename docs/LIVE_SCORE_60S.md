# MATCHDAY 무료 API 기반 약 60초 스코어 갱신 — Worker v4

## 목표 및 동작 범위

- **Cloudflare Worker**: `/feed.json`(전체 일정·순위·대진, 기존 5분 캐시)과 `/live.json`(경기 상태·점수만, **55초 캐시**)을 분리합니다.
- **휴대폰 앱이 화면에 표시되는 동안**: 60초 간격으로 `/live.json`을 조회해 표시·로컬 캐시·갤럭시워치 Data Layer를 갱신합니다.
- **휴대폰 백그라운드**: WorkManager 최소 15분 주기(정확한 실행 시점 보장 안 됨), 60초 갱신 불가.
- **워치 타일**: 60초 freshness 요청 + 폰에서 새 데이터를 받으면 즉시 갱신 요청. **Wear OS는 타일 갱신을 지연/제한할 수 있음**. 휴대폰을 종료한 채로 항상 1분 갱신되지는 않습니다.
- 서버 요청 주기를 1분으로 줄였다고 해서 실제 중계와 같은 초 단위 점수를 받을 수 있는 것은 아닙니다. **데이터 원본 반영 시각/게임 지원 범위에 따라 지연**됩니다.

## 구현한 데이터

| 종목 | 무료 데이터 | 제한 |
|---|---|---|
| KBO | 공식 KBO 포스트시즌/정규시즌 월간 일정, 확인 가능한 스코어보드 경기 점수, 이닝이 명확한 경우 이닝 수 | KBO 게임센터에서 진행 중 스코어보드가 비어 있거나 HTTP 200에 코드 200 오류를 반환할 수 있음. 이 경우 0:0을 지어내지 않고 상태 미확인/기존 결과 유지 |
| LoL | PandaScore `/lol/matches/running`, `/lol/matches/past`의 경기 상태와 제공된 세트 승수 | 무료 Fixtures 플랜은 경기 중 킬/골드/드래곤 등 실시간 인게임 데이터 미포함. 세트 승수도 제공업체 반영 지연 가능 |

PandaScore 무료 요금제는 시간당 1,000 REST 요청(2026년 11월 기준). 이 Worker의 실시간 엔드포인트는 일반적으로 PandaScore API 2번 호출하며, Cloudflare 캐시를 공유합니다. 정적 전체 토너먼트 API를 매분 조회하지 않아 호출량을 줄입니다. 사용량 제한 초과를 방지하기 위해 Cloudflare/PandaScore의 실제 대시보드도 확인하세요.

## 사용자 적용 (Cloudflare 웹 편집기만)

1. GitHub `master`에서 `cloudflare-worker/src.mjs` 파일 → **Raw** → 전체 복사.
2. Cloudflare Dashboard → **Workers & Pages → matchday-pandascore-feed → Edit code** → 붙여넣기 → **Deploy**.
3. `https://matchday-pandascore-feed.kungsman3356.workers.dev/health`의 `"version":4` 확인.
4. 새 실시간 주소는 **기존 접근 키**를 재사용합니다.
   ```text
   https://matchday-pandascore-feed.kungsman3356.workers.dev/live.json?key=본인_FEED_ACCESS_KEY
   ```
   JSON `schemaVersion:1`, `games`가 보이면 응답 정상. 현재 경기 중이 없으면 빈 배열이어도 정상.
5. 휴대폰 Android Studio에서 `git pull origin master` 후 `phone` APK 다시 설치. 워치도 `wear` APK 재설치.
6. 휴대폰 앱의 기존 설정 `/feed.json?key=...`은 **변경하지 않습니다**. 새 앱이 자동으로 동일한 Worker의 `/live.json`을 파생하여 조회합니다.
7. 휴대폰 앱 화면에서 '실시간 스코어 · 약 60초 자동 조회' 및 최근 조회 시각 확인. 워치 타일은 폰 동기화 후 갱신.

## API 응답 예제 (설명용, 실제 결과 아님)

```json
{
  "schemaVersion":1,
  "updatedAt":"2026-11-01T16:16:00+09:00",
  "teams":[],
  "games":[
    {
      "id":"NPS_20261101HTSS0",
      "competition":"KBO",
      "dateTime":"2026-11-01T14:00:00+09:00",
      "home":"SAM","away":"KIA",
      "status":"LIVE","homeScore":4,"awayScore":3,
      "inning":"3회","checkedAt":"2026-11-01T16:16:00+09:00"
    }
  ],
  "diagnostics":{"counts":{"KBO":1,"LOL":0},"warnings":[]}
}
```

위 예시는 테스트 가상 점수임. 실제 경기 점수를 주장하는 내용이 아닙니다.

## 실패 분석

- `/health` 버전이 3이라면 Worker v4 배포가 필요합니다.
- `/live.json` HTTP 403이면 기존 `FEED_ACCESS_KEY` 불일치입니다. PandaScore 토큰을 쓰면 안 됩니다.
- `games: []`는 오늘 점수 조회 대상이 없거나 무료 데이터 제공처가 아직 실시간 상태를 노출하지 않은 경우입니다.
- `games`는 존재하지만 `homeScore`/`awayScore`가 `null`이면 점수를 제공처에서 **확인하지 못한 것**입니다. 앱에서 가짜 점수를 생성하지 않습니다.
- 화면 갱신이 안 되면 **폰 화면이 실행 중인지**, Worker v4 여부, 워치 Data Layer 연결 및 기기 절전 제한을 확인하세요.
- API 토큰·접근 키·키가 포함된 URL은 GitHub/캡처에 공유하지 마세요.

## 구현 검증

```bash
node --test cloudflare-worker/test/*.test.mjs
./gradlew :core:smokeTest :phone:assembleDebug :wear:assembleDebug
```

Android 자체 폰/워치 하드웨어에서 초단위 동기화 보장은 하지 않으며 실기기 수동 검증이 별도로 필요합니다.
