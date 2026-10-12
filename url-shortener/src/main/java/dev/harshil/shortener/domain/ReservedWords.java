package dev.harshil.shortener.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Aliases a client may not claim because they collide with, or look like, paths the service itself
 * serves: the management API, actuator, API docs, browser conventions, and the names reserved for
 * future surfaces (design 3.6). Matching is case-insensitive even though codes are case-sensitive,
 * because {@code /API} or {@code /Admin} would still mislead a reader of the short URL.
 */
public final class ReservedWords {

  private static final Set<String> WORDS =
      Set.of(
          "api",
          "actuator",
          "health",
          "metrics",
          "prometheus",
          "swagger-ui",
          "v3",
          "api-docs",
          "error",
          "favicon.ico",
          "robots.txt",
          ".well-known",
          "static",
          "assets",
          "stats",
          "admin",
          "login",
          "h2-console");

  private ReservedWords() {}

  /**
   * Tells whether an alias is reserved.
   *
   * @param alias the candidate alias; {@code null} is not reserved because there is nothing to
   *     collide with, and the DTO decides whether an alias is required
   * @return true when the alias, compared case-insensitively, is on the reserved list
   */
  public static boolean isReserved(String alias) {
    return alias != null && WORDS.contains(alias.toLowerCase(Locale.ROOT));
  }
}
