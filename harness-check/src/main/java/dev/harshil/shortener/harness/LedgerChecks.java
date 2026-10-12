package dev.harshil.shortener.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Ledger consistency checks behind rules HC-04, HC-06, and HC-11. */
final class LedgerChecks {

  private static final String SKIPPED_PREFIX = "skipped:";

  private LedgerChecks() {}

  /** HC-04 for one done task: a passing gate record exists and its results are complete. */
  static List<Violation> passingGate(TaskSpec task, Ledger ledger) {
    List<GateRecord> passing =
        ledger.gatesFor(task.key()).stream().filter(GateRecord::isPass).toList();
    if (passing.isEmpty()) {
      return List.of(hc04(task.key(), "done task has no gate record with overall: pass"));
    }
    GateRecord latest = passing.get(passing.size() - 1);
    List<Violation> out = new ArrayList<>();
    for (String key : Vocabulary.GATE_KEYS) {
      String value = latest.results().get(key);
      if (value == null) {
        out.add(hc04(task.key(), "passing gate record lacks result key " + key));
      } else if (!isPassOrSkippedWithReason(value)) {
        out.add(
            hc04(
                task.key(),
                "gate result "
                    + key
                    + " is '"
                    + value
                    + "'; expected pass or "
                    + "skipped: <reason>"));
      }
    }
    return out;
  }

  /** True for {@code pass} or {@code skipped: <non-blank reason>}. */
  static boolean isPassOrSkippedWithReason(String value) {
    if (Vocabulary.PASS.equals(value)) {
      return true;
    }
    return value.startsWith(SKIPPED_PREFIX) && !value.substring(SKIPPED_PREFIX.length()).isBlank();
  }

  /** HC-06 over the whole ledger: every reference resolves to an existing task or file. */
  static List<Violation> references(HarnessRepo repo) {
    List<Violation> out = new ArrayList<>(repo.loadProblems("HC-06"));
    for (LedgerEntry entry : repo.ledger().entries()) {
      checkEntry(repo, entry, out);
    }
    for (GateRecord gate : repo.ledger().gates()) {
      if (gate.log() == null || gate.log().isBlank()) {
        out.add(hc06(gate.subject(), "gate record has no log file"));
      } else if (!repo.fileExists(gate.log())) {
        out.add(hc06(gate.subject(), "gate log file does not exist: " + gate.log()));
      }
    }
    return out;
  }

  /** HC-11 over the whole ledger: rejected files come with at least one rejected note. */
  static List<Violation> rejectedNotes(Ledger ledger) {
    List<Violation> out = new ArrayList<>();
    for (LedgerEntry entry : ledger.entries()) {
      long rejected = entry.countFiles(Vocabulary.REJECTED);
      boolean hasNote = entry.rejectedNotes().stream().anyMatch(note -> !note.isBlank());
      if (rejected > 0 && !hasNote) {
        out.add(
            new Violation(
                "HC-11",
                entry.subject(),
                rejected + " file(s) marked rejected but rejected_notes is empty"));
      }
    }
    return out;
  }

  private static void checkEntry(HarnessRepo repo, LedgerEntry entry, List<Violation> out) {
    String subject = entry.subject();
    Optional<TaskSpec> task = entry.task() == null ? Optional.empty() : repo.task(entry.task());
    if (task.isEmpty()) {
      out.add(hc06(subject, "ledger entry references a task that does not exist"));
    }
    for (String prompt : entry.prompts()) {
      if (!repo.fileExists(prompt)) {
        out.add(hc06(subject, "prompt file does not exist: " + prompt));
      }
    }
    if (task.isPresent() && !task.get().isDocumentOnly()) {
      checkRawOutput(repo, entry, out);
    }
    for (FileChange file : entry.files()) {
      checkFile(repo, subject, file, out);
    }
  }

  private static void checkRawOutput(HarnessRepo repo, LedgerEntry entry, List<Violation> out) {
    if (entry.rawOutput() == null || entry.rawOutput().isBlank()) {
      out.add(hc06(entry.subject(), "ledger entry for a non-document task has no raw_output"));
    } else if (!repo.fileExists(entry.rawOutput())) {
      out.add(hc06(entry.subject(), "raw_output patch does not exist: " + entry.rawOutput()));
    }
  }

  private static void checkFile(
      HarnessRepo repo, String subject, FileChange file, List<Violation> out) {
    if (file.path() == null || file.path().isBlank()) {
      out.add(hc06(subject, "a files item has no path"));
      return;
    }
    if (file.status() == null || !Vocabulary.FILE_STATUSES.contains(file.status())) {
      out.add(
          hc06(
              subject,
              "file "
                  + file.path()
                  + " has status '"
                  + file.status()
                  + "'; allowed: "
                  + Vocabulary.describe(Vocabulary.FILE_STATUSES)));
    }
    // A rejected draft may have been discarded entirely, so only kept files must exist.
    if (!file.hasStatus(Vocabulary.REJECTED) && !repo.fileExists(file.path())) {
      out.add(hc06(subject, "listed file does not exist: " + file.path()));
    }
  }

  private static Violation hc04(String subject, String message) {
    return new Violation("HC-04", subject, message);
  }

  private static Violation hc06(String subject, String message) {
    return new Violation("HC-06", subject, message);
  }
}
