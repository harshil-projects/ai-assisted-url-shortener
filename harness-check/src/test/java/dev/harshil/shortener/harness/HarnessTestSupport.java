package dev.harshil.shortener.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;

/** Shared helpers for the rule tests: the repository under test and the violation assertion. */
final class HarnessTestSupport {

  private HarnessTestSupport() {}

  /** The harness of the repository named by the {@code harness.root} system property. */
  static HarnessRepo repo() {
    return HarnessRepo.open(HarnessRepo.defaultRoot());
  }

  /** Fails with every violation listed when the list is not empty. */
  static void assertNoViolations(String rule, List<Violation> violations) {
    assertThat(violations).withFailMessage(() -> describe(rule, violations)).isEmpty();
  }

  private static String describe(String rule, List<Violation> violations) {
    String listed =
        violations.stream().map(Violation::toString).collect(Collectors.joining("\n  "));
    return rule + ": " + violations.size() + " violation(s)\n  " + listed;
  }
}
