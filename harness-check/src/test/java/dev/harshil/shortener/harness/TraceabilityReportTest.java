package dev.harshil.shortener.harness;

import static dev.harshil.shortener.harness.HarnessTestSupport.repo;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The traceability report is written to {@code target/} and renders deterministically. */
class TraceabilityReportTest {

  @Test
  void writesTraceabilityReportToTargetDirectory() throws IOException {
    HarnessRepo repo = repo();
    GitLog gitLog = GitLog.load(repo.root());

    Path report = TraceabilityReport.write(repo, gitLog, Path.of("target", "traceability.md"));

    assertThat(report).exists();
    String content = Files.readString(report, StandardCharsets.UTF_8);
    assertThat(content).startsWith("# Traceability report");
    assertThat(content).contains("## Tasks").contains("## Summary");
    assertThat(content).endsWith(TraceabilityReport.CLOSING_LINE + "\n");
  }

  @Test
  void rendersIdenticalMarkdownOnRepeatedRuns() {
    HarnessRepo repo = repo();
    GitLog gitLog = GitLog.load(repo.root());

    String first = TraceabilityReport.render(repo, gitLog);
    String second = TraceabilityReport.render(repo, gitLog);

    assertThat(second).isEqualTo(first);
  }

  @Test
  void listsEveryTaskOnceInIdOrder() {
    HarnessRepo repo = repo();
    String content = TraceabilityReport.render(repo, GitLog.unavailable(repo.root(), "test"));

    int previous = -1;
    for (TaskSpec task : repo.tasks()) {
      int position = content.indexOf("| " + task.key() + " |");
      assertThat(position).as("row for %s", task.key()).isGreaterThan(previous);
      previous = position;
    }
  }
}
