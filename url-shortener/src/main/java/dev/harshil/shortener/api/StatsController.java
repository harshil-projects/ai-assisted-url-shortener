package dev.harshil.shortener.api;

import dev.harshil.shortener.api.dto.StatsResponse;
import dev.harshil.shortener.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Click statistics at {@code GET /api/v1/links/{code}/stats} (design 3.3), protected by the API
 * key. Kept apart from {@link LinkController} because it depends on the analytics service, not on
 * link management, and because the ambiguous scenario may grow it independently.
 */
@RestController
@RequestMapping("/api/v1/links")
public class StatsController {

  static final String STATS_PATH = "/{code:[A-Za-z0-9_-]{4,32}}/stats";

  private final AnalyticsService analytics;

  /**
   * Creates the controller.
   *
   * @param analytics the stats computation
   */
  public StatsController(AnalyticsService analytics) {
    this.analytics = analytics;
  }

  /**
   * Returns the statistics for a link.
   *
   * @param code the short code
   * @return 200 with the statistics
   */
  @GetMapping(STATS_PATH)
  public StatsResponse stats(@PathVariable String code) {
    return analytics.stats(code);
  }
}
