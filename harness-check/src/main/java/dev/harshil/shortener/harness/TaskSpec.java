package dev.harshil.shortener.harness;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * One task specification read from {@code harness/tasks/T-NNN.yaml}. Fields missing from the file
 * are null or empty here; rule HC-01 reports them instead of the loader failing.
 *
 * @param file the YAML file the spec was read from
 * @param fileStem the file name without its {@code .yaml} extension
 * @param id the declared task id, or null when missing
 * @param title the task title, or null when missing
 * @param requirement the requirement id, or null when missing
 * @param activities the declared activities, possibly empty
 * @param impact {@code high} or {@code normal}, or null when missing
 * @param impactReasons the impact reason codes, possibly empty
 * @param status the lifecycle status, or null when missing
 * @param dependsOn the ids of tasks this one depends on
 * @param intent the intent text, or null when missing
 * @param constraints the constraints list, possibly empty
 * @param acceptanceCriteria the acceptance criteria list, possibly empty
 * @param hasTechnicalContext whether a {@code technical_context} mapping is present
 * @param technicalContextFiles the files listed under {@code technical_context}
 * @param verification the verification list, possibly empty
 * @param impactAnalysis the optional impact analysis path, read from the nested key {@code
 *     technical_context.impact_analysis} or the top-level key, given as a bare path or as the
 *     {@code document} value of a mapping
 */
public record TaskSpec(
    Path file,
    String fileStem,
    String id,
    String title,
    String requirement,
    List<String> activities,
    String impact,
    List<String> impactReasons,
    String status,
    List<String> dependsOn,
    String intent,
    List<String> constraints,
    List<String> acceptanceCriteria,
    boolean hasTechnicalContext,
    List<String> technicalContextFiles,
    List<String> verification,
    String impactAnalysis) {

  /** Builds a spec from the raw YAML mapping of one task file. */
  static TaskSpec fromYaml(Path file, Map<String, Object> yaml) {
    String name = file.getFileName().toString();
    String stem = name.endsWith(".yaml") ? name.substring(0, name.length() - 5) : name;
    Object context = yaml.get("technical_context");
    Map<String, Object> contextMap = YamlValues.asMap(context);
    return new TaskSpec(
        file,
        stem,
        YamlValues.scalar(yaml, "id"),
        YamlValues.scalar(yaml, "title"),
        YamlValues.scalar(yaml, "requirement"),
        YamlValues.scalars(yaml, "activities"),
        YamlValues.scalar(yaml, "impact"),
        YamlValues.scalars(yaml, "impact_reasons"),
        YamlValues.scalar(yaml, "status"),
        YamlValues.scalars(yaml, "depends_on"),
        YamlValues.scalar(yaml, "intent"),
        YamlValues.scalars(yaml, "constraints"),
        YamlValues.scalars(yaml, "acceptance_criteria"),
        context instanceof Map<?, ?>,
        YamlValues.scalars(contextMap, "files"),
        YamlValues.scalars(yaml, "verification"),
        impactAnalysisPath(contextMap, yaml));
  }

  /**
   * The impact analysis path: {@code technical_context.impact_analysis} first, then the top-level
   * key. Either may be a bare path or a mapping whose {@code document} key holds the path.
   */
  private static String impactAnalysisPath(Map<String, Object> context, Map<String, Object> yaml) {
    for (Map<String, Object> holder : List.of(context, yaml)) {
      Object value = holder.get("impact_analysis");
      if (value instanceof Map<?, ?>) {
        value = YamlValues.asMap(value).get("document");
      }
      if (value != null && !(value instanceof Map<?, ?>) && !(value instanceof Iterable<?>)) {
        return value.toString();
      }
    }
    return null;
  }

  /** The id used to address this task: the declared id, or the file stem when the id is missing. */
  public String key() {
    return id != null && !id.isBlank() ? id : fileStem;
  }

  /** True when the status is {@code done}. */
  public boolean isDone() {
    return Vocabulary.DONE.equals(status);
  }

  /** True when the impact is {@code high}. */
  public boolean isHighImpact() {
    return Vocabulary.HIGH.equals(impact);
  }

  /** True when the activities are exactly the single activity {@code document}. */
  public boolean isDocumentOnly() {
    return activities.equals(List.of(Vocabulary.DOCUMENT));
  }
}
