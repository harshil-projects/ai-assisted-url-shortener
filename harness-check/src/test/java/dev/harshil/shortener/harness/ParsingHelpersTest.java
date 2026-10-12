package dev.harshil.shortener.harness;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for the parsers behind the rules: git log records, impact analyses, patches, YAML. */
class ParsingHelpersTest {

  private static final String FIELD = "\u001f";
  private static final String RECORD = "\u001e";

  @Test
  void gitLogParseSplitsRecordsAndFieldsAndKeepsMultiLineBodies() {
    String output =
        "aaa"
            + FIELD
            + "Harshil"
            + FIELD
            + "feat: one"
            + FIELD
            + "Body line\n\nTask: T-001\n"
            + RECORD
            + "\n"
            + "bbb"
            + FIELD
            + "dependabot[bot]"
            + FIELD
            + "chore: bump"
            + FIELD
            + ""
            + RECORD
            + "\n";

    List<GitLog.Commit> commits = GitLog.parse(output);

    assertThat(commits).hasSize(2);
    assertThat(commits.get(0).sha()).isEqualTo("aaa");
    assertThat(commits.get(0).subject()).isEqualTo("feat: one");
    assertThat(commits.get(0).taskReferences()).containsExactly("T-001");
    assertThat(commits.get(0).isBot()).isFalse();
    assertThat(commits.get(1).taskReferences()).isEmpty();
    assertThat(commits.get(1).isBot()).isTrue();
  }

  @Test
  void taskReferencesAcceptSeveralTasksOnOneTrailerLine() {
    GitLog.Commit commit = new GitLog.Commit("c", "Harshil", "s", "Task: T-001, T-002 T-003\n");

    assertThat(commit.taskReferences()).containsExactly("T-001", "T-002", "T-003");
  }

  @Test
  void predictedFilesReadsOnlyTheBulletsUnderThePredictedFilesHeading() {
    String markdown =
        """
        # Impact analysis

        ## Predicted files

        - `url-shortener/src/main/java/dev/harshil/shortener/domain/Link.java` adds expiresAt
        - url-shortener/src/main/resources/db/migration/V2__add_link_expiry.sql
        * ./docs/reference/api.md

        ## Blast radius

        - src/main/java/NotPredicted.java
        """;

    Optional<List<String>> predicted = ImpactAnalysis.predictedFiles(markdown);

    assertThat(predicted).isPresent();
    assertThat(predicted.get())
        .containsExactly(
            "url-shortener/src/main/java/dev/harshil/shortener/domain/Link.java",
            "url-shortener/src/main/resources/db/migration/V2__add_link_expiry.sql",
            "docs/reference/api.md");
  }

  @Test
  void predictedFilesIsEmptyWithoutTheHeading() {
    assertThat(ImpactAnalysis.predictedFiles("# Impact\n\n- src/main/java/X.java\n")).isEmpty();
  }

  @Test
  void isPredictedMatchesExactPathsAndSuffixes() {
    List<String> predicted = List.of("api/LinkController.java");

    assertThat(ImpactAnalysis.isPredicted("api/LinkController.java", predicted)).isTrue();
    assertThat(ImpactAnalysis.isPredicted("x/src/main/java/api/LinkController.java", predicted))
        .isTrue();
    assertThat(ImpactAnalysis.isPredicted("x/src/main/java/api/Other.java", predicted)).isFalse();
  }

  @Test
  void addedLinesSkipFileHeadersAndBlankLines() {
    Set<String> added = KeptRatio.addedLines(SyntheticHarness.RAW_PATCH + "+\n+   \n");

    assertThat(added).containsExactlyInAnyOrder("alpha", "beta", "gamma");
  }

  @Test
  void trimmedLinesIgnoreWhitespaceOnlyLinesAndIndentation() {
    assertThat(KeptRatio.trimmedLines("  alpha\r\n\r\n\tbeta  \n   \n"))
        .containsExactlyInAnyOrder("alpha", "beta");
  }

  @Test
  void yamlValuesTolerateMissingKeysAndWrongTypes() {
    Map<String, Object> yaml =
        Map.of("list", List.of("a", 2, Map.of("x", 1)), "scalar", 7, "map", Map.of("k", "v"));

    assertThat(YamlValues.scalar(yaml, "missing")).isNull();
    assertThat(YamlValues.scalar(yaml, "list")).isNull();
    assertThat(YamlValues.scalar(yaml, "scalar")).isEqualTo("7");
    assertThat(YamlValues.scalars(yaml, "list")).containsExactly("a", "2");
    assertThat(YamlValues.scalars(yaml, "scalar")).containsExactly("7");
    assertThat(YamlValues.scalars(yaml, "map")).isEmpty();
    assertThat(YamlValues.maps(yaml, "list")).hasSize(1);
    assertThat(YamlValues.asMap("not a map")).isEmpty();
  }
}
