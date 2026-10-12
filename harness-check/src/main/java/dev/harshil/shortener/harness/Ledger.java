package dev.harshil.shortener.harness;

import java.util.List;
import java.util.Map;

/**
 * The parsed {@code harness/ledger.yaml}: review entries, gate records, and sign-offs.
 *
 * @param entries the review entries in file order
 * @param gates the gate records in file order
 * @param signoffs the sign-offs in file order
 */
public record Ledger(List<LedgerEntry> entries, List<GateRecord> gates, List<SignOff> signoffs) {

  /** An empty ledger, used when the file does not exist yet. */
  static Ledger empty() {
    return new Ledger(List.of(), List.of(), List.of());
  }

  /** Builds a ledger from the raw YAML mapping of the ledger file. */
  static Ledger fromYaml(Map<String, Object> yaml) {
    return new Ledger(
        YamlValues.maps(yaml, "entries").stream().map(LedgerEntry::fromYaml).toList(),
        YamlValues.maps(yaml, "gates").stream().map(GateRecord::fromYaml).toList(),
        YamlValues.maps(yaml, "signoffs").stream().map(SignOff::fromYaml).toList());
  }

  /** The review entries recorded for the task, in file order. */
  public List<LedgerEntry> entriesFor(String taskId) {
    return entries.stream().filter(entry -> taskId.equals(entry.task())).toList();
  }

  /** The gate records recorded for the task, in file order. */
  public List<GateRecord> gatesFor(String taskId) {
    return gates.stream().filter(gate -> taskId.equals(gate.task())).toList();
  }

  /** The sign-offs recorded for the task, in file order. */
  public List<SignOff> signoffsFor(String taskId) {
    return signoffs.stream().filter(signoff -> taskId.equals(signoff.task())).toList();
  }
}
