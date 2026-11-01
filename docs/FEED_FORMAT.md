# JSON 피드 계약 (schemaVersion=1)

`feeds/example.json` 파일을 수정하여 HTTPS로 호스팅하면 폰의 설정 → 데이터 피드 URL에서 불러옵니다.
폴더를 GitHub에 업로드하고 Raw 링크를 사용해도 됩니다. **정적 JSON은 자동으로 갱신되지 않습니다.**
실제 경기 데이터를 사용하려면 별도 데이터 제공자/서버가 해당 JSON을 업데이트해야 합니다.

```json
{
  "schemaVersion": 1,
  "updatedAt": "2026-10-30T11:30:00+09:00",
  "isDemo": false,
  "demoDay": "2026-10-30",
  "teams": [{ "id": "SAM", "sport": "KBO", "name": "삼성 라이온즈", "short": "삼성", "mark": "SL", "logoUrl": "https://example.org/samsung.png" }],
  "games": [{ "id": "game001", "competition": "KBO", "season": "2026", "dateTime": "2026-10-30T18:30:00+09:00", "home": "SAM", "away": "TBD", "status": "UPCOMING", "stage": "플레이오프", "venue": "구장명", "seriesId": "ko-po" }],
  "standings": [{ "competition": "KBO", "season": "2026", "rank": 1, "team": "SAM", "played": 144, "won": 80, "lost": 62, "drawn": 2, "forScore": 0, "againstScore": 0, "streak": "2연승" }],
  "series": [{ "id": "ko-po", "competition": "KBO", "stage": "플레이오프", "group": "MAIN", "roundIndex": 2, "left": "SAM", "right": "TBD", "leftWins": 2, "rightWins": 1, "status": "UPCOMING", "scheduled": "2026-10-30T18:30:00+09:00", "bestOf": 5, "nextId": "ko-ks" }]
}
```

- 대회 식별자: `KBO`, `LCK`, `MSI`, `EWC`, `WORLDS`, `FST` (FST는 확장 가능)
- 경기 상태: `UPCOMING`, `LIVE`, `FINAL`, `POSTPONED`, `UNKNOWN` (경기 시작 후 상태 미확인)
- 라운드 그룹: `MAIN` (KBO/녹아웃), `UPPER`, `LOWER`, `GROUP`
- 모든 팀/경기/시리즈 ID는 서로 일관성을 유지해야 합니다.
- 시간은 한국 시간 등 ISO-8601 오프셋이 포함되어야 합니다. 예: `+09:00`.
- `logoUrl`은 HTTPS 주소를 사용하면 앱에서 로고를 다운받습니다. 미설정/오류 시 내장 팀 이미지로 대체됩니다.
- KBO는 정규시즌 순위, 가을야구 시리즈를 분리합니다. 국제대회는 리그 테이블이 없을 수도 있으므로 `standings`를 빈 배열로 둘 수 있습니다.
- 사용자에게 잘못된 결과가 전달되지 않도록 검증 실패 시 마지막 정상 수신 데이터를 사용합니다.
