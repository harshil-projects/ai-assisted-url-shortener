package dev.harshil.shortener.harness;

import static dev.harshil.shortener.harness.HarnessTestSupport.assertNoViolations;
import static dev.harshil.shortener.harness.HarnessTestSupport.repo;

import org.junit.jupiter.api.Test;

/** Rules over the task specs under {@code harness/tasks/}: HC-01 and HC-02. */
class TaskSpecRulesTest {

  @Test
  void hc01_taskSpecsHaveRequiredFieldsAndValidEnums() {
    assertNoViolations("HC-01", Rules.hc01(repo()));
  }

  @Test
  void hc02_dependenciesReferToExistingTasksAndFormNoCycle() {
    assertNoViolations("HC-02", Rules.hc02(repo()));
  }
}
