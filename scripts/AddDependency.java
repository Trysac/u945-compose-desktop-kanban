// Adds one dependency to build.gradle.kts at an exact version, then rewrites gradle.lockfile
// (Gradle has no command for the first step). Factory-owned: agents never run it; they list
// dependencies and the factory adds the allowed ones.
//
// Usage: java scripts/AddDependency.java [--test | --optional] group/artifact [spec]
//        java scripts/AddDependency.java [--test | --optional] --git owner repo tag name
//
// spec: empty (the latest release on Maven Central), an exact version (1.2.3 or ==1.2.3) or a
// lower bound with an optional upper bound (>=1.2 or >=1.2,<2): the latest release must satisfy it
// and is written as the exact version. --test adds a testImplementation dependency; --optional an
// implementation one (Gradle has no optional dependencies, so an extra is a library dependency).
// --git adds a factory repository at its tag through JitPack (com.github.<owner>:<repo>:<tag>).

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds a dependency to build.gradle.kts and relocks. */
public final class AddDependency {

  static final String CENTRAL = "https://repo1.maven.org/maven2/";
  static final String JITPACK = "https://jitpack.io";
  static final Pattern NAME = Pattern.compile("^([A-Za-z0-9_.-]+)[/:]([A-Za-z0-9_.-]+)$");
  static final Pattern VERSION = Pattern.compile("^[0-9][A-Za-z0-9._-]*$");
  // Milestones, release candidates, previews and snapshots (4.0.0-M1, 2.0-rc1, 1.0-SNAPSHOT).
  static final Pattern PRE_RELEASE =
      Pattern.compile(
          "(?i)[.-](?:alpha|beta|a|b|m|rc|cr|ea|preview|milestone|snapshot)[.-]?\\d*(?:$|[.-])");

  private AddDependency() {}

  /** A refused or failed addition. */
  static final class Refused extends Exception {
    private static final long serialVersionUID = 1L;

    Refused(String message) {
      super(message);
    }
  }

  public static void main(String[] args) {
    try {
      add(Path.of("build.gradle.kts"), List.of(args));
      relock();
    } catch (Refused | IOException | InterruptedException e) {
      System.err.println("add-dependency: " + e.getMessage());
      System.exit(1);
    }
  }

  static void add(Path build, List<String> args) throws Refused, IOException, InterruptedException {
    List<String> rest = new ArrayList<>(args);
    String scope = "";
    if (!rest.isEmpty() && (rest.get(0).equals("--test") || rest.get(0).equals("--optional"))) {
      scope = rest.remove(0);
    }
    String group;
    String artifact;
    String version;
    boolean jitpack = false;
    if (!rest.isEmpty() && rest.get(0).equals("--git")) {
      if (rest.size() != 5) {
        throw new Refused("usage: --git owner repo tag name");
      }
      group = "com.github." + rest.get(1);
      artifact = rest.get(2);
      version = rest.get(3);
      jitpack = true;
    } else {
      if (rest.isEmpty() || rest.size() > 2) {
        throw new Refused("usage: [--test | --optional] group/artifact [spec]");
      }
      Matcher m = NAME.matcher(rest.get(0));
      if (!m.matches()) {
        throw new Refused("a Gradle dependency is named group/artifact, got " + rest.get(0));
      }
      group = m.group(1);
      artifact = m.group(2);
      String spec = rest.size() == 2 ? rest.get(1).strip() : "";
      version = resolve(group, artifact, spec);
    }
    String text = Files.readString(build, StandardCharsets.UTF_8);
    text = insert(text, group, artifact, version, scope);
    if (jitpack) {
      text = withJitpack(text);
    }
    Files.writeString(build, text, StandardCharsets.UTF_8);
    System.out.println("added " + group + ":" + artifact + ":" + version);
  }

  /** The exact version a spec selects; only the latest release is ever considered. */
  static String resolve(String group, String artifact, String spec)
      throws Refused, IOException, InterruptedException {
    String exact = spec.startsWith("==") ? spec.substring(2).strip() : spec;
    if (VERSION.matcher(exact).matches()) {
      return exact;
    }
    String lower = null;
    String upper = null;
    if (!spec.isEmpty()) {
      for (String part : spec.split(",")) {
        String p = part.strip();
        if (p.startsWith(">=") && VERSION.matcher(p.substring(2).strip()).matches()) {
          lower = p.substring(2).strip();
        } else if (p.startsWith("<") && VERSION.matcher(p.substring(1).strip()).matches()) {
          upper = p.substring(1).strip();
        } else {
          throw new Refused("unsupported version spec " + spec + " (use 1.2.3, >=1.2 or >=1.2,<2)");
        }
      }
    }
    String latest = latestRelease(group, artifact);
    if ((lower != null && compare(latest, lower) < 0)
        || (upper != null && compare(latest, upper) >= 0)) {
      throw new Refused("the latest release " + latest + " does not satisfy " + spec);
    }
    return latest;
  }

  static String latestRelease(String group, String artifact)
      throws Refused, IOException, InterruptedException {
    String url = CENTRAL + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml";
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    HttpResponse<String> resp =
        client.send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (resp.statusCode() != 200) {
      throw new Refused(group + ":" + artifact + " not found on Maven Central (" + url + ")");
    }
    String latest = latestStable(resp.body());
    if (latest == null) {
      throw new Refused("no stable release of " + group + ":" + artifact + " on Maven Central");
    }
    return latest;
  }

  /**
   * The highest stable version listed in maven-metadata.xml. Its {@code <release>} element can name
   * a milestone (4.0.0-M1), so pre-releases are skipped here.
   */
  static String latestStable(String metadata) {
    Matcher m = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>").matcher(metadata);
    String best = null;
    while (m.find()) {
      String v = m.group(1);
      if (VERSION.matcher(v).matches()
          && !PRE_RELEASE.matcher(v).find()
          && (best == null || compare(v, best) > 0)) {
        best = v;
      }
    }
    return best;
  }

  /** Compares dotted versions numerically segment by segment; a qualifier sorts after numbers. */
  static int compare(String a, String b) {
    String[] x = a.split("[.-]");
    String[] y = b.split("[.-]");
    for (int i = 0; i < Math.max(x.length, y.length); i++) {
      String p = i < x.length ? x[i] : "0";
      String q = i < y.length ? y[i] : "0";
      boolean pn = p.chars().allMatch(Character::isDigit);
      boolean qn = q.chars().allMatch(Character::isDigit);
      int c =
          pn && qn
              ? Long.compare(Long.parseLong(p), Long.parseLong(q))
              : pn ? -1 : qn ? 1 : p.compareTo(q);
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }

  /** Start and end offsets of the body of the top-level block {@code name { ... }}, or null. */
  static int[] topLevelBlock(String text, String name) {
    Matcher m = Pattern.compile("(?m)^" + Pattern.quote(name) + " \\{\\n").matcher(text);
    if (!m.find()) {
      return null;
    }
    Matcher close = Pattern.compile("(?m)^\\}$").matcher(text);
    return close.find(m.end()) ? new int[] {m.end(), close.start()} : null;
  }

  static String insert(String text, String group, String artifact, String version, String scope)
      throws Refused {
    int[] deps = topLevelBlock(text, "dependencies");
    if (deps == null) {
      throw new Refused("build.gradle.kts has no top-level dependencies { } block");
    }
    String block = text.substring(deps[0], deps[1]);
    if (block.contains("\"" + group + ":" + artifact + ":")
        || block.contains("\"" + group + ":" + artifact + "\"")) {
      throw new Refused(group + ":" + artifact + " is already declared");
    }
    String configuration = scope.equals("--test") ? "testImplementation" : "implementation";
    String line = "    " + configuration + "(\"" + group + ":" + artifact + ":" + version + "\")\n";
    return text.substring(0, deps[1]) + line + text.substring(deps[1]);
  }

  static String withJitpack(String text) throws Refused {
    if (text.contains(JITPACK)) {
      return text;
    }
    int[] repos = topLevelBlock(text, "repositories");
    if (repos == null) {
      throw new Refused("build.gradle.kts has no top-level repositories { } block");
    }
    String line = "    maven(\"" + JITPACK + "\")\n";
    return text.substring(0, repos[1]) + line + text.substring(repos[1]);
  }

  /** Rewrites gradle.lockfile for every configuration (Gradle's own resolution). */
  static void relock() throws Refused, IOException, InterruptedException {
    String java = ProcessHandle.current().info().command().orElse("java");
    List<String> argv =
        List.of(java, "scripts/Gradle.java", "--quiet", "dependencies", "--write-locks");
    // The dependency report itself (stdout) is not needed; errors still reach stderr.
    int code =
        new ProcessBuilder(argv)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
            .waitFor();
    if (code != 0) {
      String shown = String.join(" ", argv.subList(1, argv.size()));
      throw new Refused("`" + shown + "` failed (exit " + code + ")");
    }
  }
}
