package agents

import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.AIAgentTool.AgentToolInput
import ai.koog.agents.core.agent.AIAgentTool.AgentToolResult
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.createAgentTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.PromptExecutor
import strategy.RefinementState
import strategy.buildFetchJiraStrategy
import tools.getAccessibleAtlassianResources
import tools.getJiraIssue
import tools.searchJiraIssuesUsingJql
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun createFetchJiraAgentTool(
    promptExecutor: PromptExecutor,
): Tool<AgentToolInput<RefinementState.Initial>, AgentToolResult<RefinementState.IssueFetched>> {
    val fetchJiraAgentToolRegistry =
        ToolRegistry {
            tool(AskUser)
            tool(::getAccessibleAtlassianResources)
            tool(::getJiraIssue)
            tool(::searchJiraIssuesUsingJql)
        }

    val fetchJiraAgentConfig =
        AIAgentConfig(
            prompt =
                prompt(id = "fetchJira") {
                    system(FETCH_JIRA_SYSTEM_PROMPT)
                },
            model = OpenAIModels.Chat.GPT4o,
            maxAgentIterations = 30,
        )

    val service =
        AIAgentService(
            promptExecutor = promptExecutor,
            agentConfig = fetchJiraAgentConfig,
            strategy = buildFetchJiraStrategy(),
            toolRegistry = fetchJiraAgentToolRegistry,
        )

    return service.createAgentTool(
        agentName = "fetchJira",
        agentDescription =
            "Fetches a single Jira issue based on the user's raw input. " +
                "Accepts either an explicit issue key (e.g. 'TRT-746') or a natural-language description. " +
                "Returns a structured object: key, summary, description, comments, assignee. " +
                "Use this BEFORE any GitHub investigation when the user asks about a Jira ticket.",
    )
}

private val FETCH_JIRA_SYSTEM_PROMPT =
    """
    You are a Jira fetcher. Your only job: retrieve ONE Jira issue based on
    the user's input.

    ## Tools (call in the order that applies)
    1. getAccessibleAtlassianResources — call FIRST to obtain cloudId.
       Mandatory before any Jira read.
    2. getJiraIssue — call with cloudId + issue key when the user input
       contains an explicit key like "TRT-746".
    3. searchJiraIssuesUsingJql — call when the user describes the issue
       without giving a key (e.g. "the latest ticket in project TRT").
       Construct a narrow JQL from the user's hint.
    4. askUser — call ONLY when the input is too ambiguous to attempt
       steps 2 or 3 with confidence.

    ## Strict rules
    - NEVER fabricate an issue key. If unsure → search; if still unsure → ask.
    - Try searchJiraIssuesUsingJql BEFORE asking the user. The user expects
      you to attempt resolution first.
    - Once the issue is fetched, end the turn with a brief plain-text summary
      that includes: key, summary, status, description, comments
      (if any), assignee. This summary will be parsed by a downstream
      extractor — keep all those fields visible.
    - Do NOT propose refinements, actions, or next steps. Your scope is
      fetch only.
    """.trimIndent()
