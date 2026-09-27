# claude-team-starter (Java / Spring Boot)

소규모 팀(2~5명)이 Claude Code로 협업할 때 쓰는 **스타터 템플릿**입니다.
방법론 설명은 [`../docs/claude-code-team-methodology.md`](../docs/claude-code-team-methodology.md)에 있고, 이 저장소는 그 가이드를 **실제로 동작하는 설정**으로 옮긴 것입니다.

- 스택: Spring Boot 4.1 · Java 17 · Gradle (Kotlin DSL) · Spotless(google-java-format)
- 샘플 도메인: 메모 API (`/api/v1/memos`). hooks, 스킬, 규칙이 실제 코드에서 동작하는지 확인하는 용도입니다.
- 추가로 설치할 것: **없음.** JDK 17+와 git만 있으면 됩니다. hook도 Java 단일 파일 스크립트(`java Hook.java`)로 작성해 Node나 Python이 필요 없습니다.

## 빠른 시작

```bash
./gradlew test          # Windows PowerShell: .\gradlew.bat test
claude                  # 처음 실행하면 폴더 신뢰(trust)를 묻습니다. 신뢰해야 팀 allow 규칙이 적용됩니다
```

첫 세션에서 확인할 것:
1. `/context`에 `CLAUDE.md`와 `.claude/rules/testing.md`가 로드되어 있는지
2. `/hooks`에 `PreToolUse`(ProtectFiles)와 `Stop`(VerifyOnStop)이 보이는지
3. `/`를 입력하면 `/spec`, `/pr-ready`, `/adr`, `/new-endpoint`가 나오는지

## 구성

```
CLAUDE.md                        팀 공용 지시문 (명령, 아키텍처, 컨벤션, 주의사항)
.claude/
├── settings.json                권한(allow/ask/deny)과 hooks (팀 공유)
├── hooks/
│   ├── ProtectFiles.java        PreToolUse: .env, 비밀 설정, gradle wrapper, .git 수정 차단
│   └── VerifyOnStop.java        Stop: 소스가 바뀌었으면 spotlessApply + test, 실패하면 계속 고치게 함
├── rules/
│   ├── testing.md               항상 로드
│   └── backend/api-design.md    api 패키지 파일을 다룰 때만 로드 (paths)
├── skills/
│   ├── spec/                    /spec <기능>: 인터뷰 후 docs/specs/에 스펙 작성
│   ├── new-endpoint/            /new-endpoint <API>: 컨벤션대로 TDD 방식 엔드포인트 추가
│   ├── pr-ready/                /pr-ready: 테스트, 리뷰, PR 설명 초안
│   └── adr/                     /adr <제목>: 아키텍처 결정 기록
└── agents/reviewer.md           새 컨텍스트에서 diff를 리뷰하는 서브에이전트
docs/
├── specs/_template.md           스펙 템플릿
└── adr/                         결정 기록 (README.md는 CLAUDE.md가 import)
.github/pull_request_template.md AI 사용 체크리스트 포함 PR 템플릿
```

## 개발 흐름

```
/spec 메모 태그 기능     → docs/specs/2026-..-memo-tags.md (사람이 검토·수정)
/clear                   → 새 컨텍스트
@docs/specs/2026-..-memo-tags.md 구현해   → 테스트 먼저, 구현
(응답이 끝날 때마다)       → Stop hook이 spotlessApply + test 자동 실행
/pr-ready                → 테스트 증거, reviewer 리뷰, PR 설명 초안
사람이 PR을 올리고 동료가 리뷰
```

작은 변경(한 문장으로 설명되는 diff)은 스펙 없이 바로 요청해도 됩니다.

## 자동으로 강제되는 것 vs 권고

| 항목 | 방식 | 위치 |
|---|---|---|
| `.env`, `application-local.*`, `secrets/` 읽기·쓰기 금지 | **강제** (permissions deny. Read deny는 Write도 막음, 실측) | `.claude/settings.json` |
| 비밀 파일, gradle wrapper, `.git/` 수정 금지 | **강제** (PreToolUse hook, exit 2) | `ProtectFiles.java` |
| 코드 변경 후 포맷과 테스트 통과 | **강제** (Stop hook, 실패 시 한 번 되돌려 보냄) | `VerifyOnStop.java` |
| `build.gradle.kts` 수정, `git push` | **확인 요청** (permissions ask) | `.claude/settings.json` |
| 레이어 규칙, API 규칙, 테스트 스타일 | 권고 (CLAUDE.md, rules) + reviewer 에이전트가 검토 | `CLAUDE.md`, `.claude/rules/` |

### VerifyOnStop 동작
- `src/`, `build.gradle.kts`, `settings.gradle.kts`에 변경이 없으면 바로 통과합니다 (질문만 한 턴은 비용 0).
- 마지막으로 통과한 이후 바뀐 게 없으면 다시 실행하지 않습니다 (`build/claude-verify/last-pass`에 지문 저장).
- 실패하면 마지막 40줄을 Claude에게 보내 계속 고치게 합니다. **다시 실패하면 더 막지 않고** 사용자에게 알립니다 (무한 루프 방지).
- git 저장소가 아니면 아무것도 하지 않습니다.

## 다른 프로젝트에 적용하기: 공통 코어와 스택별 오버레이

이 저장소는 "공통 코어 + Java/Gradle 오버레이"가 이미 합쳐진 상태입니다. 기존 프로젝트나 다른 스택에 가져갈 때는 아래 표를 기준으로 복사하고 수정하세요.

| 파일 | 구분 | 다른 프로젝트에 적용할 때 |
|---|---|---|
| `.claude/skills/spec`, `pr-ready`, `adr` | 공통 코어 | 그대로 복사. `pr-ready`의 테스트 명령만 수정 |
| `.claude/agents/reviewer.md` | 공통 코어 (+Spring 항목) | "레이어 규칙" 항목을 프로젝트 구조에 맞게 수정 |
| `docs/specs/_template.md`, `docs/adr/`, PR 템플릿 | 공통 코어 | 그대로 복사 (ADR 내용만 새로 작성) |
| `.claude/hooks/ProtectFiles.java` | 공통 코어 | 보호 패턴(`protectedReason`)만 수정 |
| `.claude/hooks/VerifyOnStop.java` | 코어 로직 + 스택 설정 | `WATCHED`, `GRADLE_TASKS`, `runGradle()` 수정 (Maven이면 `mvnw`) |
| `.claude/settings.json` deny/ask | 공통 코어 | 그대로 |
| `.claude/settings.json` allow | **스택별** | 빌드 명령에 맞게 교체 |
| `CLAUDE.md` | 구조는 코어, 내용은 **스택별** | Commands/Architecture/Gotchas를 새로 작성 (`/init`으로 초안을 만든 뒤 병합) |
| `.claude/rules/*`, `skills/new-endpoint` | **스택별** | 프레임워크에 맞게 새로 작성 |
| `build.gradle.kts`의 `spotless {}` | **스택별** | 팀 포맷터로 교체 |

### OS 관련 메모
- hooks는 exec form(`"command": "java"`, `"args": [...]`)으로 등록되어 셸을 거치지 않습니다. 그래서 Windows, macOS, Linux에서 똑같이 동작합니다.
- 권한 allow 규칙은 `Bash(./gradlew ...)` 형태입니다. Windows에서 Claude Code가 PowerShell 도구로 `.\gradlew.bat`을 실행하면 확인 요청이 뜰 수 있습니다. 자주 쓰는 명령이면 개인 `settings.local.json`에 허용 규칙을 추가하세요.

## 검증 기록 (2026-09-27, Claude Code 2.1.283, Windows 11, JDK 17)
- `./gradlew spotlessCheck test`: 통과 (테스트 7개)
- `ProtectFiles.java`: 보호/비보호 경로 12개 입력 모두 기대한 종료 코드
- `VerifyOnStop.java`: 변경 없음 → 즉시 통과, 캐시 적중 → 0초, 테스트 실패 → exit 2와 실패 테스트 이름·줄 번호 전달, 재실패 → 더 막지 않고 systemMessage
- 실제 `claude -p` 실행
  - gradle wrapper 수정 시도 → PreToolUse hook이 차단하고 한글 메시지가 Claude에게 전달됨
  - 테스트를 깨는 수정 → Stop hook이 실패를 전달했고, Claude는 테스트를 지우지 않고 원인을 분석한 뒤 사용자에게 판단을 요청함

## 팀 운영 규칙 (요약)
- `CLAUDE.md`, `.claude/` 변경은 PR로 올리고, 최소 1명이 리뷰합니다. 관리자 1명을 정합니다 (순환 가능).
- Claude가 같은 실수를 반복하면 규칙을 추가하는 PR을, 규칙이 없어도 잘하는 것이 있으면 삭제하는 PR을 올립니다.
- 개인 설정은 `CLAUDE.local.md`와 `.claude/settings.local.json`에 둡니다 (gitignore됨).
