package dev.harshil.shortener.support;

/**
 * The API key configured by the test profile (application-test.properties) and the header it
 * travels in, shared by every integration test so a key change is a one-line edit.
 */
public final class TestApiKey {

  public static final String HEADER = "X-API-Key";
  public static final String VALUE = "test-api-key";

  private TestApiKey() {}
}
