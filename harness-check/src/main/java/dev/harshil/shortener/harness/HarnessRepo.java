package dev.harshil.shortener.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Locates and loads the harness data under a repository root: task specs, the ledger, prompts,
 * evidence, and reviews. Missing files load as empty data; unreadable files become violations
 * attributed to the rule that owns the file.
 */
public final class HarnessRepo {

  private static final String TASK_EXTENSION = ".yaml";
  private static final String ROOT_PROPERTY = "harness.root";
  private static final String MODULE_DIRECTORY = "harness-check";

  private final Path root;
  private final List<TaskSpec> tasks;
  private final Ledger ledger;
  private final List<Violation> loadProblems;

  private HarnessRepo(Path root, List<TaskSpec> tasks, Ledger ledger, List<Violation> problems) {
    this.root = root;
    this.tasks = tasks;
    this.ledger = ledger;
    this.loadProblems = problems;
  }

  /** Opens the harness rooted at the given repository directory. */
  public static HarnessRepo open(Path root) {
    Path normalized = root.toAbsolutePath().normalize();
    List<Violation> problems = new ArrayList<>();
    List<TaskSpec> tasks = loadTasks(normalized.resolve("harness").resolve("tasks"), problems);
    Ledger ledger = loadLedger(normalized.resolve("harness").resolve("ledger.yaml"), problems);
    return new HarnessRepo(normalized, tasks, ledger, List.copyOf(problems));
  }

  /**
   * Resolves the repository root: the {@code harness.root} system property when set, otherwise the
   * parent of the working directory when that directory is the module itself, otherwise the working
   * directory.
   */
  public static Path defaultRoot() {
    String configured = System.getProperty(ROOT_PROPERTY);
    if (configured != null && !configured.isBlank()) {
      return Path.of(configured).toAbsolutePath().normalize();
    }
    Path cwd = Path.of("").toAbsolutePath().normalize();
    Path name = cwd.getFileName();
    if (name != null && MODULE_DIRECTORY.equals(name.toString()) && cwd.getParent() != null) {
      return cwd.getParent();
    }
    return cwd;
  }

  /** The repository root. */
  public Path root() {
    return root;
  }

  /** The {@code harness/prompts} directory, which may not exist. */
  public Path promptsDir() {
    return root.resolve("harness").resolve("prompts");
  }

  /** The {@code harness/evidence} directory, which may not exist. */
  public Path evidenceDir() {
    return root.resolve("harness").resolve("evidence");
  }

  /** The {@code harness/reviews} directory, which may not exist. */
  public Path reviewsDir() {
    return root.resolve("harness").resolve("reviews");
  }

  /** All task specs, sorted by task id. */
  public List<TaskSpec> tasks() {
    return tasks;
  }

  /** The task with the given id, when one exists. */
  public Optional<TaskSpec> task(String id) {
    return tasks.stream().filter(task -> task.key().equals(id)).findFirst();
  }

  /** The parsed ledger, empty when the file does not exist. */
  public Ledger ledger() {
    return ledger;
  }

  /** Problems met while loading, attributed to the given rule. */
  public List<Violation> loadProblems(String rule) {
    return loadProblems.stream().filter(problem -> problem.rule().equals(rule)).toList();
  }

  /** Resolves a repository-relative path written with either slash style. */
  public Path resolve(String relative) {
    String cleaned = relative.trim().replace('\\', '/');
    while (cleaned.startsWith("./")) {
      cleaned = cleaned.substring(2);
    }
    return root.resolve(cleaned).normalize();
  }

  /** The repository-relative, forward-slash form of a path under the root. */
  public String relativize(Path path) {
    Path absolute = path.toAbsolutePath().normalize();
    if (!absolute.startsWith(root)) {
      return absolute.toString().replace('\\', '/');
    }
    return root.relativize(absolute).toString().replace('\\', '/');
  }

  /** True when the repository-relative path names an existing regular file. */
  public boolean fileExists(String relative) {
    return relative != null && !relative.isBlank() && Files.isRegularFile(resolve(relative));
  }

  /** Reads a text file as UTF-8 with malformed bytes replaced; empty when it cannot be read. */
  public Optional<String> readText(Path file) {
    if (!Files.isRegularFile(file)) {
      return Optional.empty();
    }
    try {
      return Optional.of(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static List<TaskSpec> loadTasks(Path dir, List<Violation> problems) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    List<TaskSpec> specs = new ArrayList<>();
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> yamlFiles =
          files
              .filter(file -> file.getFileName().toString().endsWith(TASK_EXTENSION))
              .sorted()
              .toList();
      for (Path file : yamlFiles) {
        loadYaml(file, "HC-01", problems).ifPresent(map -> specs.add(TaskSpec.fromYaml(file, map)));
      }
    } catch (IOException e) {
      problems.add(new Violation("HC-01", dir.toString(), "cannot list tasks: " + e.getMessage()));
    }
    specs.sort(Comparator.comparing(TaskSpec::key));
    return List.copyOf(specs);
  }

  private static Ledger loadLedger(Path file, List<Violation> problems) {
    if (!Files.isRegularFile(file)) {
      return Ledger.empty();
    }
    return loadYaml(file, "HC-06", problems).map(Ledger::fromYaml).orElse(Ledger.empty());
  }

  private static Optional<Map<String, Object>> loadYaml(
      Path file, String rule, List<Violation> problems) {
    String subject = file.getFileName().toString();
    try {
      String text = Files.readString(file, StandardCharsets.UTF_8);
      if (!text.isEmpty() && text.charAt(0) == 0xFEFF) {
        text = text.substring(1);
      }
      Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
      if (loaded instanceof Map<?, ?>) {
        return Optional.of(YamlValues.asMap(loaded));
      }
      problems.add(new Violation(rule, subject, "file is empty or not a YAML mapping"));
    } catch (IOException e) {
      problems.add(new Violation(rule, subject, "cannot read file: " + e.getMessage()));
    } catch (RuntimeException e) {
      problems.add(new Violation(rule, subject, "cannot parse YAML: " + e.getMessage()));
    }
    return Optional.empty();
  }
}
