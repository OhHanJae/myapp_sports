# MATCHDAY 실제 데이터 수정판(v2) 배포

이번 업데이트는 **Android 프로젝트와 Cloudflare Worker를 모두 수정**합니다. GitHub 앱 코드를 Pull하는 것만으로 Cloudflare에 이미 배포된 Worker는 갱신되지 않습니다.

## 1. Cloudflare Worker 소스 교체

1. [Cloudflare Dashboard](https://dash.cloudflare.com) → **Workers & Pages** → `matchday-pandascore-feed`.
2. **Edit code** 클릭.
3. 이 저장소 [cloudflare-worker/src.mjs](../cloudflare-worker/src.mjs) 파일 전체를 복사해 기존 코드를 대체.
4. **Deploy** 클릭. 기존 Secret은 수정할 필요 없습니다.
5. 다음 주소를 열어 버전 확인 (키 불필요):

```
https://matchday-pandascore-feed.kungsman3356.workers.dev/health
```

성공했다면 `"version": 2`가 표시됩니다. 이 값이 없으면 이전 Worker 코드가 계속 실행 중입니다.

## 2. 데이터 상태 진단

다음 주소에서 `<접근키>`만 본인이 등록한 `FEED_ACCESS_KEY`로 변경하여 브라우저에서 열어보세요.

```
https://matchday-pandascore-feed.kungsman3356.workers.dev/status?key=<접근키>
```

응답의 `diagnostics.counts`는 KBO/LoL 경기, 순위, 대진 수량입니다. `diagnostics.warnings`는 PandaScore API 제한/실패 정보를 포함할 수 있습니다. **API 토큰이나 접근 키 값은 GitHub·채팅·스크린샷에 공개하지 마세요.** 전체 URL도 키가 포함돼 있으므로 공유 금지입니다.

마지막으로 기존 앱에 등록한 `/feed.json?key=<접근키>` 주소로 응답이 오는지 확인합니다. 같은 URL을 유지하므로 앱에서 다시 입력할 필요는 없습니다.

## 3. Android Studio 적용

1. `git pull origin master` 또는 Android Studio Git → Pull.
2. 앱을 다시 빌드하여 휴대폰(`phone`)과 워치(`wear`) 모두 설치.
3. 휴대폰 앱 → 상단 새로고침 → **LoL** 탭 진입.
4. LCK 비시즌에는 현재 경기 중인 Worlds 등 다른 대회를 **처음 표시할 수 있도록 개선**했습니다. 수동으로 대회를 선택하면 그 선택이 유지됩니다.
5. 워치 타일을 다시 열어 새 데이터를 확인합니다.

## 4. 워치 타일 경기 선택 규칙

1. 응원팀의 **오늘 경기**가 있으면 우선 표시
2. 응원팀 경기 없음 → **다른 팀의 오늘 경기** (진행 중 우선)
3. 오늘 등록된 경기 자체가 없음 → **모든 팀 중 가장 가까운 미래 경기**
4. 이후 예정 경기도 없음 → '등록된 경기 일정이 없습니다'

KBO와 LoL 타일에 동일한 규칙이 적용됩니다.

## 5. 데이터 제공 범위

- **KBO:** 비공식 공개 자료 저장소 [dentearl/kbo-data](https://github.com/dentearl/kbo-data)의 2026 정규시즌 일정·종료 점수로 순위를 계산합니다. 2026 정규시즌 완료 경기 720개와 공식 순위 수치를 대조했습니다.
- **KBO 포스트시즌:** 비공식 네이버 스포츠 일정 게이트웨이(2026년 10월 14일~11월 20일)가 정상 응답할 때만 포스트시즌 일정·점수·대진을 구성합니다. 사이트 차단·구조 변경·빈 응답이면 대진은 표시되지 않습니다. 일정에 없는 팀이나 경기·점수는 생성하지 않습니다. 시리즈 라운드는 정규시즌 최종 시드 순위를 기준으로 구성합니다.
- **LoL:** PandaScore에서 국제대회/지역 리그를 각각 조회해 글로벌 100경기 제한에 의한 누락을 줄였습니다. 순위/대진은 여러 토너먼트를 차례로 확인합니다.
- **LoL 순위:** PandaScore 대회 순위 엔드포인트가 비어 있으면 *수신 가능한 정규시즌 경기 결과 기준 참고 순위*를 계산할 수 있습니다. 공식 리그 순위와 구분해야 합니다.
- **LoL 대진:** 연결 관계가 확인되지 않은 대진은 임의 연결선을 생성하지 않습니다. 제공처가 brackets 데이터를 반환한 경우에만 토너먼트 시리즈로 변환합니다.

외부 데이터 공급자의 반영 시각/서비스 정책 때문에 지연·누락이 발생할 수 있으며, 공식 중계 서비스는 아닙니다.

## 6. 점검

폰→워치 데이터는 KBO 시즌 전체 JSON의 크기를 고려해 **GZIP+Base64 압축**으로 전송합니다. 구형 앱의 평문 전송도 수신할 수 있습니다.

저장소의 GitHub Actions에서 다음을 테스트합니다.

- `node --test cloudflare-worker/test/*.test.mjs`
- `./gradlew :core:smokeTest :phone:assembleDebug :wear:assembleDebug`

계속 비어 있으면 `/status` 응답의 **counts와 warnings만** 보내주세요. `FEED_ACCESS_KEY` 및 토큰은 제외해 주세요.
