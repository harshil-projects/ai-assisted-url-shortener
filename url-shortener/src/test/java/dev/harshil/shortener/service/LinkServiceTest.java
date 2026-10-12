package dev.harshil.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.domain.AliasTakenException;
import dev.harshil.shortener.domain.CodeGenerationExhaustedException;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.domain.UrlValidationException;
import dev.harshil.shortener.domain.UrlValidationException.FieldError;
import dev.harshil.shortener.repository.LinkRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class LinkServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
  private static final String URL = "https://example.com/page";
  private static final DataIntegrityViolationException CODE_COLLISION =
      new DataIntegrityViolationException(
          "could not execute statement [Unique index or primary key violation: "
              + "\"PUBLIC.UK_LINKS_CODE_INDEX_6 ON PUBLIC.LINKS(CODE)\"]");

  @Mock private LinkRepository links;
  @Mock private LinkAllocator allocator;
  @Mock private CodeGenerator generator;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private LinkService service;

  @BeforeEach
  void createService() {
    service =
        new LinkService(
            links,
            allocator,
            generator,
            new UrlValidator(),
            Clock.fixed(NOW, ZoneOffset.UTC),
            registry);
  }

  @Test
  void create_withoutAlias_validatesCanonicalisesAndInsertsGeneratedCodeOnce() {
    when(generator.next()).thenReturn("Abc1234");
    when(allocator.insert("Abc1234", URL, NOW)).thenReturn(new Link("Abc1234", URL, NOW));

    Link created = service.create(new CreateLinkRequest("HTTPS://EXAMPLE.COM/page", null));

    assertThat(created.getCode()).isEqualTo("Abc1234");
    verify(allocator, times(1)).insert(anyString(), anyString(), any());
    verifyNoInteractions(links);
  }

  @Test
  void create_randomCodeCollides_retriesWithTheNextCode() {
    when(generator.next()).thenReturn("taken01", "fresh02");
    when(allocator.insert("taken01", URL, NOW)).thenThrow(CODE_COLLISION);
    when(allocator.insert("fresh02", URL, NOW)).thenReturn(new Link("fresh02", URL, NOW));

    Link created = service.create(new CreateLinkRequest(URL, null));

    assertThat(created.getCode()).isEqualTo("fresh02");
    verify(generator, times(2)).next();
  }

  @Test
  void create_fiveRandomCollisions_throwsCodeGenerationExhaustedAfterExactlyFiveAttempts() {
    when(generator.next()).thenReturn("col0001");
    when(allocator.insert(anyString(), anyString(), any())).thenThrow(CODE_COLLISION);

    assertThatThrownBy(() -> service.create(new CreateLinkRequest(URL, null)))
        .isInstanceOf(CodeGenerationExhaustedException.class);

    verify(allocator, times(LinkService.MAX_ATTEMPTS)).insert(anyString(), anyString(), any());
    assertThat(registry.counter(LinkService.LINKS_CREATED_METRIC).count()).isZero();
  }

  @Test
  void create_integrityViolationOnAnotherConstraint_propagatesWithoutRetry() {
    when(generator.next()).thenReturn("code001");
    DataIntegrityViolationException other =
        new DataIntegrityViolationException("NULL not allowed for column \"ORIGINAL_URL\"");
    when(allocator.insert(anyString(), anyString(), any())).thenThrow(other);

    assertThatThrownBy(() -> service.create(new CreateLinkRequest(URL, null))).isSameAs(other);

    verify(allocator, times(1)).insert(anyString(), anyString(), any());
  }

  @Test
  void create_withAlias_insertsTheAliasWithoutUsingTheGenerator() {
    when(links.existsByCode("my-alias")).thenReturn(false);
    when(allocator.insert("my-alias", URL, NOW)).thenReturn(new Link("my-alias", URL, NOW));

    Link created = service.create(new CreateLinkRequest(URL, "my-alias"));

    assertThat(created.getCode()).isEqualTo("my-alias");
    verifyNoInteractions(generator);
  }

  @Test
  void create_reservedAlias_throwsUrlValidationOnCustomAliasBeforeTouchingTheDatabase() {
    assertThatThrownBy(() -> service.create(new CreateLinkRequest(URL, "Admin")))
        .isInstanceOfSatisfying(
            UrlValidationException.class,
            ex ->
                assertThat(ex.getErrors())
                    .containsExactly(
                        new FieldError(LinkService.ALIAS_FIELD, LinkService.MSG_ALIAS_RESERVED)));

    verifyNoInteractions(links, allocator, generator);
  }

  @Test
  void create_aliasFailingThePattern_throwsUrlValidationEvenWhenBeanValidationWasBypassed() {
    assertThatThrownBy(() -> service.create(new CreateLinkRequest(URL, "ab")))
        .isInstanceOfSatisfying(
            UrlValidationException.class,
            ex ->
                assertThat(ex.getErrors())
                    .containsExactly(
                        new FieldError(LinkService.ALIAS_FIELD, LinkService.MSG_ALIAS_SHAPE)));

    verifyNoInteractions(links, allocator);
  }

  @Test
  void create_aliasAlreadyStored_throwsAliasTakenWithoutAttemptingAnInsert() {
    when(links.existsByCode("taken")).thenReturn(true);

    assertThatThrownBy(() -> service.create(new CreateLinkRequest(URL, "taken")))
        .isInstanceOf(AliasTakenException.class)
        .hasMessageContaining("taken");

    verify(allocator, never()).insert(anyString(), anyString(), any());
  }

  @Test
  void create_aliasLosesTheRaceOnTheConstraint_mapsViolationToAliasTakenWithCause() {
    when(links.existsByCode("raced")).thenReturn(false);
    DataIntegrityViolationException viaCause =
        new DataIntegrityViolationException(
            "could not execute statement",
            new SQLException("duplicate key value violates unique constraint \"uk_links_code\""));
    when(allocator.insert("raced", URL, NOW)).thenThrow(viaCause);

    assertThatThrownBy(() -> service.create(new CreateLinkRequest(URL, "raced")))
        .isInstanceOf(AliasTakenException.class)
        .hasCause(viaCause);
  }

  @Test
  void create_aliasInsertFailsOnAnotherConstraint_propagatesTheOriginalException() {
    when(links.existsByCode("alias1")).thenReturn(false);
    DataIntegrityViolationException other = new DataIntegrityViolationException("check failed");
    when(allocator.insert("alias1", URL, NOW)).thenThrow(other);

    assertThatThrownBy(() -> service.create(new CreateLinkRequest(URL, "alias1"))).isSameAs(other);
  }

  @Test
  void create_invalidUrl_throwsUrlValidationBeforeAnyCollaboratorIsCalled() {
    assertThatThrownBy(() -> service.create(new CreateLinkRequest("ftp://example.com/x", null)))
        .isInstanceOf(UrlValidationException.class);

    verifyNoInteractions(links, allocator, generator);
  }

  @Test
  void create_success_incrementsTheLinksCreatedCounterOnce() {
    when(generator.next()).thenReturn("Abc1234");
    when(allocator.insert("Abc1234", URL, NOW)).thenReturn(new Link("Abc1234", URL, NOW));

    service.create(new CreateLinkRequest(URL, null));

    assertThat(registry.counter(LinkService.LINKS_CREATED_METRIC).count()).isEqualTo(1.0);
  }

  @Test
  void resolve_knownCode_returnsTheLink() {
    Link stored = new Link("known01", URL, NOW);
    when(links.findByCode("known01")).thenReturn(Optional.of(stored));

    assertThat(service.resolve("known01")).isSameAs(stored);
  }

  @Test
  void resolve_unknownCode_throwsLinkNotFoundCarryingTheCode() {
    when(links.findByCode("nothere")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolve("nothere"))
        .isInstanceOfSatisfying(
            LinkNotFoundException.class, ex -> assertThat(ex.getCode()).isEqualTo("nothere"));
  }

  @Test
  void get_knownCode_usesTheSameSingleLookupAsResolve() {
    Link stored = new Link("known01", URL, NOW);
    when(links.findByCode("known01")).thenReturn(Optional.of(stored));

    assertThat(service.get("known01")).isSameAs(stored);
    verify(links, times(1)).findByCode(eq("known01"));
  }

  @Test
  void delete_knownCode_deletesThroughTheRepository() {
    when(links.deleteByCode("gone001")).thenReturn(1L);

    service.delete("gone001");

    verify(links).deleteByCode("gone001");
  }

  @Test
  void delete_unknownCode_throwsLinkNotFound() {
    when(links.deleteByCode("nothere")).thenReturn(0L);

    assertThatThrownBy(() -> service.delete("nothere")).isInstanceOf(LinkNotFoundException.class);
  }
}
