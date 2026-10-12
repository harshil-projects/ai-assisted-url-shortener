package dev.harshil.shortener.harness;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * One static method per harness-check rule, HC-01 to HC-12. Each method returns the violations it
 * found and never throws; an empty harness yields no violations.
 */
public final class Rules {

  private static final String PROMPT_V1 = "v1.md";

  private Rules() {}

  /** HC-01: task specs have the required fields, valid enum values, and ids matching file names. */
  public static List<Violation> hc01(HarnessRepo repo) {
    List<Violation> out = new ArrayList<>(repo.loadProblems("HC-01"));
    for (TaskSpec task : repo.tasks()) {
      out.addAll(TaskSpecChecks.requiredFields(task));
    }
    return out;
  }

  /** HC-02: depends_on entries refer to existing tasks and form no cycle. */
  public static List<Violation> hc02(HarnessRepo repo) {
    return TaskSpecChecks.dependencies(repo.tasks());
  }

  /** HC-03: every done task has a ledger entry. */
  public static List<Violation> hc03(HarnessRepo repo) {
    List<Violation> out = new ArrayList<>();
    for (TaskSpec task : doneTasks(repo)) {
      if (repo.ledger().entriesFor(task.key()).isEmpty()) {
        out.add(new Violation("HC-03", task.key(), "done task has no ledger entry"));
      }
    }
    return out;
  }

  /** HC-04: every done task has a passing gate record whose results are complete. */
  public static List<Violation> hc04(HarnessRepo repo) {
    List<Violation> out = new ArrayList<>();
    for (TaskSpec task : doneTasks(repo)) {
      out.addAll(LedgerChecks.passingGate(task, repo.ledger()));
    }
    return out;
  }

  /** HC-05: every high-impact done task has a sign-off whose review file exists. */
  public static List<Violation> hc05(HarnessRepo repo) {
    List<Violation> out = new ArrayList<>();
    for (TaskSpec task : doneTasks(repo)) {
      if (!task.isHighImpact()) {
        continue;
      }
      List<SignOff> signoffs = repo.ledger().signoffsFor(task.key());
      if (signoffs.isEmpty()) {
        out.add(new Violation("HC-05", task.key(), "high-impact done task has no sign-off"));
        continue;
      }
      for (SignOff signoff : signoffs) {
        if (!repo.fileExists(signoff.review())) {
          out.add(
              new Violation(
                  "HC-05", task.key(), "sign-off review file does not exist: " + signoff.review()));
        }
      }
    }
    return out;
  }

  /** HC-06: ledger references resolve to existing tasks, prompts, patches, logs, and files. */
  public static List<Violation> hc06(HarnessRepo repo) {
    return LedgerChecks.references(repo);
  }

  /** HC-07: non-merge, non-bot commits after the baseline tag carry a valid Task trailer. */
  public static List<Violation> hc07(HarnessRepo repo, GitLog gitLog) {
    List<Violation> out = new ArrayList<>();
    if (gitLog.skipped()) {
      return out;
    }
    for (GitLog.Commit commit : gitLog.commits()) {
      if (commit.isBot()) {
        continue;
      }
      List<String> references = commit.taskReferences();
      if (references.isEmpty()) {
        out.add(new Violation("HC-07", commit.label(), "commit has no 'Task: T-NNN' trailer"));
        continue;
      }
      for (String reference : references) {
        if (repo.task(reference).isEmpty()) {
          out.add(
              new Violation(
                  "HC-07", commit.label(), "Task trailer references missing task " + reference));
        }
      }
    }
    return out;
  }

  /** HC-08: done tasks with activities other than exactly [document] have a v1 prompt. */
  public static List<Violation> hc08(HarnessRepo repo) {
    List<Violation> out = new ArrayList<>();
    for (TaskSpec task : doneTasks(repo)) {
      if (task.isDocumentOnly()) {
        continue;
      }
      if (!Files.isRegularFile(repo.promptsDir().resolve(task.key()).resolve(PROMPT_V1))) {
        out.add(
            new Violation(
                "HC-08",
                task.key(),
                "missing prompt harness/prompts/" + task.key() + "/" + PROMPT_V1));
      }
    }
    return out;
  }

  /** HC-09: the AI configuration files contain no zero-width or bidirectional code points. */
  public static List<Violation> hc09(HarnessRepo repo) {
    return FileScanner.hiddenUnicode(repo);
  }

  /** HC-10: no text file in the working tree matches a secret pattern. */
  public static List<Violation> hc10(HarnessRepo repo) {
    return FileScanner.secrets(repo);
  }

  /** HC-11: every ledger entry with a rejected file has at least one rejected note. */
  public static List<Violation> hc11(HarnessRepo repo) {
    return LedgerChecks.rejectedNotes(repo.ledger());
  }

  /** HC-12: changed main-source files were predicted by the task's impact analysis. */
  public static List<Violation> hc12(HarnessRepo repo, GitLog gitLog) {
    return ImpactAnalysis.check(repo, gitLog);
  }

  private static List<TaskSpec> doneTasks(HarnessRepo repo) {
    return repo.tasks().stream().filter(TaskSpec::isDone).toList();
  }
}
