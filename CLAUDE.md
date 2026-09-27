# Project: starter (Claude Code 팀 스타터 샘플)
Spring Boot 4.1 / Java 17 / Gradle (Kotlin DSL) 기반 REST API. 샘플 도메인은 메모(`memo`)다.

## Commands
- 전체 테스트: `./gradlew test`  (Windows PowerShell: `.\gradlew.bat test`)
- 단일 테스트: `./gradlew test --tests "com.example.starter.memo.api.MemoControllerTest"` (전체보다 단일 테스트를 우선 실행)
- 포맷: `./gradlew spotlessApply` (google-java-format). 직접 들여쓰기를 맞추지 말고 이 명령에 맡긴다
- 실행: `./gradlew bootRun` (포트 8080)

## Architecture
- 기능(도메인) 단위 패키지: `com.example.starter.<feature>.{domain,api}`
  - `domain`: 비즈니스 로직, 엔티티. `api`를 import하지 않는다
  - `api`: Controller, 요청/응답 DTO(record). 엔티티를 그대로 응답하지 않고 `*Response.from()`으로 변환한다
- 공통 에러 처리: `common/error/ApiExceptionHandler`. 에러 응답은 항상 `ErrorResponse { code, message, details }`
- 시간: `Clock` 빈(`common/config/TimeConfig`)을 생성자로 주입받는다. `Instant.now()`를 직접 호출하지 않는다
- 아키텍처 결정 목록: @docs/adr/README.md

## Workflow
- 새 기능은 `/spec`으로 `docs/specs/`에 스펙을 먼저 만들고, 새 세션에서 스펙을 참조해 구현한다
- 버그 수정은 재현하는 실패 테스트를 먼저 작성한다
- 작업이 끝나면 Stop hook이 `spotlessApply test`를 자동 실행한다. 실패하면 근본 원인을 고친다
- PR 전에 `/pr-ready`를 실행한다

## Conventions
- 브랜치: `feat/<issue>-<slug>`, `fix/<issue>-<slug>` · 커밋: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`)
- PR 하나 = 스펙(또는 이슈) 하나, 변경 400줄 이하를 목표로 한다
- 의존성 추가(`build.gradle.kts` 수정)는 사람의 승인이 필요하다. 먼저 이유를 설명하고 묻는다

## Gotchas
- Spring Boot 4: 테스트 슬라이스 패키지가 바뀌었다. `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있다
- IMPORTANT: 테스트를 통과시키려고 assertion을 지우거나 `@Disabled`를 붙이지 않는다

## Compaction
- 컨텍스트를 압축할 때는 수정한 파일 목록, 연결된 스펙 경로, 실패 중인 테스트 이름을 보존한다
