---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# Java 21 with Spring Boot 4.1.1 built by the Maven Wrapper

In the context of a URL shortener delivered for the assignment brief to reviewers on locked-down Windows laptops, facing the need for a supported Java stack that builds with nothing but a JDK, we decided for Java 21, Spring Boot 4.1.1, and the Maven Wrapper pinned to Maven 3.9.16, and neglected Spring Boot 3.5, Java 25, and Gradle, to achieve a build that is green on a clean machine and supported through the review period, accepting that AI assistants still emit Boot 3 idioms that must be caught and rejected.

## Context and Problem Statement

The role requires Java. Reviewers have JDK 21, no admin rights, no Docker, and possibly a corporate proxy. The service must build and run from one command on Windows and Linux. Which Java version, framework line, and build tool give the longest support window with the lowest install burden?

## Decision Drivers

| Driver | Weight |
|---|---|
| Reviewer environment: JDK 21 present, nothing else installable | High |
| OSS support window covering the review period and beyond | High |
| Enterprise familiarity of the stack | High |
| One-command portable build on Windows and Linux | High |
| AI assistant accuracy on the chosen framework version | Medium |

## Considered Options

1. Java 21, Spring Boot 4.1.1, Maven Wrapper
2. Java 21, Spring Boot 3.5, Maven Wrapper
3. Java 25, Spring Boot 4.1.1, Maven Wrapper
4. Java 21, Spring Boot 4.1.1, Gradle Wrapper

## Decision Outcome

Chosen option 1. Boot 4.1.x has OSS support to 2027-07-31. Boot 4.0.x ends 2026-12-31 and Boot 3.5 OSS support ended in June 2026. Java 21 is the LTS reviewers already have and sits inside Boot's tested range. Maven Wrapper 3.3.4 downloads Maven 3.9.16 once, so reviewers install nothing. The choice is not resume-driven: the ledger records every rejected Boot 3 draft, which shows the cost of the newer line was paid knowingly.

### Consequences

| Kind | Consequence |
|---|---|
| Good | Build runs on any machine with JDK 21 and one dependency download |
| Good | Framework supported for the life of the repository's review |
| Good | Boot 4 test starters give `MockMvcTester` and `RestTestClient` without extra artifacts |
| Bad | AI drafts often use Boot 3 idioms (`spring-boot-starter-web`, `@MockBean`, `com.fasterxml` packages); each rejection is recorded in the ledger |
| Bad | springdoc and Bucket4j choices are constrained to the Boot 4 compatible lines |
| Bad | Enforcer must guard against JDK 8 or 17 on reviewer machines |

### Confirmation

| Check | Where defined |
|---|---|
| `mvnw verify` green on `windows-latest` and `ubuntu-latest` | NFR-01, CI matrix |
| Enforcer `requireJavaVersion [21,27)` and `requireMavenVersion [3.9,4)` fail fast | Environment gate, section 4.1 |
| Rejected Boot 3 drafts appear in `harness/ledger.yaml` with notes | HC-11 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| Java 21, Boot 4.1.1, Maven Wrapper | Longest support; reviewer JDK; Boot 4 test starters; wrapper needs no install | AI Boot 3 drift; newer line with fewer examples online |
| Java 21, Boot 3.5 | Most AI training data; familiar | OSS support ended June 2026; choosing it for familiarity alone would be hard to defend |
| Java 25, Boot 4.1.1 | Newer LTS; JEP 491 removes virtual thread pinning | Reviewers do not have it; less common in enterprises; pinning is moot because platform threads are used |
| Gradle Wrapper | Faster incremental builds | Less common in Java enterprise teams; Spotless, SpotBugs, JaCoCo, and Checkstyle are better documented for Maven |

## More Information

Spring Boot 4.1.1 system requirements; Spring Boot 4.0 migration guide; Spring Boot 4.1 release notes; Spring Framework 7 release notes; Jackson 3.0 release notes and Spring's Jackson 3 announcement; Oracle Java 21 virtual threads guide; JEP 491; Apache Maven download, wrapper, and proxy guides; GitHub Actions setup-java; Adoptium API.
