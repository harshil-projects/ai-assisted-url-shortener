package dev.harshil.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CodeGeneratorTest {

  private static final int DRAWS = 1_000;

  @Test
  void next_withSecureRandomDefault_returnsSevenBase62Characters() {
    CodeGenerator generator = new CodeGenerator();

    IntStream.range(0, DRAWS)
        .mapToObj(ignored -> generator.next())
        .forEach(code -> assertThat(code).hasSize(7).matches("[A-Za-z0-9]{7}"));
  }

  @Test
  void next_withSameSeed_isReproducible() {
    CodeGenerator first = new CodeGenerator(new Random(42));
    CodeGenerator second = new CodeGenerator(new Random(42));

    assertThat(first.next()).isEqualTo(second.next());
    assertThat(first.next()).isEqualTo(second.next());
  }

  @Test
  void next_withDifferentSeeds_producesDifferentCodes() {
    CodeGenerator first = new CodeGenerator(new Random(1));
    CodeGenerator second = new CodeGenerator(new Random(2));

    assertThat(first.next()).isNotEqualTo(second.next());
  }

  @Test
  void next_drawsEveryPositionFromTheFullAlphabetWithBound62() {
    CodeGenerator generator = new CodeGenerator(new CountingRandom());

    StringBuilder emitted = new StringBuilder();
    for (int i = 0; i < CodeGenerator.ALPHABET.length(); i++) {
      emitted.append(generator.next());
    }

    assertThat(emitted.toString()).startsWith(CodeGenerator.ALPHABET);
  }

  @Test
  void alphabet_hasSixtyTwoDistinctCharactersThatAllMatchTheAliasPattern() {
    assertThat(CodeGenerator.ALPHABET).hasSize(62).matches("[A-Za-z0-9]{62}");
    assertThat(CodeGenerator.ALPHABET.chars().distinct().count()).isEqualTo(62);
    assertThat(CodeGenerator.LENGTH).isEqualTo(7);
  }

  /** Returns 0, 1, 2, ... modulo the bound, and records that the bound is the alphabet size. */
  private static final class CountingRandom extends Random {

    private static final long serialVersionUID = 1L;
    private int next;

    @Override
    public int nextInt(int bound) {
      assertThat(bound).isEqualTo(CodeGenerator.ALPHABET.length());
      return next++ % bound;
    }
  }
}
