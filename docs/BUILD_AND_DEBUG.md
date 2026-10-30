# Android Studio / Windows 11 빌드 및 무선 디버깅

## 1. 처음 열기

1. ZIP 압축을 해제합니다 (가능하면 `D:\AndroidSDK\myProjects\Matchday`).
2. Android Studio → **File > Open** → `MATCHDAY_AndroidStudio` 폴더 (`settings.gradle.kts`가 있는 루트)를 선택합니다.
3. JDK는 **17 이상**으로 선택합니다. `Settings > Build > Gradle > Gradle JDK`에서 Embedded JDK도 가능합니다.
4. SDK Manager에서 **Android SDK Platform 36**과 Platform Tools, Android SDK Build-Tools를 설치합니다.
5. Gradle Sync를 실행합니다. 첫 실행 시 Gradle 8.13 및 의존 라이브러리를 다운로드하므로 인터넷이 필요합니다.

> 패키지에 `gradlew.bat`과 자체 Java 부트스트랩 JAR이 있습니다. 보안을 위해 허용된 `services.gradle.org` URL에서만 Gradle을 받습니다. 정식 Gradle Wrapper JAR이 아니므로, Gradle 설치 후 `gradle wrapper --gradle-version 8.13`을 실행해 공식 Wrapper로 교체할 수 있습니다.

## 2. 휴대폰에 실행

- 상단 Run Configuration에서 **phone** 선택 → Galaxy S24 Ultra (USB 또는 무선 ADB) 선택 → ▶ Run / Debug.
- 자동으로 디버그 APK가 설치되고 MATCHDAY 앱이 실행됩니다.
- PowerShell에서도 실행할 수 있습니다:

```powershell
cd D:\AndroidSDK\myProjects\Matchday
.\gradlew.bat :phone:assembleDebug
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r .\phone\build\outputs\apk\debug\phone-debug.apk
```

## 3. 워치에 실행

- 갤럭시 워치의 **개발자 옵션 → ADB 디버깅 / 무선 디버깅**을 켭니다.
- Android Studio의 Device Manager에서 워치를 페어링하고 Watch 대상이 나타나는지 확인합니다.
- **wear** Run Configuration 선택 → 워치 선택 → ▶ Run / Debug.
- 워치 앱 설치 후 워치의 **타일 추가 → MATCHDAY KBO / MATCHDAY LoL**을 각각 추가합니다. 타일을 누르면 워치 앱이 열립니다.

```powershell
.\gradlew.bat :wear:assembleDebug
# 워치 ADB가 연결된 후 (-s에는 실제 장치 시리얼 입력)
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" devices -l
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -s <watch-serial> install -r .\wear\build\outputs\apk\debug\wear-debug.apk
```

> 폰과 워치 모두 `applicationId=dev.matchday`를 사용하며 동일한 디버그 서명키로 빌드해야 Wear Data Layer 전송이 가능합니다. 폰과 워치는 서로 다른 장치이므로 동일한 applicationId여도 충돌하지 않습니다.

## 4. 디버깅 포인트

- 공통 모델: `core/src/main/kotlin/dev/matchday/core/Models.kt`
- JSON 파서: `core/src/main/kotlin/dev/matchday/core/FeedCodec.kt`
- 폰 네트워크·캐시·워치 전송: `phone/.../FeedManager.kt`
- 폰 UI: `phone/.../MainActivity.kt`, `Screens.kt`, `UiComponents.kt`
- 알림·주기 갱신: `phone/.../Background.kt`
- 워치 UI: `wear/.../WatchActivity.kt`
- Wear Data Layer 수신: `wear/.../WatchData.kt`
- 타일 서비스: `wear/.../MatchdayTiles.kt`
- 예제 결과 편집: `feeds/example.json`, `phone/src/main/assets/demo_feed.json`, `wear/src/main/assets/demo_feed.json`
- 필터링 Logcat: `package:dev.matchday` / 태그 `Matchday`, `MatchdayWatch`.

## 5. 알려진 제한

- 샘플 일정·결과·대진·순위는 **임의 생성한 데이터**입니다. 실시간 API 연동/실제 순위 보장은 없습니다.
- 샘플 팀 아이콘은 창작한 임시 PNG이며 공식 구단 로고가 아닙니다. HTTPS 피드의 `logoUrl`로 실제 구단 리소스를 연결할 수 있지만 이미지 사용권은 데이터 제공자/사용자가 확인해야 합니다.
- 외부 JSON URL을 설정한 뒤 앱 전경 복귀 시 갱신, 시스템이 허용할 때 30분 주기 백그라운드 갱신을 시도합니다.
- 워치 타일은 로컬 캐시를 표시하며 15분 freshness 요청을 사용합니다. **실시간 점수의 지연 없는 보장은 불가능**합니다.
- 시작 알림은 기본 미사용이고 30분 전 또는 시작 시 옵션을 켤 수 있습니다. Android WorkManager는 정확한 알람 시각을 보장하지 않으며, 경기 종료 알림은 아직 없습니다.
- 워치 테마는 폰에서 마지막으로 전송한 설정 또는 워치 시스템 설정을 따릅니다. 공식 데이터 제공자의 대회 방식에 따라 토너먼트 분기 노드 JSON을 확장할 필요가 있습니다.
- 본 환경은 Android SDK/Gradle 배포본/외부 Maven 접근이 없어 **Android APK 전체 빌드를 실행 검증하지 못했습니다**. 공통 Kotlin 데이터 모듈은 로컬 Kotlin 컴파일·샘플 JSON 검증으로 확인했습니다. 첫 Sync에서 문제가 발생하면 Gradle Build 출력 전체를 보내주세요.
