# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project purpose

Sandbox / experimental playground for the **Koog Agents** library (`ai.koog:koog-agents`) — a Kotlin framework for building AI agents. This project is NOT part of the production ALA microservices ecosystem (the org-wide guidance in `~/IdeaProjects/CLAUDE.md` does not apply here — this is a stand-alone playground: no Spring Boot, no Alice Framework, no Artifactory dependencies).

The application is a console-based **Jira ticket refinement assistant**: it fetches a ticket via the Atlassian MCP, identifies the related repository on GitHub (via the GitHub Copilot MCP), reads the code, and proposes an enriched ticket description.

## Stack

- **Kotlin** 2.2.0 on JVM toolchain 21, plus the `kotlinx.serialization` 2.2.0 plugin
- **Gradle** (Kotlin DSL, single-module), `application` plugin → entrypoint `MainKt` (`src/main/kotlin/Main.kt`)
- **`ai.koog:koog-agents:0.8.0`** — agent framework
- **Ktor CIO** (`io.ktor:ktor-client-cio:3.2.2`) — HTTP/SSE transport for MCP (Streamable HTTP)
- **ktlint** (`org.jlleitschuh.gradle.ktlint` 14.0.1)
- **Artifact repository**: `mavenCentral()` only — `ai.koog:koog-agents` is publicly available, no Artifactory credentials needed
- **Tests**: `kotlin("test")` with JUnit Platform (`useJUnitPlatform()`)

## Commands

```bash
# Format
./gradlew ktlintFormat

# Build (always with clean — user convention)
./gradlew clean build

# Run the app — prompts interactively for a ticket (issue key or natural-language description)
./gradlew run

# Tests
./gradlew test

# Single test class (once tests exist)
./gradlew test --tests "FullyQualifiedTestClassName"
```

## Required env vars and files

Without these the app **crashes at startup** (each one is a hard requirement enforced with `error(...)`):

- `OPEN_API_KEY` — OpenAI key (`Main.kt`; models in `MainAgentConfig.kt` = `GPT5_4`, in `FetchJiraAgentTool.kt` = `GPT4o`)
- `GITHUB_TOKEN` — Bearer for `https://api.githubcopilot.com/mcp/` (`connections/GithubMcp.kt`)
- `JIRA_TOKEN` — OAuth Bearer for `https://mcp.atlassian.com/v1/mcp` (`connections/JiraMcp.kt`), TTL ~8h
- File **`ala-organization-context.md`** must sit in the process cwd — `OrganizationContextProvider` loads it directly (`Path.of(FILE_NAME)`), no classpath fallback. The file content is injected into the system prompt (GitHub organization context, `*-service` → `backend-*` mapping).

A local skill `.claude/skills/refresh-jira-token/SKILL.md` regenerates `JIRA_TOKEN` (refresh_token grant against `cf.mcp.atlassian.com`, browser OAuth fallback via `mcp-remote`). The skill **prints the token** — the user sets the env var themselves (shell / IDE Run Configuration). Not committed.

## Agent architecture

Two-level Koog composition:

```
main agent (chatAgentStrategy)              ← agents/AgentFactory.kt + MainAgentConfig.kt
 ├─ AskUser (the only channel to the user)
 ├─ ExitTool (the only way to end the conversation — only after explicit user signal)
 ├─ fetchJira  ← sub-agent wrapped as a Tool via AIAgentService.createAgentTool
 │    └─ buildFetchJiraStrategy(): graph with nodeLLMRequest / nodeExecuteTool /
 │       nodeLLMSendToolResult → nodeLLMRequestStructured<RefinementState.IssueFetched>
 │       Tools: getAccessibleAtlassianResources, getJiraIssue, searchJiraIssuesUsingJql, AskUser
 └─ 5 tools from GitHub Copilot MCP (whitelist in GithubMcp.kt via X-MCP-Tools header):
       get_repository_tree, get_file_contents, search_code,
       search_repositories, list_commits
```

**Key patterns to preserve when editing:**

- **`fetchJira` as a Tool** — the sub-agent is built as `AIAgentService` + `createAgentTool(...)`. Input: `RefinementState.Initial(rawInput)`. Output: `RefinementState.IssueFetched` (structured, validated by `StructureFixingParser` with GPT-4o mini). All `RefinementState` variants are `@Serializable sealed interface` — this is the **single source of truth** for subgraph output contracts (no separate DTO + mapper).
- **State propagation across phases** — `RefinementState.{Initial → IssueFetched → RepoIdentified → CodeInspected → RefinementProposed}`. Each variant carries forward only the fields downstream phases need (selective propagation: `cloudId` dies after `fetchJira`, `assignee` dies after fetch, etc.). Phases 2–4 are TODO — when designing new subgraphs, refine the shape of those variants.
- **MCP whitelist** — `connections/GithubMcp.kt` sends an `X-MCP-Tools: <list>` header so the LLM sees only 5 read-only GitHub tools instead of the full Copilot MCP inventory (token savings + fewer hallucinations).
- **Tool description in `createFetchJiraAgentTool`** is the CONTRACT for the main agent's LLM — the description decides whether/when the main agent calls the sub-agent. When changing sub-agent behavior, update `agentDescription`.
- **Telemetry** — `AgentMetrics` + Koog `EventHandler` (`onLLMCallStarting/Completed`, `onToolCall*`). Tokens read from `LLMCallCompletedContext.responses[].metaInfo.{input,output,total}TokensCount`. `fetchMetrics` has a TODO — the summing heuristic may not handle every provider.
- **Main agent system prompt** (`MainAgentConfig.AGENT_SYSTEM_PROMPT`) is long and precise — it defines the workflow (fetch → identify repo → inspect code → propose), the output artifact spec (Markdown without `#` headers, fenced code blocks with language tags), the communication protocol (numbered options with parsing of "1, but ..." for extra constraints), and termination (ONLY after an explicit user signal). Modify consciously — none of it is filler.

## File layout

```
src/main/kotlin/
├── Main.kt                              # entrypoint: init MCP, build registry, run
├── agents/
│   ├── AgentFactory.kt                  # createBasicAgent + EventHandler / metrics
│   ├── MainAgentConfig.kt               # config + large assistant system prompt
│   ├── FetchJiraAgentTool.kt            # fetchJira sub-agent exposed as a Tool
│   ├── OrganizationContextProvider.kt   # loads ala-organization-context.md
│   └── AgentMetrics.kt                  # LLM-call + tool-call counters
├── connections/
│   ├── JiraMcp.kt                       # Atlassian MCP (env JIRA_TOKEN)
│   └── GithubMcp.kt                     # GitHub Copilot MCP + tool whitelist (env GITHUB_TOKEN)
├── strategy/
│   ├── RefinementState.kt               # sealed state: Initial → IssueFetched → ...
│   └── FetchJiraSubgraph.kt             # fetchJira sub-agent graph
└── tools/
    └── JiraTools.kt                     # wrappers over Jira MCP (cloudId, getIssue, JQL)
```

No `src/test/` yet — when adding tests, use the standard `src/test/kotlin/` layout.

## In-repo documentation

- **`README.md`** — human-facing entry point (stack, env vars, run, architecture). Keep it in sync with this CLAUDE.md when env vars / structure change.
- **`demo-cheatsheet.md`** — notes / cheatsheet for a presentation (working file).
- **`docs/blockschema.puml`, `docs/short.puml`** — PlantUML architecture diagrams.
- **`docs/koog/`** — external Koog reference material.
- **`ala-organization-context.md`** — runtime input for the agent (see above), NOT documentation.

## Koog API reference

**Local Koog 0.8.0 sources** are extracted into `.koog-api/sources/` (47 modules,
~600 `.kt` files). This is the **only** place to look up Koog API — do **NOT**
re-extract jars from `~/.gradle/caches/...` or unpack them into `/tmp`.

```bash
# Searching the API:
grep -rln "Structured" .koog-api/sources/
grep -rln "fun subgraph" .koog-api/sources/

# Reading a specific file:
cat .koog-api/sources/agents-core-jvm/commonMain/ai/koog/agents/core/dsl/builder/AIAgentSubgraphBuilder.kt
```

After bumping the Koog version in `build.gradle.kts` → run `.koog-api/update.sh <new-version>`
(the script drops the old `sources/` and extracts fresh jars from the gradle cache).

`.koog-api/` is not committed — it's local tooling, regenerable in seconds from the gradle cache.

## Notes for Development

- Current Koog version in the project: **0.8.0** (change it in `build.gradle.kts` + run `.koog-api/update.sh`).
- The Koog library defines a DSL for building agents, tools, prompts, graph-based strategies with subgraphs, and structured output.
- No deployment / CI/CD configuration — this is a playground.
- No integration with Alice Framework, Spring AI ALA, or any other services from `IdeaProjects/`.
- The MCP servers are **remote HTTP** (Atlassian + GitHub Copilot); there are no local MCP processes — nothing to start alongside the app.