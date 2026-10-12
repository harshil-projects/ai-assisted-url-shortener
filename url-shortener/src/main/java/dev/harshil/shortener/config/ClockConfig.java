package dev.harshil.shortener.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the application clock as a bean so every timestamp (creation, clicks, stats windows) is
 * taken from one injectable source. Production uses UTC; tests substitute a fixed clock and get
 * deterministic windows instead of racing the wall clock (design 3.13).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
