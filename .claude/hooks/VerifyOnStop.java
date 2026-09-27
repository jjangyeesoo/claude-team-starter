// Stop hook: Claude가 응답을 마칠 때, 코드가 바뀌었으면 포맷 + 테스트를 실행한다.
//
// 언제 실행하나
// - 작업 트리에 변경(스테이징·미스테이징·untracked)이 있고, 마지막으로 통과한 상태와 다를 때
// - 작업 트리는 깨끗하지만 지난번 Stop 이후 HEAD가 바뀌었고(커밋, pull, 브랜치 전환), 그 사이에 코드 변경이 있을 때
// 언제 건너뛰나
// - 문서만 바뀌었을 때(IGNORED), 마지막 통과 이후 아무것도 바뀌지 않았을 때, git 저장소가 아닐 때
//
// 실패하면 종료 코드 2로 멈춤을 막고 실패 로그를 Claude에게 전달한다.
// 이미 한 번 막았는데(stop_hook_active) 또 실패하면 더 막지 않고 사용자에게 알린다 (무한 루프 방지).
// 주의: 사용자가 직접 작성 중인 미완성 코드도 검증 대상이다. 테스트가 깨진 상태로 두고 질문만 해도 한 번 막힌다.
//
// JDK 17+와 git만 있으면 OS와 무관하게 동작한다.

import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class VerifyOnStop {

  // 검증을 건너뛸 경로 (git pathspec). 나머지 모든 경로의 변경은 검증 대상이다 (멀티 모듈도 자동 포함).
  private static final List<String> IGNORED =
      List.of(":(exclude)docs", ":(exclude).claude", ":(exclude).github", ":(glob,exclude)**/*.md");
  // 스택을 바꾸면 이 목록과 gradleCommand()를 수정한다.
  private static final List<String> GRADLE_TASKS = List.of("spotlessApply", "test");
  private static final long GRADLE_TIMEOUT_SECONDS = 540; // settings.json의 hook timeout(600초)보다 짧게
  private static final int TAIL_LINES = 60;

  private static final PrintStream OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);
  private static final PrintStream ERR = new PrintStream(System.err, true, StandardCharsets.UTF_8);

  public static void main(String[] args) throws Exception {
    String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    boolean alreadyBlocked = input.matches("(?s).*\"stop_hook_active\"\\s*:\\s*true.*");

    Path root = projectRoot();
    Path stateDir = root.resolve("build/claude-verify");
    Path stateFile = stateDir.resolve("state.properties");
    Properties state = loadState(stateFile);

    String head = git(root, "rev-parse", "-q", "--verify", "HEAD");
    if (head == null) head = ""; // 커밋이 없는 저장소
    List<String> changed = changedFiles(root);
    if (changed == null) return; // git 저장소가 아님

    String fingerprint = fingerprint(root, head.trim(), changed);
    String lastSeenHead = state.getProperty("lastSeenHead");
    boolean passedBefore = fingerprint.equals(state.getProperty("lastPass"));

    boolean needsRun;
    if (!changed.isEmpty()) {
      needsRun = !passedBefore;
    } else {
      // 트리는 깨끗함. HEAD가 바뀌었고 그 사이 코드가 바뀐 경우에만 검증한다.
      needsRun =
          !passedBefore
              && lastSeenHead != null
              && !lastSeenHead.equals(head.trim())
              && codeChangedBetween(root, lastSeenHead, head.trim());
    }

    if (!needsRun) {
      state.setProperty("lastSeenHead", head.trim());
      saveState(stateFile, state);
      return;
    }

    Path log = stateDir.resolve("gradle.log");
    Files.createDirectories(stateDir);
    int exitCode = runGradle(root, log);
    String output = readLog(log);

    if (exitCode == 0) {
      // spotlessApply가 파일을 바꿨을 수 있으므로 실행 후 상태로 다시 계산한다.
      List<String> after = changedFiles(root);
      state.setProperty("lastPass", fingerprint(root, head.trim(), after == null ? List.of() : after));
      state.setProperty("lastSeenHead", head.trim());
      saveState(stateFile, state);
      return;
    }

    String tail = tail(output, TAIL_LINES);
    if (!isBuildFailure(output)) {
      // Gradle이 시작조차 못 했거나 시간 초과: 코드 문제가 아니므로 Claude를 막지 않고 사용자에게만 알린다.
      String msg =
          "VerifyOnStop: 포맷/테스트를 실행하지 못했습니다 (exit "
              + exitCode
              + "). 로그: build/claude-verify/gradle.log\n"
              + tail(output, 5);
      OUT.println("{\"systemMessage\": \"" + jsonEscape(msg) + "\"}");
      return;
    }
    if (alreadyBlocked) {
      String msg =
          "VerifyOnStop: 포맷/테스트가 여전히 실패합니다. 직접 확인이 필요합니다. 로그: build/claude-verify/gradle.log";
      OUT.println("{\"systemMessage\": \"" + jsonEscape(msg) + "\"}");
      return;
    }
    ERR.println(
        "VerifyOnStop: `gradlew "
            + String.join(" ", GRADLE_TASKS)
            + "` 실패. 근본 원인을 고치고 다시 테스트하세요 (테스트를 지우거나 skip하지 말 것).\n"
            + "전체 로그: build/claude-verify/gradle.log, 테스트 리포트: build/reports/tests/test/index.html\n"
            + "--- 마지막 "
            + TAIL_LINES
            + "줄 ---\n"
            + tail);
    System.exit(2);
  }

  static Path projectRoot() {
    String dir = System.getenv("CLAUDE_PROJECT_DIR");
    return Path.of(dir == null || dir.isBlank() ? "." : dir).toAbsolutePath().normalize();
  }

  /** 검증 대상 경로의 변경 파일 목록. git 저장소가 아니면 null. 경로는 UTF-8 원문(-z, quotePath=false). */
  static List<String> changedFiles(Path root) throws Exception {
    List<String> cmd =
        new ArrayList<>(
            List.of(
                "git", "-c", "core.quotePath=false", "status", "--porcelain=v1", "-z", "-uall",
                "--", "."));
    cmd.addAll(IGNORED);
    Result r = run(root, cmd, StandardCharsets.UTF_8);
    if (r.exitCode != 0) return null;

    List<String> files = new ArrayList<>();
    String[] entries = r.output.split("\0");
    for (int i = 0; i < entries.length; i++) {
      String e = entries[i];
      if (e.length() < 4) continue;
      files.add(e); // "XY path" 전체를 넣어 상태 변화(스테이징 등)도 지문에 반영
      char x = e.charAt(0);
      if (x == 'R' || x == 'C') i++; // rename/copy는 다음 항목이 원래 경로
    }
    return files;
  }

  /** HEAD, 변경 목록, 변경 파일 내용을 해시한다. */
  static String fingerprint(Path root, String head, List<String> changed) throws Exception {
    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    sha.update(("HEAD " + head + "\n").getBytes(StandardCharsets.UTF_8));
    for (String entry : changed) {
      sha.update(entry.getBytes(StandardCharsets.UTF_8));
      sha.update((byte) 0);
      Path file = root.resolve(entry.substring(3));
      if (Files.isRegularFile(file)) sha.update(Files.readAllBytes(file));
    }
    return HexFormat.of().formatHex(sha.digest());
  }

  /** 두 커밋 사이에 검증 대상 경로의 변경이 있는가. 비교할 수 없으면(히스토리 재작성 등) true. */
  static boolean codeChangedBetween(Path root, String from, String to) throws Exception {
    if (to.isEmpty()) return false;
    List<String> cmd =
        new ArrayList<>(List.of("git", "diff", "--name-only", from, to, "--", "."));
    cmd.addAll(IGNORED);
    Result r = run(root, cmd, StandardCharsets.UTF_8);
    return r.exitCode != 0 || !r.output.isBlank();
  }

  static int runGradle(Path root, Path log) throws Exception {
    boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
    // 명시적 상대 경로(.\gradlew.bat)로 실행한다. 절대 경로를 넘기면 &, ^ 같은 문자를 cmd가 잘못 해석하고,
    // NoDefaultCurrentDirectoryInExePath가 설정된 환경에서는 "gradlew.bat"만으로는 현재 폴더를 찾지 않는다.
    // (참고: 프로젝트 경로에 &가 있으면 gradlew.bat 자체가 동작하지 않는다. 이 경우 아래 infra 오류로 처리된다)
    // macOS/Linux는 sh로 실행해 gradlew 실행 권한이 없어도 동작하게 한다.
    List<String> cmd =
        new ArrayList<>(
            windows ? List.of("cmd", "/c", ".\\gradlew.bat") : List.of("sh", "./gradlew"));
    cmd.addAll(GRADLE_TASKS);
    cmd.addAll(List.of("--console=plain", "--warning-mode=none"));

    Process p =
        new ProcessBuilder(cmd)
            .directory(root.toFile())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile())
            .start();
    p.getOutputStream().close();
    if (!p.waitFor(GRADLE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
      p.descendants().forEach(ProcessHandle::destroyForcibly);
      p.destroyForcibly();
      Files.writeString(
          log,
          "\n[VerifyOnStop] " + GRADLE_TIMEOUT_SECONDS + "초 안에 끝나지 않아 중단했습니다.\n",
          nativeCharset(),
          java.nio.file.StandardOpenOption.APPEND);
      return 124;
    }
    return p.exitValue();
  }

  /** Gradle이 실행되어 빌드/테스트가 실패했는가 (실행 자체가 안 된 경우와 구분). */
  static boolean isBuildFailure(String output) {
    return output.contains("FAILURE:") || output.contains("BUILD FAILED");
  }

  /** Gradle/cmd 출력은 OS 기본 인코딩(한국어 Windows는 MS949)으로 나온다. */
  static Charset nativeCharset() {
    String name = System.getProperty("native.encoding");
    try {
      return name == null ? Charset.defaultCharset() : Charset.forName(name);
    } catch (Exception e) {
      return Charset.defaultCharset();
    }
  }

  static String readLog(Path log) throws Exception {
    if (!Files.exists(log)) return "";
    return new String(Files.readAllBytes(log), nativeCharset()).replace("\r\n", "\n");
  }

  static String git(Path root, String... args) throws Exception {
    List<String> cmd = new ArrayList<>(List.of("git"));
    cmd.addAll(List.of(args));
    Result r = run(root, cmd, StandardCharsets.UTF_8);
    return r.exitCode == 0 ? r.output : null;
  }

  static Result run(Path dir, List<String> cmd, Charset charset) throws Exception {
    Process p = new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
    p.getOutputStream().close();
    String out = new String(p.getInputStream().readAllBytes(), charset);
    return new Result(p.waitFor(), out);
  }

  static Properties loadState(Path file) {
    Properties props = new Properties();
    try (var in = Files.newInputStream(file)) {
      props.load(in);
    } catch (Exception ignored) {
      // 첫 실행이거나 build/가 지워진 경우
    }
    return props;
  }

  static void saveState(Path file, Properties props) {
    try {
      Files.createDirectories(file.getParent());
      try (var out = Files.newOutputStream(file)) {
        props.store(out, "VerifyOnStop state");
      }
    } catch (Exception ignored) {
      // 상태 저장 실패는 다음 실행에서 검증을 한 번 더 할 뿐이다
    }
  }

  static String tail(String text, int n) {
    String[] lines =
        Arrays.stream(text.split("\n"))
            .filter(l -> !l.startsWith("> Task ") && !l.contains("Sharing is only supported"))
            .toArray(String[]::new);
    int from = Math.max(0, lines.length - n);
    return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
  }

  static String jsonEscape(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
        }
      }
    }
    return b.toString();
  }

  record Result(int exitCode, String output) {}
}
