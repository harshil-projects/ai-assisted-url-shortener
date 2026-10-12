package dev.harshil.shortener.harness;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule HC-12: compares the main-source files a task's commits actually changed with the files its
 * impact analysis predicted. Tests, docs, and everything outside {@code src/main/} are exempt.
 */
final class ImpactAnalysis {

  private static final String SECTION_TITLE = "Predicted files";
  private static final String MAIN_SOURCE_MARKER = "src/main/";
  private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s*(.+?)\\s*#*\\s*$");
  private static final Pattern BULLET = Pattern.compile("^\\s*[-*+]\\s+(.*)$");

  private ImpactAnalysis() {}

  /**
   * HC-12 over every done task that names an impact analysis; empty when git history is skipped.
   * Tasks that are still planned are exempt: their analysis file is written when the task runs, so
   * its absence beforehand is the expected state, not a gap.
   */
  static List<Violation> check(HarnessRepo repo, GitLog gitLog) {
    List<Violation> out = new ArrayList<>();
    if (gitLog.skipped()) {
      return out;
    }
    for (TaskSpec task : repo.tasks()) {
      boolean namesAnalysis = task.impactAnalysis() != null && !task.impactAnalysis().isBlank();
      if (namesAnalysis && task.isDone()) {
        checkTask(repo, gitLog, task, out);
      }
    }
    return out;
  }

  private static void checkTask(
      HarnessRepo repo, GitLog gitLog, TaskSpec task, List<Violation> out) {
    Path file = repo.resolve(task.impactAnalysis());
    if (!Files.isRegularFile(file)) {
      out.add(hc12(task.key(), "impact_analysis file does not exist: " + task.impactAnalysis()));
      return;
    }
    Optional<List<String>> predicted = predictedFiles(repo.readText(file).orElse(""));
    if (predicted.isEmpty()) {
      out.add(
          hc12(
              task.key(),
              "impact analysis has no '" + SECTION_TITLE + "' section: " + task.impactAnalysis()));
      return;
    }
    Set<String> changed = new TreeSet<>();
    for (GitLog.Commit commit : gitLog.commitsFor(task.key())) {
      changed.addAll(gitLog.changedFiles(commit.sha()));
    }
    for (String path : changed) {
      if (path.contains(MAIN_SOURCE_MARKER) && !isPredicted(path, predicted.get())) {
        out.add(hc12(task.key(), "main-source file changed but not predicted: " + path));
      }
    }
  }

  /**
   * The bullet items under the "Predicted files" heading of a Markdown document, or empty when the
   * heading is absent. Items may be bare paths or backtick-quoted paths followed by a note.
   */
  static Optional<List<String>> predictedFiles(String markdown) {
    List<String> paths = new ArrayList<>();
    boolean found = false;
    boolean inSection = false;
    for (String line : markdown.lines().toList()) {
      Matcher heading = HEADING.matcher(line);
      if (heading.matches()) {
        inSection = heading.group(1).trim().equalsIgnoreCase(SECTION_TITLE);
        found = found || inSection;
        continue;
      }
      Matcher bullet = BULLET.matcher(line);
      if (inSection && bullet.matches()) {
        String path = cleanPath(bullet.group(1));
        if (!path.isEmpty()) {
          paths.add(path);
        }
      }
    }
    return found ? Optional.of(List.copyOf(paths)) : Optional.empty();
  }

  /** Extracts the path from a bullet: the first backtick span, else the first whitespace token. */
  static String cleanPath(String bulletText) {
    String text = bulletText.trim();
    int open = text.indexOf('`');
    int close = open >= 0 ? text.indexOf('`', open + 1) : -1;
    if (open >= 0 && close > open) {
      text = text.substring(open + 1, close);
    } else {
      int space = text.indexOf(' ');
      text = space > 0 ? text.substring(0, space) : text;
    }
    text = text.replace('\\', '/').trim();
    while (text.startsWith("./")) {
      text = text.substring(2);
    }
    return text;
  }

  /** True when the changed path equals a predicted path or ends with one as a suffix. */
  static boolean isPredicted(String changed, List<String> predicted) {
    for (String path : predicted) {
      if (changed.equals(path) || changed.endsWith("/" + path)) {
        return true;
      }
    }
    return false;
  }

  private static Violation hc12(String subject, String message) {
    return new Violation("HC-12", subject, message);
  }
}
