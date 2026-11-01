# KBO 포스트시즌 실데이터 연동 (Worker v3)

## 수정한 이유
기존 Worker v2의 네이버 스포츠 비공식 일정 API는 해외 GitHub 빌드 환경에서 기간·하루 단위로 요청해도 타임아웃이 발생했습니다.
2026-11-01 GitHub Actions에서 KBO **공식 홈페이지 일정 API**를 조회한 결과, 2026년 10월 17행 및 11월 11행 응답이 정상 수신됐습니다.
공식 포스트시즌 웹 일정(한국시리즈 포함)을 가져오는 Worker v3로 교체했습니다.

## 설치(Cloudflare 웹만 사용)
1. [KBO/LoL 통합 Worker 소스](../cloudflare-worker/src.mjs)를 GitHub에서 열고 `Raw`를 클릭하여 전체 복사.
2. Cloudflare → Workers & Pages → `matchday-pandascore-feed` → **Edit code** → 기존 코드 대체 → Deploy.
3. `https://matchday-pandascore-feed.kungsman3356.workers.dev/health`에서 `"version":3` 확인.
4. 개인 URL `https://matchday-pandascore-feed.kungsman3356.workers.dev/status?key=<FEED_ACCESS_KEY>`에서 `diagnostics.counts.KBOPostseason` 값을 확인. 값이 0인 경우 `diagnostics.warnings` 확인.
5. 앱 `master` 새 코드로 폰·워치 재설치 후, 기존 피드 URL은 변경 없이 새로고침.

**인증 키를 GitHub·채팅·공유 스크린샷에 넣지 마세요.** 기존 Cloudflare Secret 이름과 값은 그대로 사용합니다.

## 데이터 출처 및 상태
- KBO 정규시즌: 비공식 JSON 저장소 (일정·완료 결과·순위 계산).
- 포스트시즌: [KBO 공식 홈페이지](https://www.koreabaseball.com/Schedule/Schedule.aspx)의 `GetScheduleList` 월별 일정/결과.
- 경기 결과는 공식 일정의 **리뷰 링크 + 실제 점수**가 있는 경기만 `FINAL`로 처리.
- 경기 시작 시간이 지났지만 응답이 0:0이고 리뷰/실시간 근거가 없는 경기는 `UNKNOWN` → **상태 미확인**, 0:0 최종 결과로 표시하지 않음.
- 플레이오프 승리팀이 미정인 한국시리즈는 `TBD`로 표시.
- 서버에서 공식 API를 못 불러오면 **2026-10-31 기준 확인된 경기 결과·향후 일정**을 임시 표시. `/status`의 `sources.KBO`에 snapshot이라고 나오면 실시간 반영이 아님. 새 데이터가 있다고 가장하지 않음.
- 캐시 5분. 2026년 11월 1일 플레이오프 5차전(삼성-KIA)은 공식 일정에 존재하지만 실시간 점수 갱신은 원본 반영 상황과 접근 환경에 따라 다름.

## 검증
GitHub Actions:
`node --test cloudflare-worker/test/*.test.mjs`
`./gradlew :core:smokeTest :phone:assembleDebug :wear:assembleDebug`

테스트는 비정상적인 0:0을 최종 무승부로 처리하지 않는지, 상대 미정(TBD)을 보존하는지 확인합니다.
