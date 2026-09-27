// PreToolUse hook (Edit|Write): 보호 대상 파일 수정을 차단한다.
// JDK만 있으면 OS와 무관하게 동작한다 (java ProtectFiles.java, JDK 11+ 단일 파일 실행).
// 종료 코드 2 = 차단. stderr 메시지는 Claude에게 피드백으로 전달된다.

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtectFiles {

  private static final Pattern FILE_PATH =
      Pattern.compile("\"file_path\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

  public static void main(String[] args) throws Exception {
    String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    Matcher m = FILE_PATH.matcher(input);
    if (!m.find()) {
      return;
    }
    String path = unescape(m.group(1)).replace('\\', '/');
    String name = path.substring(path.lastIndexOf('/') + 1);

    String reason = protectedReason(path, name);
    if (reason != null) {
      System.err.println(
          "Blocked by .claude/hooks/ProtectFiles.java: " + path + " (" + reason + "). "
              + "이 파일은 사람이 직접 수정해야 합니다. 필요하면 사용자에게 요청하세요.");
      System.exit(2);
    }
  }

  static String protectedReason(String path, String name) {
    if (name.equals(".env") || name.startsWith(".env.")) return "환경변수/비밀값 파일";
    if (name.startsWith("application-local.") || name.startsWith("application-secret."))
      return "로컬/비밀 Spring 설정";
    if (path.contains("/secrets/")) return "secrets 디렉터리";
    if (path.contains("/.git/")) return "git 내부 파일";
    if (path.contains("/gradle/wrapper/") || name.equals("gradlew") || name.equals("gradlew.bat"))
      return "Gradle wrapper (버전 변경은 ./gradlew wrapper로)";
    return null;
  }

  static String unescape(String s) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char n = s.charAt(++i);
        switch (n) {
          case 'n' -> out.append('\n');
          case 't' -> out.append('\t');
          case 'u' -> {
            out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
            i += 4;
          }
          default -> out.append(n); // \\ \" \/
        }
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
