package dev.harshil.shortener.harness;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One gate run appended to the ledger by the gate script.
 *
 * @param task the task id, or null when missing
 * @param commit the commit the gates ran against, or null when missing
 * @param overall {@code pass} or {@code fail}, or null when missing
 * @param results the per-gate results keyed by gate name; a missing value is the empty string
 * @param log the repository-relative gate log path, or null when missing
 */
public record GateRecord(
    String task, String commit, String overall, Map<String, String> results, String log) {

  /** Builds a gate record from one item of the ledger's {@code gates} list. */
  static GateRecord fromYaml(Map<String, Object> yaml) {
    Map<String, String> results = new LinkedHashMap<>();
    YamlValues.asMap(yaml.get("results"))
        .forEach((key, value) -> results.put(key, value == null ? "" : value.toString()));
    return new GateRecord(
        YamlValues.scalar(yaml, "task"),
        YamlValues.scalar(yaml, "commit"),
        YamlValues.scalar(yaml, "overall"),
        Map.copyOf(results),
        YamlValues.scalar(yaml, "log"));
  }

  /** True when the overall result is {@code pass}. */
  public boolean isPass() {
    return Vocabulary.PASS.equals(overall);
  }

  /** The subject to name in violations: the task id, or a placeholder when it is missing. */
  public String subject() {
    return task == null || task.isBlank() ? "<gate record without task>" : task;
  }
}
