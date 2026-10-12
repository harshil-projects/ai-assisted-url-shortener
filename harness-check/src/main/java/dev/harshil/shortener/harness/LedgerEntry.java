package dev.harshil.shortener.harness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One review entry of the ledger: which task, which prompts, which raw output, and the per-file
 * decisions the engineer recorded.
 *
 * @param task the task id, or null when missing
 * @param tool the AI tool name, or null when missing
 * @param model the model id as reported by the tool, or null when missing
 * @param prompts repository-relative prompt file paths
 * @param rawOutput the repository-relative raw output patch, or null when missing
 * @param files the per-file review decisions
 * @param rationale the engineer-asserted rationale, or null when missing
 * @param rejectedNotes notes explaining rejected files
 * @param reviewer the reviewer name, or null when missing
 */
public record LedgerEntry(
    String task,
    String tool,
    String model,
    List<String> prompts,
    String rawOutput,
    List<FileChange> files,
    String rationale,
    List<String> rejectedNotes,
    String reviewer) {

  /** Builds an entry from one item of the ledger's {@code entries} list. */
  static LedgerEntry fromYaml(Map<String, Object> yaml) {
    List<FileChange> files = new ArrayList<>();
    for (Object item : YamlValues.asList(yaml.get("files"))) {
      files.add(FileChange.fromYaml(item));
    }
    return new LedgerEntry(
        YamlValues.scalar(yaml, "task"),
        YamlValues.scalar(yaml, "tool"),
        YamlValues.scalar(yaml, "model"),
        YamlValues.scalars(yaml, "prompts"),
        YamlValues.scalar(yaml, "raw_output"),
        List.copyOf(files),
        YamlValues.scalar(yaml, "rationale"),
        YamlValues.scalars(yaml, "rejected_notes"),
        YamlValues.scalar(yaml, "reviewer"));
  }

  /** Counts the listed files carrying the given status. */
  public long countFiles(String status) {
    return files.stream().filter(file -> file.hasStatus(status)).count();
  }

  /** The subject to name in violations: the task id, or a placeholder when it is missing. */
  public String subject() {
    return task == null || task.isBlank() ? "<ledger entry without task>" : task;
  }
}
