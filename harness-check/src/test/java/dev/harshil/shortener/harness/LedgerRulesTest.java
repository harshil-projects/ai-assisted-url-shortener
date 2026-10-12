package dev.harshil.shortener.harness;

import static dev.harshil.shortener.harness.HarnessTestSupport.assertNoViolations;
import static dev.harshil.shortener.harness.HarnessTestSupport.repo;

import org.junit.jupiter.api.Test;

/** Rules over {@code harness/ledger.yaml} and the files it references. */
class LedgerRulesTest {

  @Test
  void hc03_doneTasksHaveLedgerEntries() {
    assertNoViolations("HC-03", Rules.hc03(repo()));
  }

  @Test
  void hc04_doneTasksHavePassingGateRecordsWithCompleteResults() {
    assertNoViolations("HC-04", Rules.hc04(repo()));
  }

  @Test
  void hc05_highImpactDoneTasksHaveSignOffWithExistingReview() {
    assertNoViolations("HC-05", Rules.hc05(repo()));
  }

  @Test
  void hc06_ledgerReferencesResolveToExistingTasksAndFiles() {
    assertNoViolations("HC-06", Rules.hc06(repo()));
  }

  @Test
  void hc08_doneNonDocumentTasksHavePromptVersionOne() {
    assertNoViolations("HC-08", Rules.hc08(repo()));
  }

  @Test
  void hc11_rejectedFilesHaveRejectedNotes() {
    assertNoViolations("HC-11", Rules.hc11(repo()));
  }
}
