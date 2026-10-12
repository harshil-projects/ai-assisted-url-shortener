package dev.harshil.shortener.service;

import dev.harshil.shortener.domain.UrlValidationException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Decides whether a submitted URL may become a redirect target and returns its canonical form
 * (design 3.6, 3.7).
 *
 * <p>The threat is not SSRF, because this service never fetches the target; it is link laundering:
 * a trusted short domain pointing a victim at an intranet or cloud-metadata address. So the rules
 * are structural (absolute {@code http} or {@code https}, a host, no credentials, bounded length)
 * plus a block list of literal addresses and the two host names attackers reach for. Host names are
 * never resolved: a lookup would turn every create request into a DNS query the client controls,
 * and a name that later resolves to a private address is a documented, accepted limitation.
 *
 * <p>Numeric hosts are accepted only as a canonical dotted quad or a bracketed IPv6 literal. The
 * shorthand forms browsers also understand, {@code 2130706433}, {@code 0x7f000001}, {@code
 * 0177.0.0.1} or {@code 012.0.0.1}, are rejected outright: {@link URI} reports them as host names,
 * so they would bypass the address block list while the browser still lands on 127.0.0.1 or
 * 10.0.0.1.
 *
 * <p>Canonicalisation lowercases only the scheme and the host, which are case-insensitive by RFC
 * 3986; path, query and fragment are preserved byte for byte, including existing percent-encoding,
 * because changing them could change where the link lands.
 */
@Component
public final class UrlValidator {

  /** Longest canonical URL stored; mirrors the {@code original_url} column. */
  public static final int MAX_LENGTH = 2048;

  static final String FIELD = "url";
  static final String MSG_REQUIRED = "URL is required";
  static final String MSG_MALFORMED = "URL is not well-formed";
  static final String MSG_SCHEME = "URL must be absolute and use the http or https scheme";
  static final String MSG_USER_INFO = "URL must not contain credentials";
  static final String MSG_HOST = "URL must have a valid host name or a canonical IP address";
  static final String MSG_BLOCKED_HOST =
      "URL must not point to a loopback, private, link-local, multicast or metadata address";
  static final String MSG_TOO_LONG = "URL must be at most " + MAX_LENGTH + " characters";

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
  private static final Set<String> BLOCKED_HOST_NAMES =
      Set.of("localhost", "metadata.google.internal");
  private static final String METADATA_ADDRESS = "169.254.169.254";
  private static final String DECIMAL_OCTET = "25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9]";
  private static final Pattern CANONICAL_IPV4 =
      Pattern.compile("^(?:" + DECIMAL_OCTET + ")(?:\\.(?:" + DECIMAL_OCTET + ")){3}$");
  private static final Pattern NUMERIC_LABEL = Pattern.compile("^(?:0[xX][0-9a-fA-F]*|[0-9]+)$");
  private static final int IPV4_LENGTH = 4;
  private static final int UNIQUE_LOCAL_MASK = 0xFE;
  private static final int UNIQUE_LOCAL_PREFIX = 0xFC;

  /**
   * Validates and canonicalises a URL.
   *
   * @param raw the value exactly as the client sent it; surrounding whitespace is tolerated
   * @return the canonical URL: lowercase scheme and host, everything else untouched
   * @throws UrlValidationException with field {@code url} and one fixed message per broken rule
   */
  public String validate(String raw) {
    if (raw == null || raw.isBlank()) {
      throw reject(MSG_REQUIRED);
    }
    URI uri = parse(raw.strip());
    String scheme = uri.getScheme();
    if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
      throw reject(MSG_SCHEME);
    }
    if (uri.getRawUserInfo() != null) {
      throw reject(MSG_USER_INFO);
    }
    String host = uri.getHost();
    if (host == null || host.isEmpty() || isAmbiguousNumericHost(host)) {
      throw reject(MSG_HOST);
    }
    if (isBlockedHost(host)) {
      throw reject(MSG_BLOCKED_HOST);
    }
    String canonical = canonicalise(uri, host);
    if (canonical.length() > MAX_LENGTH) {
      throw reject(MSG_TOO_LONG);
    }
    return canonical;
  }

  private static URI parse(String value) {
    try {
      return new URI(value);
    } catch (URISyntaxException e) {
      throw reject(MSG_MALFORMED);
    }
  }

  private static UrlValidationException reject(String message) {
    return UrlValidationException.of(FIELD, message);
  }

  private static String canonicalise(URI uri, String host) {
    StringBuilder canonical =
        new StringBuilder(uri.getScheme().toLowerCase(Locale.ROOT))
            .append("://")
            .append(host.toLowerCase(Locale.ROOT));
    if (uri.getPort() != -1) {
      canonical.append(':').append(uri.getPort());
    }
    canonical.append(uri.getRawPath());
    if (uri.getRawQuery() != null) {
      canonical.append('?').append(uri.getRawQuery());
    }
    if (uri.getRawFragment() != null) {
      canonical.append('#').append(uri.getRawFragment());
    }
    return canonical.toString();
  }

  /**
   * True for a host that is neither a bracketed IPv6 literal nor a canonical dotted quad but whose
   * rightmost label is a decimal or hexadecimal number. RFC 1123 forbids an all-numeric top label,
   * and browsers treat such hosts as IPv4 numbers, so the only safe answer is to refuse them.
   */
  private static boolean isAmbiguousNumericHost(String host) {
    if (host.startsWith("[") || CANONICAL_IPV4.matcher(host).matches()) {
      return false;
    }
    String name = withoutTrailingDot(host);
    String lastLabel = name.substring(name.lastIndexOf('.') + 1);
    return NUMERIC_LABEL.matcher(lastLabel).matches();
  }

  /**
   * Blocks the two well-known names and every literal address in a non-public range. A plain host
   * name that is not on the list passes without any resolution.
   */
  private static boolean isBlockedHost(String host) {
    String name = withoutTrailingDot(host.toLowerCase(Locale.ROOT));
    if (BLOCKED_HOST_NAMES.contains(name)) {
      return true;
    }
    return literalAddress(host).map(UrlValidator::isBlockedAddress).orElse(false);
  }

  private static String withoutTrailingDot(String host) {
    return host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
  }

  /**
   * Parses an IP literal without touching the resolver. A canonical dotted quad is parsed by hand
   * into bytes; an IPv6 literal is recognised by the brackets {@link URI} guarantees around it, and
   * the JDK converts a bracketed value textually, never through DNS. Anything else is a host name
   * and yields empty.
   */
  private static Optional<InetAddress> literalAddress(String host) {
    if (host.startsWith("[")) {
      return Optional.of(ipv6Literal(host));
    }
    if (!CANONICAL_IPV4.matcher(host).matches()) {
      return Optional.empty();
    }
    String[] parts = host.split("\\.");
    byte[] octets = new byte[IPV4_LENGTH];
    for (int i = 0; i < IPV4_LENGTH; i++) {
      octets[i] = (byte) Integer.parseInt(parts[i]);
    }
    return Optional.of(ipv4Literal(octets));
  }

  private static InetAddress ipv6Literal(String bracketed) {
    try {
      return InetAddress.getByName(bracketed);
    } catch (UnknownHostException e) {
      // URI already validated the literal syntax, so this is unreachable in practice; failing
      // closed keeps a malformed literal out of the database.
      throw reject(MSG_HOST);
    }
  }

  private static InetAddress ipv4Literal(byte[] octets) {
    try {
      return InetAddress.getByAddress(octets);
    } catch (UnknownHostException e) {
      // Only thrown for an illegal length, which the pattern rules out.
      throw reject(MSG_HOST);
    }
  }

  private static boolean isBlockedAddress(InetAddress address) {
    byte[] bytes = address.getAddress();
    boolean uniqueLocalIpv6 =
        bytes.length != IPV4_LENGTH && (bytes[0] & UNIQUE_LOCAL_MASK) == UNIQUE_LOCAL_PREFIX;
    boolean zeroNetworkIpv4 = bytes.length == IPV4_LENGTH && bytes[0] == 0;
    return address.isLoopbackAddress()
        || address.isSiteLocalAddress()
        || address.isLinkLocalAddress()
        || address.isAnyLocalAddress()
        || address.isMulticastAddress()
        || zeroNetworkIpv4
        || uniqueLocalIpv6
        || METADATA_ADDRESS.equals(address.getHostAddress());
  }
}
