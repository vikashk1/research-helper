# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Keep session interactive
# Don't put long response at once. Give user chance to ask followup questions.

See [README.md](README.md) for setup and [DESIGN.md](DESIGN.md) for architecture.

## Commands

```bash
mvn spring-boot:run        # requires ANTHROPIC_API_KEY in .env
mvn test                   # run all tests
mvn test -Dtest=ClassName  # run single test class
```

## Conventions

- Agents go in `agent/`, persistence in `job/`, SSE in `sse/`
- Each agent is a single Claude API call with its own system prompt — no agentic SDK
- Use `@Slf4j`; log at `debug` for flow, `info` for milestones, `error` for failures
- Frontend is no-build vanilla JS — no npm, no bundler; CDN-loaded libs only
- Model ID and retry delay are configurable in `application.properties`, not hardcoded
