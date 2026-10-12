package dev.harshil.shortener.harness;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

/** Content scans over the working tree, behind rules HC-09 (hidden Unicode) and HC-10 (secrets). */
final class FileScanner {

  /** Zero-width and bidirectional control code points that can hide instructions in text. */
  private static final Set<Integer> HIDDEN_CODE_POINTS =
      Set.of(0x200B, 0x200C, 0x200D, 0x2060, 0xFEFF, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E);

  private static final List<String> AI_CONFIG_FILES =
      List.of("AGENTS.md", "CLAUDE.md", ".github/copilot-instructions.md");

  private static final String AI_CONFIG_DIRECTORY = ".claude";

  private static final Set<String> SKIPPED_DIRECTORIES = Set.of(".git", "target", "data");

  private static final Set<String> BINARY_EXTENSIONS =
      Set.of(
          "jar", "class", "png", "jpg", "jpeg", "gif", "ico", "pdf", "zip", "gz", "tgz", "7z", "db",
          "exe", "dll", "so", "dylib", "woff", "woff2", "ttf", "eot", "bin", "jks", "p12");

  private static final long MAX_SCANNED_BYTES = 8L * 1024 * 1024;
  private static final int BINARY_PROBE_BYTES = 8192;

  private static final String ALLOWLISTED_LITERAL = "dev-only-key-do-not-use";

  private record SecretPattern(String name, Pattern pattern) {}

  private static final List<SecretPattern> SECRET_PATTERNS =
      List.of(
          new SecretPattern("aws-access-key-id", Pattern.compile("AKIA[0-9A-Z]{16}")),
          new SecretPattern(
              "private-key-block",
              Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |)PRIVATE KEY-----")),
          new SecretPattern(
              "credential-assignment",
              Pattern.compile(
                  "(?i)(api[_-]?key|secret|password)\\s*[:=]\\s*[\"']?[A-Za-z0-9/+]{24,}")));

  private FileScanner() {}

  /** HC-09: the AI configuration files contain no hidden Unicode code points. */
  static List<Violation> hiddenUnicode(HarnessRepo repo) {
    List<Path> targets = new ArrayList<>();
    for (String name : AI_CONFIG_FILES) {
      Path file = repo.resolve(name);
      if (Files.isRegularFile(file)) {
        targets.add(file);
      }
    }
    targets.addAll(filesUnder(repo.root().resolve(AI_CONFIG_DIRECTORY)));
    List<Violation> out = new ArrayList<>();
    for (Path file : targets) {
      repo.readText(file).ifPresent(text -> scanHidden(repo.relativize(file), text, out));
    }
    return out;
  }

  /** HC-10: no text file in the working tree matches a secret pattern. */
  static List<Violation> secrets(HarnessRepo repo) {
    List<Violation> out = new ArrayList<>();
    for (Path file : filesUnder(repo.root())) {
      if (isBinaryByExtension(file)) {
        continue;
      }
      readScannableText(file).ifPresent(text -> scanSecrets(repo.relativize(file), text, out));
    }
    return out;
  }

  private static void scanHidden(String subject, String text, List<Violation> out) {
    List<String> lines = text.lines().toList();
    for (int i = 0; i < lines.size(); i++) {
      OptionalInt hit =
          lines.get(i).codePoints().filter(point -> HIDDEN_CODE_POINTS.contains(point)).findFirst();
      if (hit.isPresent()) {
        String message =
            String.format(
                Locale.ROOT, "line %d contains hidden code point U+%04X", i + 1, hit.getAsInt());
        out.add(new Violation("HC-09", subject, message));
      }
    }
  }

  private static void scanSecrets(String subject, String text, List<Violation> out) {
    List<String> lines = text.lines().toList();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.contains(ALLOWLISTED_LITERAL)) {
        continue;
      }
      for (SecretPattern secret : SECRET_PATTERNS) {
        if (secret.pattern().matcher(line).find()) {
          String message = "line " + (i + 1) + " matches secret pattern " + secret.name();
          out.add(new Violation("HC-10", subject, message));
        }
      }
    }
  }

  /** Regular files under the directory, skipping build output, data, and git internals. */
  static List<Path> filesUnder(Path directory) {
    List<Path> files = new ArrayList<>();
    if (!Files.isDirectory(directory)) {
      return files;
    }
    try {
      Files.walkFileTree(directory, new CollectingVisitor(directory, files));
    } catch (IOException e) {
      // An unreadable subtree yields fewer files to scan; the files found so far are kept.
      return files;
    }
    files.sort(null);
    return files;
  }

  private static boolean isBinaryByExtension(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    int dot = name.lastIndexOf('.');
    return dot >= 0 && BINARY_EXTENSIONS.contains(name.substring(dot + 1));
  }

  /** Reads a file as text unless it is too large or has NUL bytes in its first block. */
  private static Optional<String> readScannableText(Path file) {
    try {
      if (Files.size(file) > MAX_SCANNED_BYTES) {
        return Optional.empty();
      }
      byte[] bytes = Files.readAllBytes(file);
      int probe = Math.min(bytes.length, BINARY_PROBE_BYTES);
      for (int i = 0; i < probe; i++) {
        if (bytes[i] == 0) {
          return Optional.empty();
        }
      }
      return Optional.of(new String(bytes, StandardCharsets.UTF_8));
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  /** Collects regular files while pruning the directories that must never be scanned. */
  private static final class CollectingVisitor extends SimpleFileVisitor<Path> {

    private final Path start;
    private final List<Path> files;

    private CollectingVisitor(Path start, List<Path> files) {
      this.start = start;
      this.files = files;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      Path name = dir.getFileName();
      boolean skip =
          !dir.equals(start) && name != null && SKIPPED_DIRECTORIES.contains(name.toString());
      return skip ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      if (attrs.isRegularFile()) {
        files.add(file);
      }
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      return FileVisitResult.CONTINUE;
    }
  }
}
