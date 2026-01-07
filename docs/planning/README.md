# Planning

This directory contains documentation for **future work, remaining tasks, and project roadmap**.

## Contents

### ROADMAP.md

**Status**: ⏳ Active

**Purpose**: High-level project roadmap and future work

**Key Topics**:

- MVP status (mostly complete)
- Remaining work: Complete all repository/controller implementations
- Future LinkedIn articles

**What's Complete**:

- ✅ Multi-module Maven structure
- ✅ OpenAPI code generation
- ✅ Spring Data JDBC with entity transformation
- ✅ MCP server with Claude Desktop integration
- ✅ MCP client with ReactJS chatbot
- ✅ OAuth2 security (Phases 0-6 complete)
- ✅ Integration tests (100% passing)

**What Remains**:

- ⏳ Complete all controller endpoints (many are stubs)
- ⏳ Implement full CRUD for bookings, orders, tables
- ⏳ Add business logic validation

**Audience**: Project managers, stakeholders, contributors

### PHASE_7_PRODUCTION_READINESS.md

**Status**: ⏳ Not Started

**Purpose**: Production hardening tasks

**Key Topics**:

- Security hardening (CSP, HSTS, session cookies)
- Docker deployment improvements
- Documentation updates
- Health check enhancements

**Estimated Effort**: 9-13 hours

**Audience**: DevOps engineers, security team, developers

---

## How to Use Planning Documents

### For Sprint Planning

1. Review **ROADMAP.md** for remaining MVP work
2. Reference [architecture/15-future-enhancements.md](../architecture/15-future-enhancements.md) for comprehensive roadmap
3. Break down remaining controller implementations into user stories
4. See [archives/](../archives/) for completed phase documentation

### For New Features

1. Add significant features to **ROADMAP.md**
2. Create detailed task breakdown in new planning document
3. Link to architecture docs for context
4. After completion: Document lessons learned in archives/

### Understanding What's Been Done

**All completed implementation phases are in [archives/](../archives/)**:

- Phase 0: WebFlux → WebMVC migration
- Phase 1-5: Database, security, OAuth2 implementation
- Phase 6: Integration testing

**For implementation details, see**:

- [architecture/](../architecture/) - Current architecture documentation
- [archives/](../archives/) - How features were implemented

---

## Document Lifecycle

```text
New Feature Idea
    ↓
Add to planning/ (ROADMAP, new phase doc)
    ↓
Implementation (reference planning docs)
    ↓
Completion (update status in planning doc)
    ↓
Lessons Learned → Move to archives/
    ↓
Architecture Updated → Update architecture/
```

---

## Related Documentation

- [docs/architecture/15-future-enhancements.md](../architecture/15-future-enhancements.md) - Synthesized roadmap with priorities
- [docs/archives/](../archives/) - Completed phase documentation
- [docs/architecture/](../architecture/) - Current architecture
