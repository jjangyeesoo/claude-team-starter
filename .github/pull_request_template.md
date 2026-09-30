## 무엇을 / 왜
<!-- 한두 문장. 관련 이슈: #  -->

## 스펙
<!-- docs/specs/... (없으면 "소규모 변경"이라고 쓰기) -->

## 검증 증거
<!-- 실행한 명령과 결과 요약. 예: backend ./gradlew test → 12 tests, 0 failed / web npm run verify → 통과 -->

## AI 사용 체크리스트
- [ ] 스펙 범위 밖 변경 없음 (있다면 사유)
- [ ] AI 리뷰(`/pr-ready` 또는 reviewer 에이전트) 수행, 반영/보류 사유 기록
- AI 리뷰 지적 __건 / 반영 __건 (작성자가 기록, 리뷰어가 확인)
- [ ] 테스트 약화(assertion 삭제, @Disabled, .skip) 없음
- [ ] API가 바뀌었다면 backend DTO와 web 타입(`web/src/lib/api/types.ts`)을 함께 수정
- [ ] 새 의존성 없음 (있다면 사유)
- [ ] 작성자는 모든 변경 줄을 설명할 수 있음
