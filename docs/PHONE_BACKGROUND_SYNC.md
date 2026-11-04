# MATCHDAY 백그라운드 점수 동기화

이번 업데이트는 휴대폰 앱을 화면에서 닫아도 경기 점수 및 일정 동기화를 수행하도록 개선합니다. **폰 앱 데이터와 워치 타일 캐시는 그대로 유지**합니다.

## 동기화 방식

| 동작 상태 | 갱신 방식 | 주기 |
|---|---|---|
| 폰 앱 실행 중 (빠른 모드 OFF) | `/live.json` 점수 갱신 후 Wear Data Layer 전송 | 약 60초 |
| 앱 화면 종료 / 다른 앱 사용 | WorkManager가 `/live.json` 및 필요 시 `/feed.json`을 갱신 후 워치 전송 | 최소 15분, 실행은 OS 재량 |
| **설정에서 빠른 백그라운드 갱신 ON** | 알림이 표시되는 `dataSync` 포그라운드 서비스가 경량 점수 API 조회·워치 전송 | 약 60초, **최대 5시간 30분** |
| 빠른 모드 만료 / 중지 | 자동 15분 갱신은 계속 유지 | OS 재량 |

### 중요한 Android 제한

- WorkManager 주기적 작업은 최소 반복 간격 15분이며, 정확한 실행 시각은 보장되지 않습니다.
- Android 15 이상에서 `dataSync` 포그라운드 서비스는 **24시간 동안 백그라운드 총 6시간 제한**을 갖습니다. 앱에서 안전하게 5시간 30분에 세션을 끝내고 `onTimeout()`도 처리합니다. 같은 날 세션을 반복하면 더 일찍 종료되거나 시작이 거부될 수 있습니다.
- 빠른 모드는 **사용자의 명시적 조작**으로만 시작됩니다. 앱이 스스로 부팅 시 1분 서비스를 재시작하지 않습니다.
- 빠른 모드 동안 지속 알림이 표시되며 **'갱신 중지'** 버튼으로 언제든 종료 가능합니다.
- Android 설정의 **강제 종료(Force stop)**, 제조사 절전 제한, 네트워크 불능 등의 경우 갱신을 지속할 수 없습니다.
- 휴대폰 전원이 꺼져 있거나 워치와 Wear Data Layer 연결이 끊긴 경우 실시간 워치 갱신은 불가능합니다. 연결이 복구되면 최신 저장 데이터 동기화가 재개됩니다.

## 사용 방법

1. GitHub에서 최신 `master`를 Pull한 뒤 **휴대폰 앱**을 새로 설치합니다. 워치 앱 변경은 없습니다.
2. 기존의 `/feed.json?key=...` URL을 그대로 유지하고 **데이터 동기화**를 한 번 실행합니다.
3. **설정 → 앱 종료 후 자동 동기화 → 경기 중 1분 백그라운드 갱신** 스위치를 켭니다.
4. 알림 권한 요청이 나오면 허용합니다.
5. 앱을 닫거나 다른 앱을 실행해도 MATCHDAY 알림이 보이는 동안은 약 60초마다 경기 점수를 수신하고 워치로 전송합니다.
6. 알림에서 **갱신 중지**를 누르거나 설정 스위치를 끄면 빠른 모드 종료. 기본 15분 동기화는 유지됩니다.

빠른 갱신 서비스는 배터리 및 모바일 데이터를 사용합니다. 경기 없는 시간에는 끄는 것을 권장합니다.

## 구현 및 검증

- `FastScoreSyncService.kt`: `foregroundServiceType=dataSync`, 명시적 시작·중지, 알림 및 자동 만료.
- `Background.kt`: 기존 WorkManager 15분 작업을 유지하며 일정뿐 아니라 실제 **점수 API**도 조회합니다. 앱을 열 때마다 주기 작업을 다시 예약하는 기존 구조를 개선했습니다.
- `FeedManager.kt`: 전체 피드의 마지막 정상 갱신 시각을 저장하고, 동시에 실행될 수 있는 WorkManager/Activity/FGS의 JSON 읽기·쓰기를 Mutex로 직렬화합니다. 실패 시 기존 데이터를 유지합니다.
- `MainActivity.kt`, `Screens.kt`: 사용자 스위치, 권한 요청, 화면 상태 표시, 서비스 중단 시 화면 갱신 복귀.
- `AndroidManifest.xml`: Android 14+ 필수 포그라운드 서비스 선언 및 권한.

**Cloudflare Worker 코드는 변경하지 않았으므로 재배포가 필요 없습니다.** 최신 Worker v5의 기존 `/feed.json`·`/live.json`을 그대로 사용합니다.

### 빌드

```bash
./gradlew :core:smokeTest :phone:assembleDebug :wear:assembleDebug
```

### 실기기 확인

1. 앱에서 빠른 모드 ON 후 HOME으로 나가도 알림이 유지되는지 확인.
2. Worker `/live.json`의 `updatedAt` 변화와 실제 팀 점수 반영, 워치 타일 갱신 확인.
3. 알림의 갱신 중지를 누른 뒤 60초 주기 갱신이 멈추는지 확인.
4. 모드 OFF에서 WorkManager 작업이 15분 이상의 간격으로 실행되는지 로그로 확인. 절전 상태에서는 지연될 수 있습니다.

ADB 디버깅 예시(기기 선택 필요):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices -l
& $adb -s PHONE_DEVICE_ID logcat -d -t 500 -s MatchdayFastSync WorkManager
```

API 키나 `?key=...` URL 전체는 로그·GitHub·스크린샷에 공유하지 마세요.
