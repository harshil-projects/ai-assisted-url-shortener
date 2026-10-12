package dev.harshil.shortener.api;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.api.dto.LinkResponse;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.service.LinkService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Management API for links under {@code /api/v1/links} (design 3.3), protected by the API key.
 *
 * <p>The {@code code} path variable carries the same pattern as a valid alias, so a request for a
 * code that could never exist is answered by the framework's 404 before any service or database
 * call. Error bodies come from {@link ApiExceptionHandler}; this class only maps success.
 */
@RestController
@RequestMapping("/api/v1/links")
public class LinkController {

  /** Path segment accepted as a code; mirrors {@link CreateLinkRequest#ALIAS_PATTERN}. */
  static final String CODE_PATH = "/{code:[A-Za-z0-9_-]{4,32}}";

  private static final String LOCATION_PREFIX = "/api/v1/links/";

  private final LinkService links;
  private final ShortUrlBuilder shortUrls;

  /**
   * Creates the controller.
   *
   * @param links link use cases
   * @param shortUrls short URL rendering
   */
  public LinkController(LinkService links, ShortUrlBuilder shortUrls) {
    this.links = links;
    this.shortUrls = shortUrls;
  }

  /**
   * Creates a link.
   *
   * @param request the JSON body
   * @return 201 with {@code Location: /api/v1/links/{code}} and the link
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<LinkResponse> create(@Valid @RequestBody CreateLinkRequest request) {
    Link link = links.create(request);
    return ResponseEntity.created(URI.create(LOCATION_PREFIX + link.getCode()))
        .body(toResponse(link));
  }

  /**
   * Returns a link's metadata.
   *
   * @param code the short code
   * @return 200 with the link
   */
  @GetMapping(CODE_PATH)
  public LinkResponse get(@PathVariable String code) {
    return toResponse(links.get(code));
  }

  /**
   * Deletes a link and its click events.
   *
   * @param code the short code
   * @return 204 with no body
   */
  @DeleteMapping(CODE_PATH)
  public ResponseEntity<Void> delete(@PathVariable String code) {
    links.delete(code);
    return ResponseEntity.noContent().build();
  }

  private LinkResponse toResponse(Link link) {
    return LinkResponse.of(link, shortUrls.shortUrl(link.getCode()));
  }
}
