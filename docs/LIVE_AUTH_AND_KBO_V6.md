# MATCHDAY Live 인증과 KBO 경기 점수 (Worker v6)

## 확인된 사용자 응답
2026-11-04 20:21:45 KST의 실제 `/live.json` 응답: 삼성(원정) 1 : KT(홈) 1, **6회말 LIVE**. `diagnostics.counts.KBO=1`, `warnings=[]` 이므로 데이터는 서버까지 정상 도착했습니다.

**`teams:[]`는 실시간 점수 오버레이에서 정상**입니다. 전체 팀 목록은 `/feed.json`에서 받아 보관하며, `/live.json`은 상태와 점수 변경분만 전송합니다. 이번 응답은 인증이 성공한 경우이며, 이전 `{"error":"Forbidden"}`은 별도 HTTP 403 인증 실패입니다.

## 수정 사항
- **Worker v6**: `/feed.json`과 `/live.json`에 동일한 `FEED_ACCESS_KEY` 인증 유지. HTTP 403일 때 안전한 코드 `INVALID_FEED_ACCESS_KEY`와 키 입력 힌트를 함께 반환. Secret은 절대 반환하지 않음.
- **폰**: `/feed.json` URL을 `/live.json`으로 변환할 때 인코딩된 `?key=` 문자열 유지. 잘못된 URL 및 403에 구체적인 오류 안내.
- **KBO**: 공식 HTML에서 경기 ID·진행 상태·점수가 확인되면, 월간 일정 API가 차단돼도 점수를 받음. 실제 시작 시각을 모르면 관측 시각으로 명시하며 가상의 0:0은 생성하지 않음.
- **병합**: KBO 양 팀·날짜가 같은 유일한 경기라면 중계 ID나 관측 시각이 달라도 전체 피드의 기존 일정에 병합. 더블헤더는 잘못 병합하지 않도록 방지.

## 적용
1. GitHub `master`의 <https://github.com/OhHanJae/myapp_sports/blob/master/cloudflare-worker/src.mjs> → Raw → 전체 복사.
2. Cloudflare의 `matchday-pandascore-feed` → Edit code → 교체 → Deploy.
3. <https://matchday-pandascore-feed.kungsman3356.workers.dev/health>에서 `"version":6` 확인.
4. Android Studio에서 `git pull origin master` 후 폰 앱 새로 설치. 워치 앱 코드 변경 없음.
5. 앱 설정에는 **`https://matchday-pandascore-feed.kungsman3356.workers.dev/feed.json?key=본인_FEED_ACCESS_KEY`**만 저장.
6. 브라우저에서 확인할 때만 `/live.json?key=동일한키` 사용. 접근 키가 포함된 URL은 공유 금지.

## 오류 구분
- `/live.json` HTTP 403: 접근 키 누락/불일치. Timeout이 아님.
- `/live.json`의 `teams:[]`와 `games:[...]`: 정상적인 점수 오버레이.
- `/live.json` `games:[]`: 현재 소스에서 확인된 경기가 없거나 제공처가 지연된 상태.
- 점수는 서버에 있는데 앱에 없으면: 최신 폰 앱 설치 여부, 기본 `/feed.json` 동기화, 날짜·팀 경기 ID 병합 확인.

## 테스트
`node --test cloudflare-worker/test/*.test.mjs`
`./gradlew :core:smokeTest :phone:assembleDebug :wear:assembleDebug`

실제 Cloudflare 배포는 GitHub 병합과 별도 작업입니다.
