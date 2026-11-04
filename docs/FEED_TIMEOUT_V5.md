# 전체 피드 동기화 timeout 해결 (Worker v5)

## 원인 (GitHub master 분석)

Android \`FeedManager.refresh()\`는 전체 JSON \`/feed.json\`의 서버 응답 대기 시간이 **10초**였습니다.
한편 Worker v4의 전체 피드는 PandaScore 글로벌 경기 → 리그별 경기 → 순위/대진
(대회 5종 순차) → KBO 정규시즌 → KBO 포스트시즌 API를 기다린 뒤 응답했습니다.
첫 요청 또는 5분 캐시 만료 시 서버 응답이 10초를 넘을 수 있었습니다.

이 문제는 \`/live.json\`을 앱 설정에 입력하여 해결할 수 없습니다.
\`/live.json\`은 진행 중인 경기의 작은 점수 변경분이므로 팀 목록이 비어 있을 수 있습니다.

## 적용 변경

1. **휴대폰**: 전체 \`/feed.json\`에 한하여 연결 10초, 응답 읽기 45초 제한.
   1분 갱신용 \`/live.json\`은 기존의 짧은 제한을 유지합니다.
2. **Worker v5**: KBO 수집과 LoL 수집 병렬 실행. LoL LCK/MSI/EWC/Worlds/FST의
   순위와 대진 API 요청도 서로 병렬화하며 각 PandaScore 요청에 8초 timeout.
   오류가 나는 한 대회의 순위가 다른 종목의 일정 수집을 막지 않도록 합니다.
3. **정상 결과 캐시**: \`/feed.json\`과 \`/status\`의 5분 갱신 정책은 유지하되
   **마지막 성공한 전체 피드**를 Cloudflare의 데이터센터별 캐시에 최대 24시간 보관.
   5분이 지난 캐시가 있으면 앱에 즉시 마지막 정상 피드를 주고, 백그라운드 갱신.
   갱신 실패는 정상 피드를 덮어쓰지 않습니다.
4. **진단**: \`/status\` JSON의 \`cache\` 및 \`ageSeconds\` 항목으로
   차가운 캐시/보유 중인 오래된 캐시를 구분합니다.

Cloudflare Cache API는 데이터센터별 로컬 캐시이며 항상 존재하는 영구 DB가 아닙니다.
처음 요청하거나 캐시가 사라진 데이터센터에서는 새 피드 생성에 여전히 수 초 이상 걸릴 수 있습니다.
이 때문에 앱의 대기 시간 증가와 병렬화가 함께 적용됩니다.

## 설치 단계

1. GitHub \`master\`에서 \`cloudflare-worker/src.mjs\`의 **Raw** 코드 전체 복사.
2. Cloudflare → Workers & Pages → \`matchday-pandascore-feed\` → Edit code
   → 기존 JS 전체 교체 → **Deploy**.
3. [Worker health](https://matchday-pandascore-feed.kungsman3356.workers.dev/health) 확인:
   \`"version":5\` 출력. 이 값이 다르면 이전 Worker를 실행 중입니다.
4. 휴대폰 Android Studio에서 \`git pull origin master\` 후 \`phone\` 앱 업데이트.
   워치 \`wear\` 앱은 이번 timeout 수정으로 필수 재설치할 필요가 없습니다.
5. MATCHDAY 설정의 주소는 그대로 아래 형식 유지:
   \`\`\`
   https://matchday-pandascore-feed.kungsman3356.workers.dev/feed.json?key=본인_FEED_ACCESS_KEY
   \`\`\`
6. 앱에서 **저장 및 새로고침** 후 데이터 수신 확인.
   첫 번 요청은 캐시가 비어서 더 오래 걸릴 수 있고, 이후에는 일반적으로 더 빨리 응답합니다.

## 오류가 계속된다면

브라우저에서 동일 \`/feed.json?key=...\`를 **비공개로** 열어보세요.

- 브라우저 정상 JSON + 앱 timeout: 폰 네트워크, 앱 업데이트 여부, 오래된 앱 설치 여부 확인.
- 브라우저도 오래 걸림/502: Worker Logs의 외부 API 타임아웃 또는 무료 플랜 제한 확인.
- 403: \`FEED_ACCESS_KEY\` 불일치. PandaScore 토큰을 URL에 사용하지 않습니다.
- 서버 \`/health\` version 4: Worker v5 배포 누락.
- \`/status?key=...\`를 조회하면 \`cache\`, \`ageSeconds\`,
  \`diagnostics.counts\`, \`diagnostics.warnings\`만 공유하세요. 키를 포함한 전체 URL은
  채팅·이슈·스크린샷에 게시하지 마세요.

이 변경은 60초 간격 실시간 점수 \`/live.json\` 캐시·갱신 주기를 변경하지 않습니다.

## 검증

GitHub Actions:
\`\`\`bash
node --test cloudflare-worker/test/*.test.mjs
./gradlew :core:smokeTest :phone:assembleDebug :wear:assembleDebug
\`\`\`
