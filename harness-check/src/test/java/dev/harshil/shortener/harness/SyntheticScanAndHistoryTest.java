package dev.harshil.shortener.harness;

import static dev.harshil.shortener.harness.SyntheticHarness.messages;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves the scanning and history rules fire on synthetic data: HC-07, HC-09, HC-10, HC-12. */
class SyntheticScanAndHistoryTest {

  private static final String FAKE_AWS_KEY = "AKIA" + "ABCDEFGHIJKLMNOP";
  private static final String ZERO_WIDTH_SPACE = Character.toString(0x200B);
  private static final String NUL = Character.toString(0);
  private static final String SHA_PADDING = "0".repeat(36);

  @TempDir Path root;

  private SyntheticHarness harness;

  @BeforeEach
  void setUp() {
    harness = new SyntheticHarness(root);
  }

  @Test
  void hc07_flagsCommitsWithoutTrailerOrWithUnknownTaskAndIgnoresBots() throws IOException {
    harness.write("harness/tasks/T-001.yaml", SyntheticHarness.VALID_TASK);
    GitLog gitLog =
        GitLog.of(
            root,
            List.of(
                commit("1111", "Harshil", "feat: create endpoint", "Task: T-001\n"),
                commit("2222", "Harshil", "fix: forgot the trailer", ""),
                commit("3333", "dependabot[bot]", "chore: bump", ""),
                commit("4444", "Harshil", "docs: unknown task", "Task: T-999\n")));

    List<Violation> found = Rules.hc07(harness.open(), gitLog);

    assertThat(found).hasSize(2);
    assertThat(found.get(0).subject()).startsWith("2222");
    assertThat(found.get(0).message()).contains("no 'Task: T-NNN' trailer");
    assertThat(found.get(1).subject()).startsWith("4444");
    assertThat(found.get(1).message()).contains("missing task T-999");
  }

  @Test
  void hc07_isSilentWhenHistoryIsSkipped() {
    assertThat(Rules.hc07(harness.open(), harness.noHistory())).isEmpty();
  }

  @Test
  void hc09_detectsZeroWidthCharactersInAiConfigurationFiles() throws IOException {
    harness
        .write(".claude/settings.json", "{ \"permissions\": \"deny" + ZERO_WIDTH_SPACE + "\" }\n")
        .write("AGENTS.md", "# Agents\n\nPlain text only.\n")
        .write("notes/other.md", "Not scanned: " + ZERO_WIDTH_SPACE + "\n");

    List<Violation> found = Rules.hc09(harness.open());

    assertThat(found).hasSize(1);
    assertThat(found.get(0).subject()).isEqualTo(".claude/settings.json");
    assertThat(found.get(0).message()).contains("U+200B");
  }

  @Test
  void hc10_detectsSecretPatternsAndHonoursTheAllowlist() throws IOException {
    harness
        .write("notes.txt", "key " + FAKE_AWS_KEY + "\n" + "api_key = dev-only-key-do-not-use\n")
        .write("config.txt", "password = \"" + "A".repeat(30) + "\"\n")
        .write("key.txt", "-----BEGIN " + "RSA PRIVATE KEY-----\n");

    List<String> found = messages(Rules.hc10(harness.open()));

    assertThat(found).hasSize(3);
    assertThat(found)
        .anyMatch(message -> message.contains("aws-access-key-id"))
        .anyMatch(message -> message.contains("credential-assignment"))
        .anyMatch(message -> message.contains("private-key-block"));
  }

  @Test
  void hc10_skipsGitTargetDataDirectoriesAndBinaryFiles() throws IOException {
    harness
        .write(".git/config", FAKE_AWS_KEY + "\n")
        .write("target/classes/x.txt", FAKE_AWS_KEY + "\n")
        .write("data/urlshortener.mv.db", FAKE_AWS_KEY + "\n")
        .write("image.png", FAKE_AWS_KEY + "\n")
        .write("blob.dat", NUL + FAKE_AWS_KEY + "\n");

    assertThat(Rules.hc10(harness.open())).isEmpty();
  }

  @Test
  void hc12_flagsMissingImpactAnalysisFileAndMissingPredictedSection() throws IOException {
    String nestedKey =
        "  files: [src/main/java/App.java]\n  impact_analysis: docs/impact/T-001.md\n";
    harness
        .write(
            "harness/tasks/T-001.yaml",
            SyntheticHarness.VALID_TASK.replace("  files: [src/main/java/App.java]\n", nestedKey))
        .write(
            "harness/tasks/T-002.yaml",
            SyntheticHarness.VALID_TASK
                .replace("id: T-001", "id: T-002")
                .concat("impact_analysis:\n  document: docs/impact/T-002.md\n")
                .concat("  predicted_files: []\n"))
        .write("docs/impact/T-002.md", "# Impact\n\n## Blast radius\n\n- nothing\n");
    GitLog gitLog = GitLog.of(root, List.of());

    List<Violation> found = Rules.hc12(harness.open(), gitLog);

    assertThat(messages(found))
        .anyMatch(message -> message.contains("impact_analysis file does not exist"))
        .anyMatch(message -> message.contains("no 'Predicted files' section"));
    assertThat(found).extracting(Violation::subject).containsExactly("T-001", "T-002");
  }

  @Test
  void hc12_isSilentWhenHistoryIsSkipped() throws IOException {
    harness.write(
        "harness/tasks/T-001.yaml",
        SyntheticHarness.VALID_TASK + "impact_analysis: docs/impact/missing.md\n");

    assertThat(Rules.hc12(harness.open(), harness.noHistory())).isEmpty();
  }

  private static GitLog.Commit commit(String sha, String author, String subject, String body) {
    return new GitLog.Commit(sha + SHA_PADDING, author, subject, body);
  }
}
