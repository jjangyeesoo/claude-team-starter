// PreToolUse hook (Edit|Write|NotebookEdit): 보호 대상 파일 수정을 차단한다.
// JDK 17+만 있으면 OS와 무관하게 동작한다 (java ProtectFiles.java, 단일 파일 실행).
// 종료 코드 2 = 차단. stderr 메시지는 Claude에게 피드백으로 전달된다.
//
// 1차 방어선은 settings.json의 deny 규칙이고, 이 hook은 2차 방어선이다.
// 이 hook은 파일 편집 도구만 본다. 스크립트가 간접적으로 파일을 쓰는 경우는 막지 못한다 (README 참고).
// 입력을 해석하지 못하면 예외로 종료 코드 1이 되어 편집이 허용된다(fail-open). 도구가 멈추는 것보다 낫다고 판단했다.

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtectFiles {

  private static final Pattern PATH_FIELD =
      Pattern.compile("\"(?:file_path|notebook_path)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

  public static void main(String[] args) throws Exception {
    String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    Matcher m = PATH_FIELD.matcher(toolInput(input));
    if (!m.find()) {
      return;
    }
    String original = unescape(m.group(1));
    String reason = protectedReason(normalize(original));
    if (reason != null) {
      PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
      err.println(
          "Blocked by .claude/hooks/ProtectFiles.java: "
              + original
              + " ("
              + reason
              + "). 이 파일은 사람이 직접 수정해야 합니다. 필요하면 사용자에게 요청하세요.");
      System.exit(2);
    }
  }

  /** tool_input 객체 부분만 대상으로 한다 (content 안에 "file_path" 문자열이 있어도 오탐하지 않도록). */
  static String toolInput(String input) {
    int i = input.indexOf("\"tool_input\"");
    return i < 0 ? input : input.substring(i);
  }

  /** 소문자, 슬래시 구분자, 맨 앞 '/'로 정규화한다. Windows·macOS 파일시스템은 대소문자를 구분하지 않는다. */
  static String normalize(String path) {
    String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
    return p.startsWith("/") ? p : "/" + p;
  }

  static String protectedReason(String path) {
    String name = path.substring(path.lastIndexOf('/') + 1);
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
