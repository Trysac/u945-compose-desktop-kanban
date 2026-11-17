// Runs the Gradle wrapper of this repository without a shell, so the same command works on Windows
// and Linux: `java scripts/Gradle.java <tasks>` is `./gradlew <tasks>`. Gradle itself is the
// version gradle/wrapper/gradle-wrapper.properties pins by its distribution SHA-256.
//
// With FABRICA_BUILD_DIR set (the factory's gates image), Gradle's project cache and Kotlin's
// session files go there too, so nothing but sources lands in the mounted repository.
// Single-file source program: Java 21, standard library only.

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** The Gradle wrapper launcher. */
public final class Gradle {

  static final String WRAPPER_JAR = "gradle/wrapper/gradle-wrapper.jar";

  private Gradle() {}

  public static void main(String[] args) {
    if (!Files.isRegularFile(Path.of(WRAPPER_JAR))) {
      System.err.println("gradle: run from the repository root (" + WRAPPER_JAR + " not found)");
      System.exit(2);
    }
    try {
      System.exit(new ProcessBuilder(argv(List.of(args))).inheritIO().start().waitFor());
    } catch (IOException | InterruptedException e) {
      System.err.println("gradle: " + e.getMessage());
      System.exit(1);
    }
  }

  /** The java executable running this program. */
  static String java() {
    return ProcessHandle.current().info().command().orElse("java");
  }

  /** The wrapper command line for the given Gradle arguments. */
  static List<String> argv(List<String> args) {
    List<String> argv = new ArrayList<>();
    argv.addAll(
        List.of(
            java(),
            "-Xmx64m",
            "-Xms64m",
            "-Dorg.gradle.appname=gradlew",
            "-jar",
            WRAPPER_JAR,
            "--console=plain"));
    String dir = System.getenv("FABRICA_BUILD_DIR");
    if (dir != null && !dir.isEmpty()) {
      argv.add("--project-cache-dir=" + Path.of(dir, "gradle-project"));
      argv.add("-Pkotlin.project.persistent.dir=" + Path.of(dir, "kotlin"));
    }
    argv.addAll(args);
    return argv;
  }
}
