package dev.harshil.shortener.harness;

import java.util.List;
import java.util.Set;

/** The closed vocabularies of the harness data, as fixed by the design document. */
final class Vocabulary {

  static final Set<String> ACTIVITIES =
      Set.of("implement", "debug", "refactor", "test-gen", "document", "review-prep");

  static final Set<String> IMPACTS = Set.of("high", "normal");

  static final Set<String> IMPACT_REASONS =
      Set.of("schema", "security", "dependency", "data-deletion");

  static final Set<String> STATUSES = Set.of("todo", "in-progress", "done", "dropped");

  static final Set<String> FILE_STATUSES = Set.of("generated", "edited", "rejected");

  /** Every key a passing gate record must report, in report order. */
  static final List<String> GATE_KEYS =
      List.of("format", "style", "sast", "tests", "coverage", "traceability", "secrets", "perf");

  static final String DOCUMENT = "document";

  static final String DONE = "done";

  static final String HIGH = "high";

  static final String PASS = "pass";

  static final String REJECTED = "rejected";

  private Vocabulary() {}

  /** Sorted, comma-separated rendering of an allowed set for messages. */
  static String describe(Set<String> allowed) {
    return String.join(", ", allowed.stream().sorted().toList());
  }
}
