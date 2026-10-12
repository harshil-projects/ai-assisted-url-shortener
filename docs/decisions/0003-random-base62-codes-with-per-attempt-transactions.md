---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# Random 7-character Base62 codes with database-enforced uniqueness and one transaction per attempt

In the context of allocating short codes for a single-node shortener whose codes must not be enumerable, facing the choice between random codes and a coordinated counter, we decided for 7-character Base62 codes drawn from `SecureRandom` with uniqueness enforced by the named constraint `uk_links_code` and a bounded retry where each attempt runs in its own transaction, and neglected Base62 of a counter (including the Redis `INCR` design with block reservation and per-region ranges), a hash of the URL, and Snowflake ids, to achieve non-enumerable codes with no extra infrastructure, accepting a small, handled collision rate and codes that cannot be decoded to a row id.

## Context and Problem Statement

Every link needs a short, URL-safe, unique code. Sequential codes can be scanned; the 2016 "Gone in Six Characters" study found private cloud documents that way. A collision must never produce a wrong redirect, and a failed attempt must not poison the request's transaction. How are codes generated and made unique on one node, and when would that change?

## Decision Drivers

| Driver | Weight |
|---|---|
| Codes non-enumerable by construction | High |
| No infrastructure beyond the database | High |
| Correct under concurrent inserts, including custom alias races | High |
| Short codes, 7 characters or fewer | Medium |
| A clear switch condition for the distributed design | Medium |

## Considered Options

1. Random 7-character Base62, unique constraint, bounded retry, one transaction per attempt
2. Base62 of a counter, with the Redis `INCR` design for many writers
3. Hash of the URL, truncated to 7 characters
4. Snowflake-style time-ordered ids encoded in Base62

## Decision Outcome

Chosen option 1. `LinkService.create` validates, then loops up to five times calling `LinkAllocator.insert`, a separate bean whose method is `@Transactional`, so each attempt is its own transaction. `DataIntegrityViolationException` is caught outside the proxy and the constraint name is checked. A collision on a random code retries. A collision on a custom alias maps to 409, with `existsByCode(alias)` as the fast path before the insert.

Keyspace and collision math:

| Quantity | Value |
|---|---|
| Alphabet | `[0-9A-Za-z]`, 62 symbols, URL-safe, no escaping |
| Code | 7 draws of `SecureRandom.nextInt(62)` from one shared instance |
| Keyspace N | 62^7 = 3,521,614,606,208, about 3.5 × 10^12 |
| Expected collisions while inserting n links | about n² / (2N), the birthday bound |
| n = 1,000,000 | about 0.14 collisions in total |
| n = 10,000,000 | about 14 collisions in total |
| Probability one insert collides with n rows present | n / N; at n = 10^6 about 2.8 × 10^-7 |
| Probability of five collisions in a row at n = 10^6 | (n / N)^5, below 10^-32 |
| Keyspace lifetime at 1,000 new links per second | about 111 years |

The Redis counter alternative, as the common interview treatment ends: `INCR` on one key yields a monotonic id that Base62 encodes to at most 7 characters. Block reservation has each write instance call `INCRBY counter 1000` and hand out the block locally, so Redis is hit once per thousand creates and an instance survives a short Redis outage until its block runs out. Per-region ranges give each region a disjoint id range, for example a leading region digit or a fixed offset, so regions allocate without cross-region coordination. The unused tail of a block is lost when an instance crashes, which is acceptable.

Why not here: sequential ids are enumerable, and XOR or Hashids obfuscation is reversible so it is not a security control; it needs Redis, which reviewers cannot run; one node has no coordination problem to solve.

Switch condition: adopt the counter design when more than one write instance must allocate codes and the ids must be decodable to a shard or row. A secondary trigger is observed collision retries above one percent of creates, which the keyspace predicts only past about 35 billion links.

### Consequences

| Kind | Consequence |
|---|---|
| Good | Non-enumerable without any obfuscation layer |
| Good | The `INSERT` is the uniqueness check; no separate lookup round trip for random codes |
| Good | A lost race on a custom alias maps to 409, never a silent retry |
| Good | A failed attempt cannot mark the request transaction rollback-only |
| Bad | Collisions exist and need a real-database test |
| Bad | Five consecutive collisions surface as `code-generation-exhausted` 500, astronomically rare |
| Bad | Codes carry no information; sharding by id later needs the counter design |

### Confirmation

| Check | Where defined |
|---|---|
| `LinkAllocatorTest` on a real database pre-inserts a colliding code and stubs the generator to prove the retry | FR-01, section 3.6 |
| `LinkAllocatorTest.aliasRaceMapsTo409` | FR-03 |
| `CodeGeneratorTest` asserts alphabet and length | T-003 |
| `LinkFlowIT.createsLinkAndReturns201` | FR-01 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| Random Base62, constraint, retry | Non-enumerable; no infrastructure; correct under races | Rare collisions; not decodable |
| Base62 of a counter, Redis `INCR` with blocks and regions | No collisions; shortest codes; decodable; scales writes across regions | Enumerable unless obfuscated, and obfuscation is reversible; needs Redis; pointless on one node |
| Hash of the URL | Deterministic; dedupes repeat URLs | Still collides and must be resolved; reveals that two users shortened the same URL unless salted |
| Snowflake ids | Uncoordinated; time-sortable | About 11 Base62 characters; leaks creation time and worker id; solves distribution, not shortness |

## More Information

System Design Primer pastebin and bit.ly chapter; ByteByteGo "Design a URL Shortener"; High Scalability on bitly; DesignGurus TinyURL lesson; Hello Interview Bitly breakdown; Georgiev and Shmatikov, "Gone in Six Characters", arXiv 1604.02734; PlanetScale slotted counters; Redis INCR and EXPIRE docs; RFC 3986.
