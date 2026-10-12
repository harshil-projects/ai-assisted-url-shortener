package dev.harshil.shortener.harness;

import java.util.Map;

/**
 * One file listed in a ledger entry with its engineer-asserted review status.
 *
 * @param path the repository-relative path, or null when missing
 * @param status {@code generated}, {@code edited}, or {@code rejected}; null when missing
 */
public record FileChange(String path, String status) {

  /** Builds a file change from a ledger item, which may be a mapping or a bare path scalar. */
  static FileChange fromYaml(Object item) {
    if (item instanceof Map<?, ?>) {
      Map<String, Object> map = YamlValues.asMap(item);
      return new FileChange(YamlValues.scalar(map, "path"), YamlValues.scalar(map, "status"));
    }
    return new FileChange(item == null ? null : item.toString(), null);
  }

  /** True when the status equals the given value. */
  public boolean hasStatus(String expected) {
    return expected.equals(status);
  }
}
