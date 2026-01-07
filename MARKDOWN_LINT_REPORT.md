# Markdown Lint Report

Generated: 2026-01-07

This report organizes all markdownlint issues by failure code with direct links to locations.

---

## MD026 - No Trailing Punctuation in Headings

Headings should not end with punctuation characters like `:`.

| File                                                                                                                               | Line | Context      |
| ---------------------------------------------------------------------------------------------------------------------------------- | ---- | ------------ |
| [PHASE_6_TEST_PLAN.md:616](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE_6_TEST_PLAN.md#L616)                       | 616  | Trailing `:` |
| [PHASE_6_TEST_PLAN.md:622](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE_6_TEST_PLAN.md#L622)                       | 622  | Trailing `:` |
| [SECURITY_IMPLEMENTATION_PLAN.md:417](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L417) | 417  | Trailing `:` |
| [SECURITY_IMPLEMENTATION_PLAN.md:427](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L427) | 427  | Trailing `:` |

---

## MD028 - No Blank Lines Inside Blockquotes

Blank lines inside blockquotes break the quote into separate blocks.

| File                                                                    | Line |
| ----------------------------------------------------------------------- | ---- |
| [01-system-overview.md:27](docs/architecture/01-system-overview.md#L27) | 27   |
| [01-system-overview.md:29](docs/architecture/01-system-overview.md#L29) | 29   |

---

## MD029 - Ordered List Item Prefix

Ordered list items should use sequential numbering (1, 2, 3), not arbitrary numbers.

| File                                                                              | Line | Expected | Actual |
| --------------------------------------------------------------------------------- | ---- | -------- | ------ |
| [15-future-enhancements.md:735](docs/architecture/15-future-enhancements.md#L735) | 735  | 1        | 4      |
| [15-future-enhancements.md:740](docs/architecture/15-future-enhancements.md#L740) | 740  | 2        | 5      |
| [15-future-enhancements.md:745](docs/architecture/15-future-enhancements.md#L745) | 745  | 3        | 6      |
| [15-future-enhancements.md:752](docs/architecture/15-future-enhancements.md#L752) | 752  | 1        | 7      |
| [15-future-enhancements.md:757](docs/architecture/15-future-enhancements.md#L757) | 757  | 2        | 8      |
| [RELEASE.md:34](docs/RELEASE.md#L34)                                              | 34   | 1        | 2      |

---

## MD031 - Blank Lines Around Fenced Code Blocks

Fenced code blocks should be surrounded by blank lines.

| File                                                                                      | Line |
| ----------------------------------------------------------------------------------------- | ---- |
| [08-ai-integration.md:520](docs/architecture/08-ai-integration.md#L520)                   | 520  |
| [08-ai-integration.md:530](docs/architecture/08-ai-integration.md#L530)                   | 530  |
| [09-frontend-architecture.md:618](docs/architecture/09-frontend-architecture.md#L618)     | 618  |
| [09-frontend-architecture.md:640](docs/architecture/09-frontend-architecture.md#L640)     | 640  |
| [001-openapi-first.md:58](docs/architecture/adr/001-openapi-first.md#L58)                 | 58   |
| [002-spring-data-jdbc.md:169](docs/architecture/adr/002-spring-data-jdbc.md#L169)         | 169  |
| [SPARK.md:144](docs/archives/mvp/SPARK.md#L144)                                           | 144  |
| [PHASE_7_PRODUCTION_READINESS.md:44](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L44)   | 44   |
| [PHASE_7_PRODUCTION_READINESS.md:52](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L52)   | 52   |
| [PHASE_7_PRODUCTION_READINESS.md:58](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L58)   | 58   |
| [PHASE_7_PRODUCTION_READINESS.md:62](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L62)   | 62   |
| [PHASE_7_PRODUCTION_READINESS.md:64](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L64)   | 64   |
| [PHASE_7_PRODUCTION_READINESS.md:69](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L69)   | 69   |
| [PHASE_7_PRODUCTION_READINESS.md:71](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L71)   | 71   |
| [PHASE_7_PRODUCTION_READINESS.md:73](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L73)   | 73   |
| [PHASE_7_PRODUCTION_READINESS.md:75](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L75)   | 75   |
| [PHASE_7_PRODUCTION_READINESS.md:123](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L123) | 123  |
| [PHASE_7_PRODUCTION_READINESS.md:125](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L125) | 125  |
| [PHASE_7_PRODUCTION_READINESS.md:127](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L127) | 127  |
| [PHASE_7_PRODUCTION_READINESS.md:129](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L129) | 129  |

---

## MD033 - No Inline HTML

Inline HTML elements should be avoided (some elements allowed via config).

| File                                                                          | Line | Element              |
| ----------------------------------------------------------------------------- | ---- | -------------------- |
| [05-data-architecture.md:131](docs/architecture/05-data-architecture.md#L131) | 131  | `BookingTableEntity` |

---

## MD034 - No Bare URLs

URLs should be wrapped in angle brackets `<url>` or as proper markdown links `[text](url)`.

| File                                                                                  | Line | URL                                 |
| ------------------------------------------------------------------------------------- | ---- | ----------------------------------- |
| [01-system-overview.md:115](docs/architecture/01-system-overview.md#L115)             | 115  | `john@example.com`                  |
| [01-system-overview.md:313](docs/architecture/01-system-overview.md#L313)             | 313  | `http://localhost:8081`             |
| [01-system-overview.md:314](docs/architecture/01-system-overview.md#L314)             | 314  | `http://localhost:8080`             |
| [01-system-overview.md:315](docs/architecture/01-system-overview.md#L315)             | 315  | `http://localhost:8080/swagger-...` |
| [01-system-overview.md:316](docs/architecture/01-system-overview.md#L316)             | 316  | `http://localhost:8080/actuator`    |
| [05-data-architecture.md:1096](docs/architecture/05-data-architecture.md#L1096)       | 1096 | `http://localhost:8080/h2-conso...` |
| [06-security-architecture.md:832](docs/architecture/06-security-architecture.md#L832) | 832  | `http://localhost:8081`             |
| [07-mcp-architecture.md:220](docs/architecture/07-mcp-architecture.md#L220)           | 220  | `http://localhost:8082`             |
| [08-ai-integration.md:16](docs/architecture/08-ai-integration.md#L16)                 | 16   | `https://docs.spring.io/spring-...` |
| [09-frontend-architecture.md:769](docs/architecture/09-frontend-architecture.md#L769) | 769  | `http://localhost:5173`             |
| [09-frontend-architecture.md:785](docs/architecture/09-frontend-architecture.md#L785) | 785  | `http://localhost:8081`             |
| [11-migration-patterns.md:916](docs/architecture/11-migration-patterns.md#L916)       | 916  | `https://central.sonatype.com/`     |
| [11-migration-patterns.md:917](docs/architecture/11-migration-patterns.md#L917)       | 917  | `https://github.com/spring-proj...` |
| [11-migration-patterns.md:918](docs/architecture/11-migration-patterns.md#L918)       | 918  | `https://docs.spring.io/spring-...` |
| [13-deployment.md:76](docs/architecture/13-deployment.md#L76)                         | 76   | `http://localhost:8081`             |
| [13-deployment.md:77](docs/architecture/13-deployment.md#L77)                         | 77   | `http://localhost:8080`             |
| [13-deployment.md:78](docs/architecture/13-deployment.md#L78)                         | 78   | `http://localhost:8080/swagger-...` |
| [13-deployment.md:79](docs/architecture/13-deployment.md#L79)                         | 79   | `http://localhost:8080/h2-conso...` |
| [ROADMAP.md:24](docs/archives/mvp/ROADMAP.md#L24)                                     | 24   | `https://github.com/pacphi/matt...` |
| [README.md:273](README.md#L273)                                                       | 273  | `http://localhost:8081`             |

---

## MD040 - Fenced Code Language

Fenced code blocks should have a language specified (e.g., ` ```java ` not just ` ``` `).

| File                                                                                                                             | Line |
| -------------------------------------------------------------------------------------------------------------------------------- | ---- |
| [02-technology-stack.md:477](docs/architecture/02-technology-stack.md#L477)                                                      | 477  |
| [03-module-architecture.md:383](docs/architecture/03-module-architecture.md#L383)                                                | 383  |
| [03-module-architecture.md:552](docs/architecture/03-module-architecture.md#L552)                                                | 552  |
| [03-module-architecture.md:812](docs/architecture/03-module-architecture.md#L812)                                                | 812  |
| [03-module-architecture.md:828](docs/architecture/03-module-architecture.md#L828)                                                | 828  |
| [03-module-architecture.md:1138](docs/architecture/03-module-architecture.md#L1138)                                              | 1138 |
| [03-module-architecture.md:1147](docs/architecture/03-module-architecture.md#L1147)                                              | 1147 |
| [05-data-architecture.md:1065](docs/architecture/05-data-architecture.md#L1065)                                                  | 1065 |
| [07-mcp-architecture.md:623](docs/architecture/07-mcp-architecture.md#L623)                                                      | 623  |
| [07-mcp-architecture.md:819](docs/architecture/07-mcp-architecture.md#L819)                                                      | 819  |
| [07-mcp-architecture.md:836](docs/architecture/07-mcp-architecture.md#L836)                                                      | 836  |
| [07-mcp-architecture.md:937](docs/architecture/07-mcp-architecture.md#L937)                                                      | 937  |
| [07-mcp-architecture.md:959](docs/architecture/07-mcp-architecture.md#L959)                                                      | 959  |
| [08-ai-integration.md:230](docs/architecture/08-ai-integration.md#L230)                                                          | 230  |
| [08-ai-integration.md:409](docs/architecture/08-ai-integration.md#L409)                                                          | 409  |
| [08-ai-integration.md:427](docs/architecture/08-ai-integration.md#L427)                                                          | 427  |
| [08-ai-integration.md:791](docs/architecture/08-ai-integration.md#L791)                                                          | 791  |
| [08-ai-integration.md:811](docs/architecture/08-ai-integration.md#L811)                                                          | 811  |
| [08-ai-integration.md:878](docs/architecture/08-ai-integration.md#L878)                                                          | 878  |
| [08-ai-integration.md:912](docs/architecture/08-ai-integration.md#L912)                                                          | 912  |
| [08-ai-integration.md:1095](docs/architecture/08-ai-integration.md#L1095)                                                        | 1095 |
| [09-frontend-architecture.md:23](docs/architecture/09-frontend-architecture.md#L23)                                              | 23   |
| [09-frontend-architecture.md:341](docs/architecture/09-frontend-architecture.md#L341)                                            | 341  |
| [09-frontend-architecture.md:366](docs/architecture/09-frontend-architecture.md#L366)                                            | 366  |
| [09-frontend-architecture.md:519](docs/architecture/09-frontend-architecture.md#L519)                                            | 519  |
| [11-migration-patterns.md:836](docs/architecture/11-migration-patterns.md#L836)                                                  | 836  |
| [13-deployment.md:741](docs/architecture/13-deployment.md#L741)                                                                  | 741  |
| [14-testing.md:21](docs/architecture/14-testing.md#L21)                                                                          | 21   |
| [001-openapi-first.md:51](docs/architecture/adr/001-openapi-first.md#L51)                                                        | 51   |
| [005-http-streamable-transport.md:101](docs/architecture/adr/005-http-streamable-transport.md#L101)                              | 101  |
| [mcp-tool-invocation.md:333](docs/architecture/diagrams/mcp-tool-invocation.md#L333)                                             | 333  |
| [mcp-tool-invocation.md:350](docs/architecture/diagrams/mcp-tool-invocation.md#L350)                                             | 350  |
| [mcp-tool-invocation.md:428](docs/architecture/diagrams/mcp-tool-invocation.md#L428)                                             | 428  |
| [README.md:169](docs/architecture/README.md#L169)                                                                                | 169  |
| [PHASE_6_TEST_PLAN.md:24](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE_6_TEST_PLAN.md#L24)                       | 24   |
| [PHASE6_REMAINING_TEST_SCOPE.md:208](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE6_REMAINING_TEST_SCOPE.md#L208) | 208  |
| [PHASE6_REMAINING_TEST_SCOPE.md:349](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE6_REMAINING_TEST_SCOPE.md#L349) | 349  |
| [PHASE6_REMAINING_TEST_SCOPE.md:383](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE6_REMAINING_TEST_SCOPE.md#L383) | 383  |
| [PHASE6_REMAINING_TEST_SCOPE.md:415](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE6_REMAINING_TEST_SCOPE.md#L415) | 415  |
| [PHASE6_REMAINING_TEST_SCOPE.md:485](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE6_REMAINING_TEST_SCOPE.md#L485) | 485  |
| [SECURITY_IMPLEMENTATION_PLAN.md:66](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L66) | 66   |
| [UPDATED_REMAINING_TASKS.md:154](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/UPDATED_REMAINING_TASKS.md#L154)         | 154  |
| [README.md:89](docs/planning/README.md#L89)                                                                                      | 89   |
| [TESTS.md:57](docs/TESTS.md#L57)                                                                                                 | 57   |
| [TESTS.md:300](docs/TESTS.md#L300)                                                                                               | 300  |
| [TESTS.md:486](docs/TESTS.md#L486)                                                                                               | 486  |
| [TESTS.md:896](docs/TESTS.md#L896)                                                                                               | 896  |
| [TESTS.md:910](docs/TESTS.md#L910)                                                                                               | 910  |
| [TESTS.md:925](docs/TESTS.md#L925)                                                                                               | 925  |
| [TESTS.md:940](docs/TESTS.md#L940)                                                                                               | 940  |
| [TESTS.md:1007](docs/TESTS.md#L1007)                                                                                             | 1007 |

---

## MD044 - Proper Names Capitalization

Proper names should have correct capitalization (Spring, Maven, Java, GitHub, Docker, PostgreSQL).

### Spring (lowercase → Spring)

| File                                                                                                                             | Line                      |
| -------------------------------------------------------------------------------------------------------------------------------- | ------------------------- |
| [02-technology-stack.md:81](docs/architecture/02-technology-stack.md#L81)                                                        | 81                        |
| [02-technology-stack.md:82](docs/architecture/02-technology-stack.md#L82)                                                        | 82                        |
| [03-module-architecture.md:1159-1164](docs/architecture/03-module-architecture.md#L1159)                                         | 1159-1164 (6 occurrences) |
| [11-migration-patterns.md:18](docs/architecture/11-migration-patterns.md#L18)                                                    | 18 (2 occurrences)        |
| [11-migration-patterns.md:1192-1197](docs/architecture/11-migration-patterns.md#L1192)                                           | 1192-1197 (5 occurrences) |
| [004-webmvc-over-webflux.md:354](docs/architecture/adr/004-webmvc-over-webflux.md#L354)                                          | 354                       |
| [004-webmvc-over-webflux.md:357](docs/architecture/adr/004-webmvc-over-webflux.md#L357)                                          | 357 (2 occurrences)       |
| [005-http-streamable-transport.md:405](docs/architecture/adr/005-http-streamable-transport.md#L405)                              | 405                       |
| [README.md:29](docs/archives/README.md#L29)                                                                                      | 29 (2 occurrences)        |
| [PHASE_0_LESSONS_LEARNED.md:17](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE_0_LESSONS_LEARNED.md#L17)           | 17                        |
| [PHASE_0_LESSONS_LEARNED.md:308](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE_0_LESSONS_LEARNED.md#L308)         | 308                       |
| [SECURITY_IMPLEMENTATION_PLAN.md:30](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L30) | 30                        |
| [SECURITY_IMPLEMENTATION_PLAN.md:61](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L61) | 61                        |
| [PHASE_7_PRODUCTION_READINESS.md:346](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L346)                                        | 346 (2 occurrences)       |

### Maven (lowercase → Maven)

| File                                                                              | Line                  |
| --------------------------------------------------------------------------------- | --------------------- |
| [02-technology-stack.md:81](docs/architecture/02-technology-stack.md#L81)         | 81                    |
| [02-technology-stack.md:84-88](docs/architecture/02-technology-stack.md#L84)      | 84-88 (5 occurrences) |
| [03-module-architecture.md:929](docs/architecture/03-module-architecture.md#L929) | 929                   |
| [RELEASE.md:9](docs/RELEASE.md#L9)                                                | 9                     |
| [TESTS.md:880](docs/TESTS.md#L880)                                                | 880                   |

### Java (lowercase → Java)

| File                                                                                                                                   | Line                    |
| -------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- |
| [PHASE_6_TEST_PLAN.md:522](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE_6_TEST_PLAN.md#L522)                           | 522                     |
| [PHASE_6_TEST_PLAN.md:545](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/PHASE_6_TEST_PLAN.md#L545)                           | 545                     |
| [REMAINING_TASKS.md:13](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/REMAINING_TASKS.md#L13)                                 | 13                      |
| [SECURITY_IMPLEMENTATION_PLAN.md:518-533](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L518) | 518-533 (6 occurrences) |
| [TESTS.md:306](docs/TESTS.md#L306)                                                                                                     | 306                     |
| [TESTS.md:418](docs/TESTS.md#L418)                                                                                                     | 418                     |
| [TESTS.md:936](docs/TESTS.md#L936)                                                                                                     | 936                     |
| [TESTS.md:946](docs/TESTS.md#L946)                                                                                                     | 946                     |

### GitHub (Github → GitHub)

| File                          | Line |
| ----------------------------- | ---- |
| [README.md:3](README.md#L3)   | 3    |
| [README.md:46](README.md#L46) | 46   |
| [README.md:56](README.md#L56) | 56   |
| [README.md:70](README.md#L70) | 70   |

### Docker (lowercase → Docker)

| File                                                                                                     | Line               |
| -------------------------------------------------------------------------------------------------------- | ------------------ |
| [deployment-architecture.md:471](docs/architecture/diagrams/deployment-architecture.md#L471)             | 471                |
| [REMAINING_TASKS.md:248](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/REMAINING_TASKS.md#L248) | 248                |
| [PHASE_7_PRODUCTION_READINESS.md:87](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L87)                  | 87                 |
| [PHASE_7_PRODUCTION_READINESS.md:98](docs/planning/PHASE_7_PRODUCTION_READINESS.md#L98)                  | 98 (2 occurrences) |
| [TESTS.md:969](docs/TESTS.md#L969)                                                                       | 969                |

---

## MD049 - Emphasis Style

Use asterisks `*text*` for emphasis, not underscores `_text_`.

| File                                                                                                                               | Line                |
| ---------------------------------------------------------------------------------------------------------------------------------- | ------------------- |
| [002-spring-data-jdbc.md:375](docs/architecture/adr/002-spring-data-jdbc.md#L375)                                                  | 375 (2 occurrences) |
| [SECURITY_IMPLEMENTATION_PLAN.md:193](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L193) | 193 (2 occurrences) |
| [SECURITY_IMPLEMENTATION_PLAN.md:210](docs/archives/upgrade-to-spring-boot-4-and-spring-ai-2/SECURITY_IMPLEMENTATION_PLAN.md#L210) | 210 (2 occurrences) |

---

## MD058 - Blank Lines Around Tables

Tables should be surrounded by blank lines.

| File                                                                                           | Line |
| ---------------------------------------------------------------------------------------------- | ---- |
| [06-security-architecture.md:1081](docs/architecture/06-security-architecture.md#L1081)        | 1081 |
| [07-mcp-architecture.md:977](docs/architecture/07-mcp-architecture.md#L977)                    | 977  |
| [003-dynamic-liquibase.md:72](docs/architecture/adr/003-dynamic-liquibase.md#L72)              | 72   |
| [code-generation-pipeline.md:247](docs/architecture/diagrams/code-generation-pipeline.md#L247) | 247  |

---

## Summary Statistics

| Code  | Rule Name                 | Count   | Description                              |
| ----- | ------------------------- | ------- | ---------------------------------------- |
| MD026 | `no-trailing-punctuation` | 4       | Headings ending with punctuation         |
| MD028 | `no-blanks-blockquote`    | 2       | Blank lines inside blockquotes           |
| MD029 | `ol-prefix`               | 6       | Incorrect ordered list numbering         |
| MD031 | `blanks-around-fences`    | 20      | Missing blank lines around code blocks   |
| MD033 | `no-inline-html`          | 1       | Inline HTML elements                     |
| MD034 | `no-bare-urls`            | 20      | URLs not properly formatted              |
| MD040 | `fenced-code-language`    | 51      | Code blocks without language             |
| MD044 | `proper-names`            | 54      | Incorrect capitalization of proper names |
| MD049 | `emphasis-style`          | 6       | Underscore emphasis instead of asterisk  |
| MD058 | `blanks-around-tables`    | 4       | Missing blank lines around tables        |
|       | **TOTAL**                 | **173** |                                          |

### Files by Error Count

| Files with Most Issues                              | Count |
| --------------------------------------------------- | ----- |
| `docs/TESTS.md`                                     | 12    |
| `docs/architecture/08-ai-integration.md`            | 11    |
| `docs/archives/.../SECURITY_IMPLEMENTATION_PLAN.md` | 11    |
| `docs/planning/PHASE_7_PRODUCTION_READINESS.md`     | 10    |
| `docs/architecture/03-module-architecture.md`       | 9     |
| `docs/architecture/02-technology-stack.md`          | 9     |

---

_Run `pnpm run lint:md` to regenerate this data._
