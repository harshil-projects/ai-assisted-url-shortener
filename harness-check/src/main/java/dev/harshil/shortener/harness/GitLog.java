package dev.harshil.shortener.harness;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * The non-merge commits after the {@code baseline} tag, read through the git command line. When
 * {@code .git} is absent, git is not on the PATH, or the tag does not exist, the log is marked
 * skipped with a reason and rules that need history are skipped rather than failed.
 */
public final class GitLog {

  private static final String BASELINE_TAG = "baseline";
  private static final String FIELD_SEPARATOR = "\u001f";
  private static final String RECORD_SEPARATOR = "\u001e";
  private static final String LOG_FORMAT = "%H%x1f%an%x1f%s%x1f%b%x1e";
  private static final String TRAILER_PREFIX = "Task:";
  private static final long TIMEOUT_SECONDS = 60;

  /**
   * One commit from the log.
   *
   * @param sha the full commit hash
   * @param author the author name
   * @param subject the first line of the message
   * @param body the remaining message lines, possibly empty
   */
  public record Commit(String sha, String author, String subject, String body) {

    /** True when the author is an automation account such as Dependabot. */
    public boolean isBot() {
      String name = author.toLowerCase(Locale.ROOT);
      return name.contains("[bot]") || name.contains("dependabot");
    }

    /** The task ids named on {@code Task:} trailer lines, in order; empty when there is none. */
    public List<String> taskReferences() {
      List<String> references = new ArrayList<>();
      for (String line : body.lines().toList()) {
        String trimmed = line.trim();
        if (!trimmed.startsWith(TRAILER_PREFIX)) {
          continue;
        }
        for (String token : trimmed.substring(TRAILER_PREFIX.length()).trim().split("[,\\s]+")) {
          if (!token.isEmpty()) {
            references.add(token);
          }
        }
      }
      return references;
    }

    /** The short hash and subject, for violation messages. */
    public String label() {
      return sha.substring(0, Math.min(12, sha.length())) + " " + subject;
    }
  }

  private record Result(int exitCode, String output) {}

  private final Path root;
  private final boolean skipped;
  private final String skipReason;
  private final List<Commit> commits;

  private GitLog(Path root, boolean skipped, String skipReason, List<Commit> commits) {
    this.root = root;
    this.skipped = skipped;
    this.skipReason = skipReason;
    this.commits = commits;
  }

  /** Reads the log for the repository at the root, or returns a skipped log with the reason. */
  public static GitLog load(Path root) {
    if (!Files.exists(root.resolve(".git"))) {
      return unavailable(root, "no .git directory under " + root);
    }
    Optional<Result> tag =
        run(root, "git", "rev-parse", "--verify", "--quiet", "refs/tags/" + BASELINE_TAG);
    if (tag.isEmpty()) {
      return unavailable(root, "git is not on the PATH or could not be started");
    }
    if (tag.get().exitCode() != 0) {
      return unavailable(root, "tag '" + BASELINE_TAG + "' does not exist");
    }
    Optional<Result> log =
        run(root, "git", "log", "--format=" + LOG_FORMAT, "--no-merges", BASELINE_TAG + "..HEAD");
    if (log.isEmpty() || log.get().exitCode() != 0) {
      String detail = log.map(Result::output).orElse("").trim();
      return unavailable(root, "git log failed" + (detail.isEmpty() ? "" : ": " + detail));
    }
    return new GitLog(root, false, "", parse(log.get().output()));
  }

  /** A log that could not be read, carrying the reason for skipping history rules. */
  public static GitLog unavailable(Path root, String reason) {
    return new GitLog(root, true, reason, List.of());
  }

  /** A log built from known commits, for tests that must not depend on a real repository. */
  static GitLog of(Path root, List<Commit> commits) {
    return new GitLog(root, false, "", List.copyOf(commits));
  }

  /** True when history could not be read; see {@link #skipReason()}. */
  public boolean skipped() {
    return skipped;
  }

  /** Why history was skipped, or the empty string when it was read. */
  public String skipReason() {
    return skipReason;
  }

  /** The non-merge commits after the baseline tag, newest first; empty when skipped. */
  public List<Commit> commits() {
    return commits;
  }

  /** The commits whose {@code Task:} trailer names the task. */
  public List<Commit> commitsFor(String taskId) {
    return commits.stream().filter(commit -> commit.taskReferences().contains(taskId)).toList();
  }

  /** The repository-relative paths changed by the commit; empty when skipped or on failure. */
  public List<String> changedFiles(String sha) {
    if (skipped) {
      return List.of();
    }
    Optional<Result> result =
        run(
            root,
            "git",
            "-c",
            "core.quotepath=false",
            "diff-tree",
            "--no-commit-id",
            "--name-only",
            "-r",
            sha);
    if (result.isEmpty() || result.get().exitCode() != 0) {
      return List.of();
    }
    return result.get().output().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
  }

  static List<Commit> parse(String output) {
    List<Commit> commits = new ArrayList<>();
    for (String entry : output.split(RECORD_SEPARATOR)) {
      String chunk = entry.strip();
      if (chunk.isEmpty()) {
        continue;
      }
      String[] fields = chunk.split(FIELD_SEPARATOR, 4);
      if (fields.length < 3) {
        continue;
      }
      String body = fields.length == 4 ? fields[3] : "";
      commits.add(new Commit(fields[0].trim(), fields[1], fields[2], body));
    }
    return List.copyOf(commits);
  }

  private static Optional<Result> run(Path directory, String... command) {
    ProcessBuilder builder =
        new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true);
    try {
      Process process = builder.start();
      byte[] output;
      try (InputStream in = process.getInputStream()) {
        output = in.readAllBytes();
      }
      if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return Optional.empty();
      }
      String text = new String(output, StandardCharsets.UTF_8);
      return Optional.of(new Result(process.exitValue(), text));
    } catch (IOException e) {
      return Optional.empty();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return Optional.empty();
    }
  }
}
