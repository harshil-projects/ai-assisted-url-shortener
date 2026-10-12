package dev.harshil.shortener.harness;

import java.util.List;
import java.util.Map;

/**
 * One human sign-off recorded in the ledger for a high-impact task.
 *
 * @param task the task id, or null when missing
 * @param reasons the impact reason codes the sign-off covers
 * @param review the repository-relative review artifact path, or null when missing
 * @param signedBy the signer, or null when missing
 */
public record SignOff(String task, List<String> reasons, String review, String signedBy) {

  /** Builds a sign-off from one item of the ledger's {@code signoffs} list. */
  static SignOff fromYaml(Map<String, Object> yaml) {
    return new SignOff(
        YamlValues.scalar(yaml, "task"),
        YamlValues.scalars(yaml, "reasons"),
        YamlValues.scalar(yaml, "review"),
        YamlValues.scalar(yaml, "signed_by"));
  }
}
