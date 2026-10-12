package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.harshil.shortener.api.dto.StatsResponse;
import dev.harshil.shortener.config.SecurityConfig;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.service.AnalyticsService;
import dev.harshil.shortener.support.TestProfile;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(StatsController.class)
@Import(SecurityConfig.class)
@TestProfile
class StatsControllerTest {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String API_KEY = "test-api-key";
  private static final String STATS = "/api/v1/links/abcdefg/stats";

  @Autowired private MockMvcTester mvc;
  @MockitoBean private AnalyticsService analyticsService;

  @Test
  void stats_knownCodeWithKey_returns200WithEveryField() {
    Instant lastClick = Instant.parse("2026-10-12T11:57:00Z");
    when(analyticsService.stats("abcdefg"))
        .thenReturn(new StatsResponse("abcdefg", 10L, 2L, 7L, lastClick));

    assertThat(mvc.get().uri(STATS).header(API_KEY_HEADER, API_KEY))
        .hasStatusOk()
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("abcdefg"))
        .hasPathSatisfying("$.totalClicks", total -> total.assertThat().isEqualTo(10))
        .hasPathSatisfying("$.clicksLast24h", day -> day.assertThat().isEqualTo(2))
        .hasPathSatisfying("$.clicksLast7d", week -> week.assertThat().isEqualTo(7))
        .hasPathSatisfying(
            "$.lastClickedAt", at -> at.assertThat().isEqualTo("2026-10-12T11:57:00Z"));
  }

  @Test
  void stats_linkNeverClicked_rendersNullLastClickedAtExplicitly() {
    when(analyticsService.stats("abcdefg"))
        .thenReturn(new StatsResponse("abcdefg", 0L, 0L, 0L, null));

    assertThat(mvc.get().uri(STATS).header(API_KEY_HEADER, API_KEY))
        .hasStatusOk()
        .bodyJson()
        .hasPathSatisfying("$.totalClicks", total -> total.assertThat().isEqualTo(0))
        .hasPathSatisfying("$.lastClickedAt", at -> at.assertThat().isNull());
  }

  @Test
  void stats_unknownCode_returns404LinkNotFoundProblem() {
    when(analyticsService.stats("abcdefg")).thenThrow(new LinkNotFoundException("abcdefg"));

    assertThat(mvc.get().uri(STATS).header(API_KEY_HEADER, API_KEY))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/link-not-found"));
  }

  @Test
  void stats_withoutApiKey_returns401AndNeverReachesTheService() {
    assertThat(mvc.get().uri(STATS)).hasStatus(HttpStatus.UNAUTHORIZED);

    verifyNoInteractions(analyticsService);
  }

  @Test
  void stats_codeFailingThePathPattern_returns404WithoutCallingTheService() {
    assertThat(mvc.get().uri("/api/v1/links/ab/stats").header(API_KEY_HEADER, API_KEY))
        .hasStatus(HttpStatus.NOT_FOUND);

    verifyNoInteractions(analyticsService);
  }
}
