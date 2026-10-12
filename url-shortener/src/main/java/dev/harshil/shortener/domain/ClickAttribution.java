package dev.harshil.shortener.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Map;

/**
 * Where a click came from, reduced to what marketing asked for and to nothing that identifies the
 * visitor (R-03 decision, ADR 0006): the host of the {@code Referer} header and the three {@code
 * utm_*} parameters carried on the short link request. Nothing else from the request is read; no
 * address, no user agent, no full referrer URL, no location, no device.
 *
 * <p>{@link #from(String, Map)} runs on the request thread and never throws: anything absent,
 * malformed or empty becomes {@code null}, so a hostile header can never cost a redirect or a
 * click. Values are bounded to their column widths so the insert cannot fail on length either.
 *
 * @param referrerHost lower-cased host of the referring page, at most {@value #MAX_HOST_LENGTH}
 *     characters, or {@code null} when the header was absent, relative or not a URL
 * @param utmSource {@code utm_source} with control characters removed, trimmed, at most {@value
 *     #MAX_UTM_LENGTH} characters, or {@code null} when absent or empty after cleaning
 * @param utmMedium {@code utm_medium}, same rules as {@code utmSource}
 * @param utmCampaign {@code utm_campaign}, same rules as {@code utmSource}
 */
public record ClickAttribution(
    String referrerHost, String utmSource, String utmMedium, String utmCampaign) {

  /** Attribution of a click that carried neither a usable referer nor campaign parameters. */
  public static final ClickAttribution NONE = new ClickAttribution(null, null, null, null);

  /** Longest host kept: the DNS limit for a fully qualified name, and the column width. */
  public static final int MAX_HOST_LENGTH = 253;

  /** Longest campaign value kept: the column width. */
  public static final int MAX_UTM_LENGTH = 64;

  static final String UTM_SOURCE = "utm_source";
  static final String UTM_MEDIUM = "utm_medium";
  static final String UTM_CAMPAIGN = "utm_campaign";

  /**
   * Derives the attribution of one redirect request. Never throws.
   *
   * @param refererHeader the raw {@code Referer} header, or {@code null} when absent
   * @param query the request's parameter map as the servlet container parsed it, or {@code null}
   * @return the attribution; {@link #NONE} when nothing usable was present
   */
  public static ClickAttribution from(String refererHeader, Map<String, String[]> query) {
    return new ClickAttribution(
        referrerHost(refererHeader),
        utm(query, UTM_SOURCE),
        utm(query, UTM_MEDIUM),
        utm(query, UTM_CAMPAIGN));
  }

  /**
   * Keeps only the lower-cased host of an absolute referer URL. Userinfo, port, path, query and
   * fragment are discarded; a relative reference, an opaque URI such as {@code data:} or {@code
   * mailto:}, or anything {@link URI} rejects yields {@code null}.
   */
  static String referrerHost(String referer) {
    if (referer == null || referer.isEmpty()) {
      return null;
    }
    try {
      URI uri = new URI(referer);
      if (!uri.isAbsolute() || uri.getHost() == null) {
        return null;
      }
      return truncate(uri.getHost().toLowerCase(Locale.ROOT), MAX_HOST_LENGTH);
    } catch (URISyntaxException e) {
      return null;
    }
  }

  /** First value of the key with ISO control characters removed, trimmed and bounded. */
  static String utm(Map<String, String[]> query, String key) {
    if (query == null) {
      return null;
    }
    String[] values = query.get(key);
    if (values == null || values.length == 0 || values[0] == null) {
      return null;
    }
    String cleaned = truncate(stripControls(values[0]).strip(), MAX_UTM_LENGTH);
    return cleaned.isEmpty() ? null : cleaned;
  }

  private static String stripControls(String value) {
    StringBuilder kept = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char current = value.charAt(i);
      if (!Character.isISOControl(current)) {
        kept.append(current);
      }
    }
    return kept.toString();
  }

  /** Cuts to {@code max} characters without splitting a surrogate pair at the cut. */
  private static String truncate(String value, int max) {
    if (value.length() <= max) {
      return value;
    }
    int end = Character.isHighSurrogate(value.charAt(max - 1)) ? max - 1 : max;
    return value.substring(0, end);
  }
}
