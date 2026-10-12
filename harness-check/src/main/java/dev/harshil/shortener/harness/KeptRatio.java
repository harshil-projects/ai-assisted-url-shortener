package dev.harshil.shortener.harness;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * Computes, for one task, the fraction of lines the AI added in its raw patch that still appear
 * verbatim in the files the ledger lists for that task. Whitespace-only lines are ignored and lines
 * are compared after trimming, so the ratio is a coarse but computed signal.
 */
public final class KeptRatio {

  private static final String ADDED_LINE_PREFIX = "+";
  private static final String FILE_HEADER_PREFIX = "+++";

  private KeptRatio() {}

  /**
   * The kept ratio for a task, or empty when no raw patch exists or it adds no non-blank lines. The
   * patches are those named by the task's ledger entries, falling back to the conventional {@code
   * harness/evidence/T-NNN/ai-raw.patch} location when no entry names one.
   */
  public static OptionalDouble compute(HarnessRepo repo, String taskId, List<LedgerEntry> entries) {
    Set<String> added = new HashSet<>();
    for (String patch : patchPaths(taskId, entries)) {
      repo.readText(repo.resolve(patch)).ifPresent(text -> added.addAll(addedLines(text)));
    }
    if (added.isEmpty()) {
      return OptionalDouble.empty();
    }
    Set<String> committed = new HashSet<>();
    for (LedgerEntry entry : entries) {
      for (FileChange file : entry.files()) {
        if (file.path() != null && !file.path().isBlank()) {
          repo.readText(repo.resolve(file.path()))
              .ifPresent(text -> committed.addAll(trimmedLines(text)));
        }
      }
    }
    long kept = added.stream().filter(committed::contains).count();
    return OptionalDouble.of((double) kept / added.size());
  }

  /** The non-blank, trimmed content of every added line in a unified diff. */
  static Set<String> addedLines(String patch) {
    Set<String> lines = new HashSet<>();
    for (String line : patch.lines().toList()) {
      if (!line.startsWith(ADDED_LINE_PREFIX) || line.startsWith(FILE_HEADER_PREFIX)) {
        continue;
      }
      String content = line.substring(ADDED_LINE_PREFIX.length()).trim();
      if (!content.isEmpty()) {
        lines.add(content);
      }
    }
    return lines;
  }

  /** The non-blank, trimmed lines of a text. */
  static Set<String> trimmedLines(String text) {
    Set<String> lines = new HashSet<>();
    for (String line : text.lines().toList()) {
      String content = line.trim();
      if (!content.isEmpty()) {
        lines.add(content);
      }
    }
    return lines;
  }

  private static Set<String> patchPaths(String taskId, List<LedgerEntry> entries) {
    Set<String> paths = new LinkedHashSet<>();
    for (LedgerEntry entry : entries) {
      if (entry.rawOutput() != null && !entry.rawOutput().isBlank()) {
        paths.add(entry.rawOutput());
      }
    }
    if (paths.isEmpty()) {
      paths.add("harness/evidence/" + taskId + "/ai-raw.patch");
    }
    return paths;
  }
}
