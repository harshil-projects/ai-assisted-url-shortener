package dev.harshil.shortener.service;

import java.security.SecureRandom;
import java.util.Random;
import org.springframework.stereotype.Component;

/**
 * Produces the random short codes that identify links (ADR 0003).
 *
 * <p>Codes are 7 characters over the 62-character alphabet {@code [A-Za-z0-9]}, so the space holds
 * 62^7, about 3.5 trillion, values. Drawing codes uniformly at random from {@link SecureRandom}
 * makes them non-enumerable, which matters because the redirect endpoint is public. Collisions are
 * resolved by the database's unique constraint and a retry in {@code LinkService}, not here; the
 * mathematics says they are rare: inserting the first million codes produces about n^2 / 2N = 10^12
 * / (2 x 3.5 x 10^12), roughly 0.14, collisions in total, and a single insert into a table of a
 * million rows collides with probability 10^6 / 3.5 x 10^12, under one in three million.
 *
 * <p>The alphabet is a strict subset of the alias pattern {@code [A-Za-z0-9_-]}, so a generated
 * code and a custom alias can never differ only in a character the path regex would reject.
 */
@Component
public final class CodeGenerator {

  /** Characters in a generated code. */
  public static final int LENGTH = 7;

  static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  private final Random random;

  /** Production constructor: cryptographically strong randomness. */
  public CodeGenerator() {
    this(new SecureRandom());
  }

  /**
   * Test constructor: any {@link Random}, for example a seeded one, so sequences are reproducible.
   *
   * @param random the source of randomness
   */
  public CodeGenerator(Random random) {
    this.random = random;
  }

  /**
   * Draws a fresh code.
   *
   * @return exactly {@link #LENGTH} characters from the Base62 alphabet
   */
  public String next() {
    char[] code = new char[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
    }
    return new String(code);
  }
}
