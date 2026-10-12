package dev.harshil.shortener.harness;

import static dev.harshil.shortener.harness.HarnessTestSupport.assertNoViolations;
import static dev.harshil.shortener.harness.HarnessTestSupport.repo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Rules that need git history after the {@code baseline} tag: HC-07 and HC-12. Both are skipped,
 * with the reason printed, when {@code .git} is absent, git is not on the PATH, or the tag does not
 * exist.
 */
class GitRulesTest {

  @Test
  void hc07_commitsAfterBaselineCarryTaskTrailersNamingExistingTasks() {
    HarnessRepo repo = repo();
    GitLog gitLog = GitLog.load(repo.root());
    assumeHistory("HC-07", gitLog);
    assertNoViolations("HC-07", Rules.hc07(repo, gitLog));
  }

  @Test
  void hc12_changedMainSourceFilesWerePredictedByImpactAnalysis() {
    HarnessRepo repo = repo();
    GitLog gitLog = GitLog.load(repo.root());
    assumeHistory("HC-12", gitLog);
    assertNoViolations("HC-12", Rules.hc12(repo, gitLog));
  }

  private static void assumeHistory(String rule, GitLog gitLog) {
    if (gitLog.skipped()) {
      System.out.println(rule + " skipped: " + gitLog.skipReason());
    }
    Assumptions.assumeFalse(gitLog.skipped(), () -> rule + " skipped: " + gitLog.skipReason());
  }
}
