package dev.harshil.shortener.harness;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Field and dependency checks over task specs, behind rules HC-01 and HC-02. */
final class TaskSpecChecks {

  private TaskSpecChecks() {}

  /** HC-01 checks for one task: required fields, enum values, and id matching the file name. */
  static List<Violation> requiredFields(TaskSpec task) {
    List<Violation> out = new ArrayList<>();
    String subject = task.key();
    requireText(out, subject, "id", task.id());
    if (task.id() != null && !task.id().equals(task.fileStem())) {
      out.add(hc01(subject, "id '" + task.id() + "' does not match file name " + task.fileStem()));
    }
    requireText(out, subject, "title", task.title());
    requireText(out, subject, "requirement", task.requirement());
    requireEnumList(out, subject, "activities", task.activities(), Vocabulary.ACTIVITIES);
    requireEnum(out, subject, "impact", task.impact(), Vocabulary.IMPACTS);
    checkImpactReasons(out, task);
    requireEnum(out, subject, "status", task.status(), Vocabulary.STATUSES);
    requireText(out, subject, "intent", task.intent());
    requireNonEmpty(out, subject, "constraints", task.constraints());
    requireNonEmpty(out, subject, "acceptance_criteria", task.acceptanceCriteria());
    if (!task.hasTechnicalContext()) {
      out.add(hc01(subject, "missing required field technical_context"));
    } else {
      requireNonEmpty(out, subject, "technical_context.files", task.technicalContextFiles());
    }
    requireNonEmpty(out, subject, "verification", task.verification());
    return out;
  }

  /** HC-02 checks over all tasks: dependencies exist and form no cycle. */
  static List<Violation> dependencies(List<TaskSpec> tasks) {
    Map<String, List<String>> edges = new TreeMap<>();
    for (TaskSpec task : tasks) {
      edges.put(task.key(), task.dependsOn());
    }
    List<Violation> out = new ArrayList<>();
    for (TaskSpec task : tasks) {
      for (String dependency : task.dependsOn()) {
        if (!edges.containsKey(dependency)) {
          out.add(hc02(task.key(), "depends_on references missing task " + dependency));
        }
      }
    }
    Set<String> finished = new HashSet<>();
    for (String start : edges.keySet()) {
      visit(start, edges, new ArrayDeque<>(), finished, out);
    }
    return out;
  }

  private static void visit(
      String node,
      Map<String, List<String>> edges,
      Deque<String> path,
      Set<String> finished,
      List<Violation> out) {
    if (finished.contains(node)) {
      return;
    }
    if (path.contains(node)) {
      out.add(hc02(node, "depends_on forms a cycle: " + describeCycle(path, node)));
      return;
    }
    path.addLast(node);
    for (String dependency : edges.getOrDefault(node, List.of())) {
      visit(dependency, edges, path, finished, out);
    }
    path.removeLast();
    finished.add(node);
  }

  private static String describeCycle(Deque<String> path, String repeated) {
    List<String> cycle = new ArrayList<>();
    boolean started = false;
    for (String node : path) {
      started = started || node.equals(repeated);
      if (started) {
        cycle.add(node);
      }
    }
    cycle.add(repeated);
    return String.join(" -> ", cycle);
  }

  private static void checkImpactReasons(List<Violation> out, TaskSpec task) {
    if (task.isHighImpact() && task.impactReasons().isEmpty()) {
      out.add(hc01(task.key(), "impact_reasons is required when impact is high"));
    }
    for (String reason : task.impactReasons()) {
      if (!Vocabulary.IMPACT_REASONS.contains(reason)) {
        out.add(hc01(task.key(), "impact_reasons has unknown value '" + reason + "'"));
      }
    }
  }

  private static void requireText(List<Violation> out, String subject, String field, String value) {
    if (value == null || value.isBlank()) {
      out.add(hc01(subject, "missing required field " + field));
    }
  }

  private static void requireNonEmpty(
      List<Violation> out, String subject, String field, List<String> values) {
    if (values.isEmpty()) {
      out.add(hc01(subject, field + " must be a non-empty list"));
    }
  }

  private static void requireEnum(
      List<Violation> out, String subject, String field, String value, Set<String> allowed) {
    if (value == null || value.isBlank()) {
      out.add(hc01(subject, "missing required field " + field));
    } else if (!allowed.contains(value)) {
      out.add(hc01(subject, unknownValue(field, value, allowed)));
    }
  }

  private static void requireEnumList(
      List<Violation> out, String subject, String field, List<String> values, Set<String> allowed) {
    requireNonEmpty(out, subject, field, values);
    for (String value : values) {
      if (!allowed.contains(value)) {
        out.add(hc01(subject, unknownValue(field, value, allowed)));
      }
    }
  }

  private static String unknownValue(String field, String value, Set<String> allowed) {
    return field + " has unknown value '" + value + "', allowed: " + Vocabulary.describe(allowed);
  }

  private static Violation hc01(String subject, String message) {
    return new Violation("HC-01", subject, message);
  }

  private static Violation hc02(String subject, String message) {
    return new Violation("HC-02", subject, message);
  }
}
