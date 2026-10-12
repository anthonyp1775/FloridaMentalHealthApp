# ADR 0005 — Pin Spring Boot 3.5.16 on JDK 25

## Status
Accepted

## Context

**Spring Boot.** Spring Boot 3.5 reached open-source end of life on
2026-06-30; 3.5.16 was the final OSS release on that branch. Spring Boot
4.1 is current.

Against upgrading: course materials, lab examples and instructor support
all target 3.x, and Spring Boot 4 changed enough of the Spring Security
configuration API that following along would cost days of the schedule —
days that would come out of building the thing being graded.

Worth noting what was *not* a reason: the project was initially pinned to
3.5.0, which was simply out of date. Moving to 3.5.16 picked up every
patch released on the branch while it was supported.

**The JDK.** JDK 27 released days before this build. Neither Spring Boot
3.5.16 nor the Lombok version it manages supports it — Lombok fails
during annotation processing with
`ExceptionInInitializerError: com.sun.tools.javac.tree.EndPosTable`,
which presents as hundreds of "cannot find symbol" errors in code that
has not changed.

## Decision

**Spring Boot 3.5.16 on JDK 25 (LTS)** for this project.

Two build-configuration choices follow directly, and both are in the
pom with comments explaining why:

- **Lombok is declared explicitly as an annotation processor** via
  `annotationProcessorPaths`. Having it on the compile classpath used to
  be enough; JDK 21 deprecated implicit discovery and **JDK 23 switched
  it off**. On JDK 25, javac ignores processors it only finds on the
  classpath, so every `@Getter` silently generates nothing. IntelliJ
  kept working because it passes the processor path itself, which is why
  this surfaced only on the first Maven run.
- **JaCoCo is pinned to 0.8.14**, the first release with official Java
  25 support. JaCoCo reads compiled bytecode, so it has to understand
  the class file format the JDK emits — Java 25 writes major version 69,
  and 0.8.12 fails on it during instrumentation and again during report
  generation, *after* every test has already passed.

## Consequences

**Accepted costs**

- No further open-source security patches on the 3.5 branch. Anything
  production-bound would target 4.1.x. This is a course project with a
  fixed end date, no deployment, and no real user data — and the
  decision is documented rather than quietly inherited.
- Pinned to JDK 25 until Lombok and Spring Boot both support a newer
  release.

**Gained**

- The stack matches the course materials, so lab examples and instructor
  help apply directly.
- 3.5.16 rather than 3.5.0 means every patch the branch received.

**The wider pattern, which is the real lesson**

Three separate build failures in this project had the same shape:
Lombok, then stale half-compiled classes left by the failed build, then
JaCoCo. None was a defect in the application code. Each was a tool that
had to keep pace with a very new JDK, and each surfaced only when the
build ran somewhere it had not run before — a different IDE path, a
different runner.

Choosing an LTS JDK and a version-matched toolchain is not conservatism
for its own sake. On a fixed schedule, the cost of being early is paid
in hours spent on build configuration rather than on the system.
