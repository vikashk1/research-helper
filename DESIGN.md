# Multi-Agent Research Pipeline — Design Document

## Stack
- Java, Spring Boot, Maven
- Anthropic Java SDK (`com.anthropic:anthropic-java`)
- H2 (embedded database, swappable to PostgreSQL later)
- Spring Data JPA

## Architecture

### Agents
- **CoordinatorAgent** — receives a research topic, orchestrates the pipeline sequentially
- **WebSearchAgent** — Claude API call with built-in web search tool enabled
- **SummarizerAgent** — Claude API call to summarize search results
- **ReportFormatterAgent** — Claude API call to format the final report

Each agent is a separate Claude API call with a specialized system prompt (Option 1: no agentic SDK, full control in Java).

### Pipeline Flow
```
User submits topic
    → ClarificationAgent generates 2-3 clarifying questions (Claude decides questions dynamically)
    → Frontend displays questions, user answers them
    → CoordinatorAgent creates a Job (PENDING) with topic + answers
    → WebSearchAgent searches the web (IN_PROGRESS)
    → SummarizerAgent summarizes results
    → ReportFormatterAgent formats final report as Markdown
    → Job marked COMPLETED (or FAILED on unrecoverable error)
```

### Agents (updated)
- **ClarificationAgent** — Claude generates relevant clarifying questions based on the topic
- **CoordinatorAgent** — orchestrates the pipeline with topic + clarification answers
- **WebSearchAgent** — Claude API call with built-in web search tool enabled
- **SummarizerAgent** — Claude API call to summarize search results
- **ReportFormatterAgent** — Claude API call to produce a well-structured Markdown report; structure varies dynamically based on topic, audience, and content (not hardcoded)

## Job Management
- Each research request creates a job with a unique ID
- Job states: `PENDING → IN_PROGRESS → COMPLETED / FAILED`
- Logs appended progressively as pipeline runs
- Jobs and logs persisted in H2 via Spring Data JPA
- User can leave and return — state is always available

## Frontend
- Simple web page: user enters a topic, submits
- SSE (Server-Sent Events) for live log streaming
- User sees real-time progress without waiting on the same page
- Can check back anytime via job ID or job list

## Retry / Fallback
- 3 retry attempts per subagent
- Exponential backoff between retries: 1s, 2s, 4s
- If all retries exhausted, the entire job fails with a clear error message
- No partial results — all-or-nothing pipeline

## Project Structure
```
research-helper/
├── src/main/java/com/epam/research/
│   ├── agent/
│   │   ├── ClarificationAgent.java
│   │   ├── CoordinatorAgent.java
│   │   ├── WebSearchAgent.java
│   │   ├── SummarizerAgent.java
│   │   └── ReportFormatterAgent.java
│   ├── job/
│   │   ├── Job.java              (JPA entity)
│   │   ├── JobLog.java           (JPA entity)
│   │   ├── JobRepository.java
│   │   ├── JobService.java
│   │   └── JobController.java
│   └── sse/
│       └── SseService.java
├── src/main/resources/
│   ├── application.properties
│   └── static/index.html
└── pom.xml
```

## Future Considerations
- Swap H2 for PostgreSQL for production
- Add real authentication if needed
- Parallelize subagents where pipeline allows
