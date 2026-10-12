package dev.harshil.shortener.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes small synthetic harnesses into a temporary root so rule detection can be proven. */
final class SyntheticHarness {

  static final String TASK_ID = "T-001";

  static final String VALID_TASK =
      """
      id: T-001
      title: Create link endpoint
      requirement: R-01
      activities: [implement]
      impact: high
      impact_reasons: [security]
      status: done
      depends_on: []
      intent: Expose the create endpoint.
      constraints:
        - No new dependencies
      acceptance_criteria:
        - WHEN a valid URL is posted THE SYSTEM SHALL respond 201
      technical_context:
        files: [src/main/java/App.java]
      verification: [AppTest]
      """;

  static final String COMPLETE_LEDGER =
      """
      entries:
        - task: T-001
          tool: Claude Code
          model: test-model
          prompts: [harness/prompts/T-001/v1.md]
          raw_output: harness/evidence/T-001/ai-raw.patch
          files:
            - { path: src/main/java/App.java, status: generated }
            - { path: src/test/java/AppTest.java, status: rejected }
          rationale: Engineer-asserted.
          rejected_notes:
            - Test asserted on message text
      gates:
        - task: T-001
          commit: abc123
          overall: pass
          results:
            format: pass
            style: pass
            sast: pass
            tests: pass
            coverage: pass
            traceability: pass
            secrets: pass
            perf: "skipped: opt-in profile not run locally"
          log: harness/evidence/T-001/verify-1.log
      signoffs:
        - task: T-001
          reasons: [security]
          review: harness/reviews/T-001.md
          signed_by: Harshil
      """;

  static final String RAW_PATCH =
      """
      diff --git a/src/main/java/App.java b/src/main/java/App.java
      --- /dev/null
      +++ b/src/main/java/App.java
      @@ -0,0 +1,3 @@
      +alpha
      +beta
      +gamma
      """;

  private final Path root;

  SyntheticHarness(Path root) {
    this.root = root;
  }

  /** Writes a UTF-8 text file under the root, creating parent directories. */
  SyntheticHarness write(String relative, String content) throws IOException {
    Path file = root.resolve(relative);
    Files.createDirectories(file.getParent());
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return this;
  }

  /** Writes one done, high-impact task with every artifact the rules require. */
  SyntheticHarness writeComplete() throws IOException {
    return write("harness/tasks/T-001.yaml", VALID_TASK)
        .write("harness/ledger.yaml", COMPLETE_LEDGER)
        .write("harness/prompts/T-001/v1.md", "# Prompt v1\n")
        .write("harness/evidence/T-001/ai-raw.patch", RAW_PATCH)
        .write("harness/evidence/T-001/verify-1.log", "BUILD SUCCESS\n")
        .write("harness/reviews/T-001.md", "# Review\n")
        .write("src/main/java/App.java", "alpha\nbeta\n");
  }

  HarnessRepo open() {
    return HarnessRepo.open(root);
  }

  GitLog noHistory() {
    return GitLog.unavailable(root, "synthetic harness has no git history");
  }

  /** The messages of the violations, for readable assertions. */
  static List<String> messages(List<Violation> violations) {
    return violations.stream().map(Violation::message).toList();
  }
}
