// Stop hook: Claude가 응답을 마칠 때, 소스가 바뀌었으면 포맷 + 테스트를 실행한다.
// - 변경 없음 / 마지막 통과 이후 변경 없음 → 즉시 통과 (빠름)
// - 실패 → 종료 코드 2로 멈춤을 막고, 실패 로그를 Claude에게 전달해 계속 고치게 한다
// - 이미 한 번 막았는데(stop_hook_active) 또 실패 → 더 막지 않고 사용자에게 알린다 (무한 루프 방지)
// JDK만 있으면 OS와 무관하게 동작한다. git이 PATH에 있어야 한다.

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

public class VerifyOnStop {

  // 이 경로 아래가 바뀌었을 때만 검증한다. 스택을 바꾸면 이 목록과 GRADLE_TASKS를 수정한다.
  private static final List<String> WATCHED =
      List.of("src", "build.gradle.kts", "settings.gradle.kts");
  private static final List<String> GRADLE_TASKS = List.of("spotlessApply", "test");
  private static final int TAIL_LINES = 40;

  public static void main(String[] args) throws Exception {
    String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    boolean alreadyBlocked = input.matches("(?s).*\"stop_hook_active\"\\s*:\\s*true.*");

    Path root = Path.of(System.getenv().getOrDefault("CLAUDE_PROJECT_DIR", ".")).toAbsolutePath();
    Path cache = root.resolve("build/claude-verify/last-pass");

    String before = fingerprint(root);
    if (before == null || before.isEmpty()) return; // git 저장소가 아니거나 변경 없음
    if (Files.exists(cache) && Files.readString(cache).equals(before)) return; // 이미 통과한 상태

    Result result = runGradle(root);
    if (result.exitCode == 0) {
      Files.createDirectories(cache.getParent());
      Files.writeString(cache, fingerprint(root)); // spotlessApply로 바뀐 뒤의 상태를 기록
      return;
    }

    String tail = tail(result.output, TAIL_LINES);
    if (alreadyBlocked) {
      String msg = "VerifyOnStop: 테스트가 여전히 실패합니다. 직접 확인이 필요합니다.";
      System.out.println("{\"systemMessage\": \"" + msg + "\"}");
      return;
    }
    System.err.println(
        "VerifyOnStop: `gradlew " + String.join(" ", GRADLE_TASKS) + "` 실패. "
            + "근본 원인을 고치고 다시 테스트하세요 (테스트를 지우거나 skip하지 말 것). "
            + "상세: build/reports/tests/test/index.html\n--- 마지막 " + TAIL_LINES + "줄 ---\n" + tail);
    System.exit(2);
  }

  /** 감시 대상 경로의 변경 파일 목록과 내용을 해시한다. 변경이 없으면 "", git이 없으면 null. */
  static String fingerprint(Path root) throws Exception {
    List<String> cmd = new ArrayList<>(List.of("git", "status", "--porcelain", "-uall", "--"));
    cmd.addAll(WATCHED);
    Result status = run(root, cmd);
    if (status.exitCode != 0) return null;
    if (status.output.isBlank()) return "";

    MessageDigest sha = MessageDigest.getInstance("SHA-256");
    for (String line : status.output.split("\n")) {
      if (line.length() < 4) continue;
      String rel = line.substring(3).trim();
      if (rel.contains(" -> ")) rel = rel.substring(rel.indexOf(" -> ") + 4); // rename
      rel = rel.replace("\"", "");
      sha.update(line.getBytes(StandardCharsets.UTF_8));
      Path file = root.resolve(rel);
      if (Files.isRegularFile(file)) sha.update(Files.readAllBytes(file));
    }
    return HexFormat.of().formatHex(sha.digest());
  }

  static Result runGradle(Path root) throws Exception {
    boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
    List<String> cmd =
        new ArrayList<>(
            windows
                ? List.of("cmd", "/c", root.resolve("gradlew.bat").toString())
                : List.of(root.resolve("gradlew").toString()));
    cmd.addAll(GRADLE_TASKS);
    cmd.addAll(List.of("--console=plain", "--warning-mode=none"));
    return run(root, cmd);
  }

  static Result run(Path dir, List<String> cmd) throws Exception {
    Process p =
        new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true).start();
    p.getOutputStream().close();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Result(p.waitFor(), out.replace("\r\n", "\n"));
  }

  static String tail(String text, int n) {
    String[] lines = text.split("\n");
    int from = Math.max(0, lines.length - n);
    return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
  }

  record Result(int exitCode, String output) {}

}
