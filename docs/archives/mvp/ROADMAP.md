# Developing a Spring AI Enhanced Restaurant Booking System Employing an API-first Approach

> **Note**: This document has been archived. For current and future enhancements, see [docs/architecture/15-future-enhancements.md](../../architecture/15-future-enhancements.md). For the historical record of decisions made during MVP development, see [ADR-000: Project Genesis](../../architecture/adr/000-project-genesis-mvp.md).

---

## Roadmap

### MVP

This is just a punch-list of activities:

- [x] Create basic Maven multi-module project structure
- [x] Seed documentation — README, SPARK, RELEASE, ROADMAP
- [x] Code-generate model objects and a Feign client based on OpenAPI derivative
  - [x] ask Claude to help generate from Postman docs
  - [x] iterated on that — validated the spec online until it rendered Swagger interface without errors
- [x] Build a Spring Data module that encapsulates the model objects from `client` module and provides away to bootstrap a backend using an in-memory database
  - [x] need to figure out how to adapt codegen so that it produces models enhanced with Jakarta Persistence annotations.
  - [x] seed with some test data for demo convenience
- [x] Implement an MCP server — it's just function callbacks delegating to the DefaultApiClient
  - [x] Define client configuration for use with Claude desktop.
- [x] Implement an MCP client
  - [x] Implement a ReactJS chatbot — borrow from: https://github.com/pacphi/mattermost-ai-service.
- [x] Author a series of articles about this project on LinkedIn
- [ ] Complete implementation for all repositories, controllers, seed data, and mappers

MVP is defined as being able to chat via Claude or the ReactJS chatbot.
