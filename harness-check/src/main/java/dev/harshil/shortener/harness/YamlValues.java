package dev.harshil.shortener.harness;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tolerant read access to the untyped maps and lists produced by SnakeYAML. Missing keys and wrong
 * types yield nulls or empty collections so that rules, not the loader, report them.
 */
final class YamlValues {

  private YamlValues() {}

  /** Returns the value as a string-keyed map when it is a map, otherwise an empty map. */
  static Map<String, Object> asMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return Map.of();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, item) -> result.put(String.valueOf(key), item));
    return result;
  }

  /** Returns the value as a list when it is a collection, otherwise an empty list. */
  static List<Object> asList(Object value) {
    if (value instanceof Collection<?> items) {
      return new ArrayList<>(items);
    }
    return List.of();
  }

  /** Returns the scalar under the key as a string, or null when missing or not a scalar. */
  static String scalar(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null || value instanceof Collection<?> || value instanceof Map<?, ?>) {
      return null;
    }
    return value.toString();
  }

  /** Returns the scalars under the key; a lone scalar becomes a singleton list. */
  static List<String> scalars(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value == null || value instanceof Map<?, ?>) {
      return List.of();
    }
    if (value instanceof Collection<?> items) {
      List<String> result = new ArrayList<>();
      for (Object item : items) {
        if (item != null && !(item instanceof Collection<?>) && !(item instanceof Map<?, ?>)) {
          result.add(item.toString());
        }
      }
      return List.copyOf(result);
    }
    return List.of(value.toString());
  }

  /** Returns the maps in the list under the key, skipping items that are not maps. */
  static List<Map<String, Object>> maps(Map<String, Object> map, String key) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : asList(map.get(key))) {
      if (item instanceof Map<?, ?>) {
        result.add(asMap(item));
      }
    }
    return List.copyOf(result);
  }
}
