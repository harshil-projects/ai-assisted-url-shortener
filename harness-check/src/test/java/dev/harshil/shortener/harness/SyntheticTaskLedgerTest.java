package dev.harshil.shortener.harness;

import static dev.harshil.shortener.harness.SyntheticHarness.messages;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves that the task and ledger rules fire on bad synthetic data and stay quiet on good data. */
class SyntheticTaskLedgerTest {

  @TempDir Path root;

  private SyntheticHarness harness;

  @BeforeEach
  void setUp() {
    harness = new SyntheticHarness(root);
  }

  @Test
  void emptyHarnessYieldsNoViolations() {
    HarnessRepo repo = harness.open();

    assertThat(allViolations(repo, harness.noHistory())).isEmpty();
    assertThat(repo.tasks()).isEmpty();
    assertThat(repo.ledger().entries()).isEmpty();
  }

  @Test
  void completeHarnessYieldsNoViolations() throws IOException {
    harness.writeComplete();

    assertThat(allViolations(harness.open(), harness.noHistory())).isEmpty();
  }

  @Test
  void hc01_detectsMissingFieldsUnknownEnumsAndIdMismatch() throws IOException {
    harness.write(
        "harness/tasks/T-001.yaml",
        "id: T-002\ntitle: Wrong id\nactivities: [implement, dance]\n"
            + "impact: high\nstatus: shipped\n");

    List<String> found = messages(Rules.hc01(harness.open()));

    assertThat(found)
        .anyMatch(message -> message.contains("does not match file name"))
        .anyMatch(message -> message.contains("activities has unknown value 'dance'"))
        .anyMatch(message -> message.contains("impact_reasons is required"))
        .anyMatch(message -> message.contains("status has unknown value 'shipped'"))
        .anyMatch(message -> message.contains("missing required field intent"))
        .anyMatch(message -> message.contains("missing required field technical_context"))
        .anyMatch(message -> message.contains("verification must be a non-empty list"));
  }

  @Test
  void hc01_reportsUnparseableTaskFileAsViolation() throws IOException {
    harness.write("harness/tasks/T-001.yaml", "id: [unclosed\n");

    List<Violation> found = Rules.hc01(harness.open());

    assertThat(found).hasSize(1);
    assertThat(found.get(0).message()).contains("cannot parse YAML");
  }

  @Test
  void hc02_detectsMissingDependencyAndCycle() throws IOException {
    harness
        .write("harness/tasks/T-001.yaml", withDependsOn("T-001", "[T-002]"))
        .write("harness/tasks/T-002.yaml", withDependsOn("T-002", "[T-001, T-999]"));

    List<String> found = messages(Rules.hc02(harness.open()));

    assertThat(found)
        .anyMatch(message -> message.contains("missing task T-999"))
        .anyMatch(message -> message.contains("forms a cycle: T-001 -> T-002 -> T-001"));
    assertThat(found).filteredOn(message -> message.contains("cycle")).hasSize(1);
  }

  @Test
  void hc03_detectsDoneTaskWithoutLedgerEntry() throws IOException {
    harness.write("harness/tasks/T-001.yaml", SyntheticHarness.VALID_TASK);

    List<Violation> found = Rules.hc03(harness.open());

    assertThat(found).hasSize(1);
    assertThat(found.get(0).subject()).isEqualTo(SyntheticHarness.TASK_ID);
  }

  @Test
  void ledgerRulesDetectIncompleteGatesMissingSignOffAndMissingFiles() throws IOException {
    harness
        .write("harness/tasks/T-001.yaml", SyntheticHarness.VALID_TASK)
        .write(
            "harness/ledger.yaml",
            """
            entries:
              - task: T-001
                prompts: [harness/prompts/T-001/v1.md]
                raw_output: harness/evidence/T-001/ai-raw.patch
                files:
                  - { path: src/main/java/App.java, status: generated }
                  - { path: src/test/java/AppTest.java, status: rejected }
                  - { path: src/main/java/Other.java, status: kept }
            gates:
              - task: T-001
                overall: pass
                results: { format: pass, style: pass, sast: pass, tests: fail, perf: skipped }
                log: harness/evidence/T-001/verify-1.log
            signoffs: []
            """);
    HarnessRepo repo = harness.open();

    assertThat(messages(Rules.hc04(repo)))
        .anyMatch(message -> message.contains("lacks result key coverage"))
        .anyMatch(message -> message.contains("gate result tests is 'fail'"))
        .anyMatch(message -> message.contains("gate result perf is 'skipped'"));
    assertThat(messages(Rules.hc05(repo))).anyMatch(message -> message.contains("no sign-off"));
    assertThat(messages(Rules.hc06(repo)))
        .anyMatch(message -> message.contains("prompt file does not exist"))
        .anyMatch(message -> message.contains("raw_output patch does not exist"))
        .anyMatch(message -> message.contains("listed file does not exist: src/main/java/App.java"))
        .anyMatch(message -> message.contains("has status 'kept'"))
        .anyMatch(message -> message.contains("gate log file does not exist"))
        .noneMatch(message -> message.contains("AppTest.java"));
    assertThat(messages(Rules.hc08(repo))).anyMatch(message -> message.contains("v1.md"));
    assertThat(messages(Rules.hc11(repo)))
        .anyMatch(message -> message.contains("rejected_notes is empty"));
  }

  @Test
  void hc04_acceptsSkippedResultsOnlyWhenTheyCarryReasonText() {
    assertThat(LedgerChecks.isPassOrSkippedWithReason("pass")).isTrue();
    assertThat(LedgerChecks.isPassOrSkippedWithReason("skipped: opt-in profile")).isTrue();
    assertThat(LedgerChecks.isPassOrSkippedWithReason("skipped:")).isFalse();
    assertThat(LedgerChecks.isPassOrSkippedWithReason("skipped")).isFalse();
    assertThat(LedgerChecks.isPassOrSkippedWithReason("fail")).isFalse();
  }

  @Test
  void hc06_and_hc08_exemptDocumentOnlyTasksFromRawOutputAndPromptChecks() throws IOException {
    harness
        .write(
            "harness/tasks/T-001.yaml",
            SyntheticHarness.VALID_TASK.replace("[implement]", "[" + Vocabulary.DOCUMENT + "]"))
        .write("harness/ledger.yaml", "entries:\n  - task: T-001\n    files: []\n");
    HarnessRepo repo = harness.open();

    assertThat(messages(Rules.hc06(repo))).noneMatch(message -> message.contains("raw_output"));
    assertThat(Rules.hc08(repo)).isEmpty();
  }

  @Test
  void reportComputesKeptRatioAndCountsFromTheData() throws IOException {
    harness.writeComplete();

    String report = TraceabilityReport.render(harness.open(), harness.noHistory());

    assertThat(report)
        .contains("| T-001 | implement | high | done | 1 | 1 | 0 | 1 | 0.67 | pass | yes | n/a |")
        .contains("| Files rejected | 1 | engineer-asserted |")
        .contains("| Mean kept ratio | 0.67 | computed |")
        .endsWith(TraceabilityReport.CLOSING_LINE + "\n");
  }

  private static String withDependsOn(String id, String dependsOn) {
    return SyntheticHarness.VALID_TASK
        .replace("id: T-001", "id: " + id)
        .replace("depends_on: []", "depends_on: " + dependsOn);
  }

  private static List<Violation> allViolations(HarnessRepo repo, GitLog gitLog) {
    List<Violation> all = new ArrayList<>();
    all.addAll(Rules.hc01(repo));
    all.addAll(Rules.hc02(repo));
    all.addAll(Rules.hc03(repo));
    all.addAll(Rules.hc04(repo));
    all.addAll(Rules.hc05(repo));
    all.addAll(Rules.hc06(repo));
    all.addAll(Rules.hc07(repo, gitLog));
    all.addAll(Rules.hc08(repo));
    all.addAll(Rules.hc09(repo));
    all.addAll(Rules.hc10(repo));
    all.addAll(Rules.hc11(repo));
    all.addAll(Rules.hc12(repo, gitLog));
    return all;
  }
}
