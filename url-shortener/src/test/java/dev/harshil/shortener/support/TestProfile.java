package dev.harshil.shortener.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;

/**
 * Activates the {@code test} profile so every test, slice or full context, runs against the
 * in-memory H2 database in PostgreSQL compatibility mode defined in application-test.properties.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ActiveProfiles("test")
public @interface TestProfile {}
