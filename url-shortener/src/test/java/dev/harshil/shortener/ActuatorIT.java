package dev.harshil.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestApiKey;
import dev.harshil.shortener.support.TestProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The operational endpoints of design section 3.8: public probes, and build info and Prometheus
 * metrics behind the API key. Build info requires the {@code build-info} Maven goal to have run (it
 * does in every {@code mvnw} build); an IDE run without it fails the info check on purpose.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class ActuatorIT {

  @Autowired private RestTestClient client;

  @Test
  void health_isPublicAndUp() {
    client
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
  void probes_arePublicAndUp(String path) {
    client
        .get()
        .uri(path)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void info_withKey_exposesBuildVersion() {
    client
        .get()
        .uri("/actuator/info")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.build.version")
        .exists()
        .jsonPath("$.build.artifact")
        .isEqualTo("url-shortener");
  }

  @Test
  void prometheus_withKey_exposesJvmMetrics() {
    client
        .get()
        .uri("/actuator/prometheus")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
  }
}
