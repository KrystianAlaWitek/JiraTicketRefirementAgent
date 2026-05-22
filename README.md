# agentKoogTest

Sandbox / playground for experimenting with the [Koog Agents](https://github.com/JetBrains/koog)
library (`ai.koog:koog-agents`).
The project builds a console-based assistant for **Jira ticket refinement**: it fetches
a ticket via the Atlassian MCP, identifies the matching GitHub repository, reads the code
through the GitHub MCP, and proposes an enriched ticket description.

Not part of the production ALA ecosystem — no Spring Boot, no Alice Framework, no
Artifactory. Maven pulls from `mavenCentral()` only.

## Stack

- Kotlin 2.2.0, JVM toolchain 21
- Gradle (Kotlin DSL), single-module, `application` plugin → entrypoint `MainKt`
- `ai.koog:koog-agents:0.8.0`
- MCP transport: Ktor CIO (`io.ktor:ktor-client-cio:3.2.2`) + Streamable HTTP
- ktlint (`org.jlleitschuh.gradle.ktlint:14.0.1`)

## Environment requirements

Environment variables required to run:

| Variable       | Purpose                                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------------------------|
| `OPEN_API_KEY` | OpenAI key (GPT-4o / GPT-5 — see `agents/MainAgentConfig.kt`, `agents/FetchJiraAgentTool.kt`)                        |
| `GITHUB_TOKEN` | Bearer token for `https://api.githubcopilot.com/mcp/` (GitHub Copilot MCP)                                           |
| `JIRA_TOKEN`   | OAuth Bearer for `https://mcp.atlassian.com/v1/mcp` (Atlassian MCP). TTL ~8h, needs periodic refresh.                |

The working directory must also contain `ala-organization-context.md`
(loaded by `OrganizationContextProvider` — describes the GitHub org, repo naming
convention, and `*-service` → `backend-*` mapping).

## Running

```bash
# Format + build
./gradlew ktlintFormat
./gradlew clean build

# Run
./gradlew run
# → "What ticket would you like to refine?"
# type e.g. "TRT-746" or "the latest ticket in project TRT"
```

Tests:

```bash
./gradlew test
```

(No test classes yet — `src/test/kotlin/` is empty.)

## Architecture

Two-level composition of Koog agents:

```
main agent (chatAgentStrategy)         ← agents/AgentFactory.kt + MainAgentConfig.kt
 ├─ tool: AskUser, ExitTool
 ├─ tool: fetchJira  ← sub-agent wrapped as a tool
 │    └─ subgraph: getAccessibleAtlassianResources → getJiraIssue / searchJiraIssuesUsingJql
 │       → nodeLLMRequestStructured<RefinementState.IssueFetched>
 └─ tools from GitHub Copilot MCP:
       get_repository_tree, get_file_contents, search_code,
       search_repositories, list_commits
```

- **Main agent** drives the conversation with the user (`AskUser` as the only
  user-facing channel), picks the repo, reads the code, and produces a refined
  description in a fixed Markdown format (see the prompt in
  `MainAgentConfig.AGENT_SYSTEM_PROMPT`).
- **`fetchJira` sub-agent** is an `AIAgentService` exposed as a tool
  (`createAgentTool`) — its own strategy graph with `nodeLLMRequestStructured`
  returning `RefinementState.IssueFetched` (sealed interface with refinement
  phases in `strategy/RefinementState.kt`).
- **MCP whitelist** for GitHub via the `X-MCP-Tools` header in `GithubMcp.kt` —
  restricts the tool surface visible to the LLM to 5 read-only operations.
- **Telemetry** in `AgentMetrics` (counts tool calls, input/output tokens)
  wired through Koog's `EventHandler` feature.

## Project structure

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
│   ├── JiraMcp.kt                       # Atlassian MCP (hardcoded Bearer)
│   └── GithubMcp.kt                     # GitHub Copilot MCP + tool whitelist
├── strategy/
│   ├── RefinementState.kt               # sealed state: Initial → IssueFetched → ...
│   └── FetchJiraSubgraph.kt             # fetchJira sub-agent graph
└── tools/
    └── JiraTools.kt                     # wrappers over Jira MCP (cloudId, getIssue, JQL)
```

## Koog API reference

Local Koog 0.8.0 sources are extracted to `.koog-api/sources/` (47 modules,
~600 `.kt` files) — use them to look up the API:

```bash
grep -rln "fun subgraph" .koog-api/sources/
cat .koog-api/sources/agents-core-jvm/commonMain/ai/koog/agents/core/dsl/builder/AIAgentSubgraphBuilder.kt
```

After bumping the Koog version in `build.gradle.kts`, run
`.koog-api/update.sh <new-version>`. The `.koog-api/` directory is not committed —
it regenerates in seconds from the gradle cache.