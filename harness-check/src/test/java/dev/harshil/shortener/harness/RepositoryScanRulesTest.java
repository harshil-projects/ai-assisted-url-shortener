package dev.harshil.shortener.harness;

import static dev.harshil.shortener.harness.HarnessTestSupport.assertNoViolations;
import static dev.harshil.shortener.harness.HarnessTestSupport.repo;

import org.junit.jupiter.api.Test;

/** Rules that scan the working tree: HC-09 (hidden Unicode) and HC-10 (secret patterns). */
class RepositoryScanRulesTest {

  @Test
  void hc09_aiConfigurationFilesContainNoHiddenUnicode() {
    assertNoViolations("HC-09", Rules.hc09(repo()));
  }

  @Test
  void hc10_noTrackedTextFileMatchesSecretPatterns() {
    assertNoViolations("HC-10", Rules.hc10(repo()));
  }
}
