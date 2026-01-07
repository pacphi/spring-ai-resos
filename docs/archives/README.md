# Archives

This directory contains documentation from **completed implementation phases** that serve as historical reference for architectural decisions and migration patterns.

## Contents

### Project Origin

See [mvp/](mvp/) for project origin documentation:

#### SPARK.md

**Purpose**: How this project started - the original inspiration and genesis

**Key Topics**: Project inception, initial goals, evolution

#### ROADMAP.md

**Purpose**: Original MVP roadmap (archived)

**Key Topics**: Initial project goals and milestones

---

### Spring Boot 4 / Spring AI 2 Upgrade

These documents chronicle the upgrade from Spring Boot 3.x to 4.x and Spring AI 1.x to 2.x.

See [upgrade-to-spring-boot-4-and-spring-ai-2/](upgrade-to-spring-boot-4-and-spring-ai-2/) for:

#### PHASE_0_LESSONS_LEARNED.md

**Status**: Complete | **Date**: January 2026

**Purpose**: WebFlux to WebMVC migration experience

**Key Topics**: MCP security compatibility, artifact names, code patterns, pitfalls

**Referenced By**: [architecture/11-migration-patterns.md](../architecture/11-migration-patterns.md), [adr/004](../architecture/adr/004-webmvc-over-webflux.md)

#### SECURITY_IMPLEMENTATION_PLAN.md

**Status**: Complete | **Date**: January 2026

**Purpose**: OAuth2 security implementation plan

**Key Topics**: Three-tier OAuth2, WebMVC + HTTP Streamable, security configuration

**Referenced By**: [architecture/06-security-architecture.md](../architecture/06-security-architecture.md)

#### REMAINING_TASKS.md

**Status**: Complete (Phases 0-5) | **Date**: January 2026

**Purpose**: Original implementation task breakdown

**Key Topics**: Phase-by-phase implementation plan (all completed)

- Phase 0: WebFlux → WebMVC
- Phase 1: Database Schema
- Phase 2: Backend Security
- Phase 3: MCP-Server Security
- Phase 4: MCP-Client OAuth2
- Phase 5: React SPA

**Value**: Historical record of implementation approach

#### UPDATED_REMAINING_TASKS.md

**Status**: Complete (Phases 1-7) | **Date**: January 6, 2026

**Purpose**: Refined task breakdown with completion status

**Key Topics**: Detailed implementation tasks with verification steps (all completed)

**Value**: Shows evolution of task planning during implementation

#### PHASE_6_TEST_PLAN.md

**Status**: Complete | **Date**: January 2026

**Purpose**: Integration testing plan for OAuth2 security

**Key Topics**: Test architecture, test pyramid, module-specific test plans

**Value**: Reference for testing approach and coverage strategy

#### PHASE6_REMAINING_TEST_SCOPE.md

**Status**: Complete (53/53 tests - 100%) | **Date**: January 6, 2026

**Purpose**: Test implementation progress and scope

**Key Topics**: Test coverage by module, completion status

**Value**: Final test completion status (see [TESTS.md](../TESTS.md) for final results)

---

## When to Add Documents Here

Place documents in this directory when they:

- Document completed implementation phases
- Contain lessons learned from migrations
- Serve as historical reference for decisions
- Are still valuable for understanding "why we did it this way"
- May help future teams with similar migrations

---

## Related Documentation

- [docs/architecture/11-migration-patterns.md](../architecture/11-migration-patterns.md) - Synthesizes lessons from archives
- [docs/architecture/adr/](../architecture/adr/) - Architectural decision records
- [mvp/SPARK.md](mvp/SPARK.md) - Project origin story
- [mvp/ROADMAP.md](mvp/ROADMAP.md) - Original MVP roadmap
- [docs/planning/](../planning/) - Future work
