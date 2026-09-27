# claude-team-starter (Java / Spring Boot)

소규모 팀(2~5명)이 Claude Code로 협업할 때 쓰는 **스타터 템플릿**입니다.
팀 방법론 가이드("소규모 IT 팀을 위한 Claude Code 개발 방법론")의 내용을 **실제로 동작하는 설정**으로 옮겼습니다. 가이드는 이 저장소 밖의 별도 문서입니다.

- 스택: Spring Boot 4.1 · Java 17 · Gradle (Kotlin DSL) · Spotless(google-java-format)
- 샘플 도메인: 메모 API (`/api/v1/memos`). hooks, 스킬, 규칙이 실제 코드에서 동작하는지 확인하는 용도입니다.
- 추가로 설치할 것: **없습니다.** JDK 17+와 git만 있으면 됩니다. hook도 Java 단일 파일 스크립트(`java Hook.java`)로 작성해 Node나 Python이 필요 없습니다.

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
│   └── VerifyOnStop.java        Stop: 코드가 바뀌었으면 spotlessApply + test, 실패하면 계속 고치게 함
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
| `.env`, `.env.*`, `application-local.*`, `application-secret.*`, `secrets/` 읽기·수정 금지 | **강제** (permissions deny) | `.claude/settings.json` |
| 위 파일 + gradle wrapper, `.git/` 수정 금지 | **강제** (PreToolUse hook, 2차 방어선) | `ProtectFiles.java` |
| 코드 변경 후 포맷과 테스트 통과 | **강제** (Stop hook, 실패 시 한 번 되돌려 보냄) | `VerifyOnStop.java` |
| `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml` 수정, `git push` | **확인 요청** (permissions ask) | `.claude/settings.json` |
| 레이어 규칙, API 규칙, 테스트 스타일 | 권고 (CLAUDE.md, rules) + reviewer 에이전트가 검토 | `CLAUDE.md`, `.claude/rules/` |

### 강제의 한계 (알고 쓰세요)
- **deny 규칙이 막는 것**: Claude의 파일 도구(Read/Edit/Write), 그리고 Claude Code가 알아보는 Bash 파일 명령(`cat`, `head`, `sed`, `tee`, `> file` 리다이렉션 등).
  - 공식 문서에 따르면 Read deny는 같은 경로의 Edit/Write와 새 파일 생성도 막습니다. 다만 NotebookEdit은 막지 않기 때문에 `Edit` deny를 함께 두었습니다.
- **막지 못하는 것**
  - 파일 이름을 명령에 드러내지 않고 읽는 명령. 예: `.env`가 있는 폴더에서 `grep -r KEY .`
  - 스크립트나 프로그램이 간접적으로 파일을 여는 경우. 예: Python, Node, Gradle 태스크
  - 이런 경우까지 막으려면 OS 수준 격리인 `/sandbox`를 켜세요.
- **ProtectFiles**는 파일 편집 도구(Edit, Write, NotebookEdit)만 검사합니다. 입력을 해석하지 못하면 편집을 허용합니다(fail-open).
- **`.env.example`도 보호 대상**입니다(`.env.*` 패턴). Claude가 읽어야 하는 예시 파일은 `env.example`처럼 점 없이 이름 붙이세요.

### VerifyOnStop 동작
- **검증 대상**: `docs/`, `.claude/`, `.github/`, `*.md`를 **제외한** 모든 경로입니다. 멀티 모듈, `gradle/libs.versions.toml`, `buildSrc/`도 자동으로 포함됩니다.
- **실행하는 경우**
  - 작업 트리에 변경이 있고, 마지막으로 통과한 상태와 다를 때
  - 트리는 깨끗하지만, 지난번 이후 HEAD가 바뀌었고 그 사이 코드가 바뀌었을 때. 커밋, pull, 브랜치 전환이 여기에 해당합니다. Claude가 수정하고 곧바로 커밋해도 검증이 빠지지 않습니다.
- **건너뛰는 경우**
  - 질문만 한 턴
  - 문서만 바뀐 경우
  - 마지막 통과 이후 아무것도 바뀌지 않은 경우
  - 상태는 `build/claude-verify/state.properties`에 저장됩니다.
- **실패하면** 마지막 60줄(실패한 테스트 이름, 기대값/실제값, 위치)을 Claude에게 보내 계속 고치게 합니다.
  - 전체 로그는 `build/claude-verify/gradle.log`에 있습니다.
  - **다시 실패하면 더 막지 않고** 사용자에게 알립니다. 무한 루프를 막기 위해서입니다.
- **Gradle이 실행조차 안 되면** Claude를 막지 않고 사용자에게만 알립니다. 시간 초과(540초), 환경 문제 등이 해당합니다. 코드 문제가 아닌데 Claude가 "고치려" 들지 않게 하기 위해서입니다.
- **주의: 사용자가 직접 작성 중인 미완성 코드도 검증 대상입니다.** 테스트가 깨진 상태로 두고 Claude에게 질문만 해도 한 번 막힙니다. 이때 Claude가 코드를 고치려 하면 멈추고 방향을 알려 주세요.
- **주의: `spotlessApply`는 응답이 끝난 뒤 파일을 다시 포맷합니다.** Claude가 읽은 내용과 줄바꿈·들여쓰기가 달라질 수 있습니다. 기능에는 영향이 없습니다.
- git 저장소가 아니면 아무것도 하지 않습니다.

## 다른 프로젝트에 적용하기: 공통 코어와 스택별 오버레이

이 저장소는 "공통 코어 + Java/Gradle 오버레이"가 이미 합쳐진 상태입니다. 기존 프로젝트나 다른 스택에 가져갈 때는 아래 표를 기준으로 복사하고 수정하세요.

| 파일 | 구분 | 다른 프로젝트에 적용할 때 |
|---|---|---|
| `.claude/skills/spec`, `pr-ready`, `adr` | 공통 코어 | 그대로 복사. `pr-ready`의 테스트 명령만 수정 |
| `.claude/agents/reviewer.md` | 공통 코어 (+Spring 항목) | "레이어 규칙" 항목을 프로젝트 구조에 맞게 수정 |
| `docs/specs/_template.md`, `docs/adr/`, PR 템플릿 | 공통 코어 | 그대로 복사 (ADR 내용만 새로 작성) |
| `.claude/hooks/ProtectFiles.java` | 공통 코어 | 보호 패턴(`protectedReason`)만 수정 |
| `.claude/hooks/VerifyOnStop.java` | 코어 로직 + 스택 설정 | `IGNORED`, `GRADLE_TASKS`, `runGradle()` 수정 (Maven이면 `mvnw`) |
| `.claude/settings.json` deny/ask | 공통 코어 | 그대로. 보호 대상이 바뀌면 `ProtectFiles`, `.gitignore`와 함께 수정 |
| `.claude/settings.json` allow | **스택별** | 빌드 명령에 맞게 교체 (Bash와 PowerShell 둘 다) |
| `CLAUDE.md` | 구조는 코어, 내용은 **스택별** | Commands/Architecture/Gotchas를 새로 작성 (`/init`으로 초안을 만든 뒤 병합) |
| `.claude/rules/*`, `skills/new-endpoint` | **스택별** | 프레임워크에 맞게 새로 작성 |
| `build.gradle.kts`의 `spotless {}` | **스택별** | 팀 포맷터로 교체. **기존 코드베이스라면 `ratchetFrom("origin/main")` 주석을 풀어** 첫 실행에 전체 파일이 재포맷되지 않게 함 |
| `build.gradle.kts`의 `testLogging {}` | **스택별 (필수)** | Stop hook이 Claude에게 넘기는 실패 정보가 이 설정에 달려 있음. 빼지 말 것 |
| `.gitignore`의 Claude Code·Secrets 항목 | 공통 코어 | deny 규칙, ProtectFiles와 같은 목록을 유지 |
| `.gitattributes`, `gradlew` 실행 권한 | **스택별** | 복사 후 `git update-index --chmod=+x gradlew` 확인 (hook은 `sh gradlew`로 실행해 권한이 없어도 동작) |

기본 브랜치 이름: `reviewer` 에이전트와 `/pr-ready`는 `main`을 기준으로 diff를 봅니다. 다른 이름이면 두 파일을 수정하세요.

### OS 관련 메모
- hooks는 exec form(`"command": "java"`, `"args": [...]`)으로 등록되어 셸을 거치지 않습니다. 그래서 Windows, macOS, Linux에서 똑같이 동작합니다.
- **Windows**
  - Gradle은 `cmd /c .\gradlew.bat`으로 실행합니다.
  - 출력은 OS 인코딩(한국어 Windows는 MS949)으로 읽고, Claude에게는 UTF-8로 전달합니다.
  - **프로젝트 경로에 `&`가 있으면 `gradlew.bat` 자체가 동작하지 않습니다.** Gradle의 한계입니다. 이 경우 Stop hook은 검증을 건너뛰었다고 알립니다.
- **macOS/Linux**: Gradle은 `sh ./gradlew`로 실행합니다. 이 OS들에서는 직접 실행해 보지 않았으니 처음 도입할 때 한 번 확인하세요.

## 검증 기록 (2026-09-27, Claude Code 2.1.283, Windows 11, JDK 17)
- `./gradlew spotlessCheck test`: 통과 (테스트 11개)
- `ProtectFiles.java`: 22가지 입력이 모두 기대한 종료 코드
  - 대소문자, 상대경로, NotebookEdit, 공백·한글 경로 포함
  - content 안에 들어 있는 `"file_path"` 문자열은 오탐하지 않음
- `VerifyOnStop.java`: 한글·공백·괄호가 들어간 경로의 복제본에서 16개 시나리오 통과
  - 한글 파일명 변경, 깨진 테스트를 커밋한 경우, 문서만 바뀐 경우, 재실패 시 루프 방지
  - `&` 경로에서 차단하지 않고 알림
- 실제 `claude -p` 실행 (초기 버전 기준)
  - gradle wrapper 수정 시도 → PreToolUse hook이 차단하고 한글 메시지가 Claude에게 전달됨
  - 테스트를 깨는 수정 → Stop hook이 실패를 전달했고, Claude는 테스트를 지우지 않고 원인을 분석한 뒤 사용자에게 판단을 요청함

## 팀 운영 규칙 (요약)
- `CLAUDE.md`, `.claude/` 변경은 PR로 올리고, 최소 1명이 리뷰합니다. 관리자 1명을 정합니다 (순환 가능).
- Claude가 같은 실수를 반복하면 규칙을 추가하는 PR을, 규칙이 없어도 잘하는 것이 있으면 삭제하는 PR을 올립니다.
- 개인 설정은 `CLAUDE.local.md`와 `.claude/settings.local.json`에 둡니다 (gitignore됨).
