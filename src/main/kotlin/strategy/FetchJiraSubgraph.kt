package strategy

import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.builder.subgraph
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMRequestStructured
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.reflect.asTool
import ai.koog.agents.ext.tool.AskUser
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.structure.StructuredResponse
import tools.getAccessibleAtlassianResources
import tools.getJiraIssue
import tools.searchJiraIssuesUsingJql

fun buildFetchJiraStrategy(): AIAgentGraphStrategy<RefinementState.Initial, RefinementState.IssueFetched> =
    strategy<RefinementState.Initial, RefinementState.IssueFetched>(name = "refinement") {
        val fetchJira by subgraph<RefinementState.Initial, RefinementState.IssueFetched>(
            name = "fetchJira",
            tools =
                listOf(
                    ::getAccessibleAtlassianResources.asTool(),
                    ::getJiraIssue.asTool(),
                    ::searchJiraIssuesUsingJql.asTool(),
                    AskUser,
                ),
        ) {
            val userMessage by node<RefinementState.Initial, String> { initial ->
                initial.rawInput
            }

            val callLLMWithTools by nodeLLMRequest("callLLM")
            val executeTool by nodeExecuteTool("executeTool")
            val sendToolResultToLlm by nodeLLMSendToolResult("sendToolResult")
            val finalStructuredResponse by nodeLLMRequestStructured<RefinementState.IssueFetched>(
                name = "extractStructuredResponse",
                examples = structuredResponseExamples(),
                fixingParser =
                    StructureFixingParser(
                        model = OpenAIModels.Chat.GPT4oMini,
                        retries = 2,
                    ),
            )

            val finalState
                by node<Result<StructuredResponse<RefinementState.IssueFetched>>, RefinementState.IssueFetched> { result ->
                    result.getOrThrow().data
                }

            edge(nodeStart forwardTo userMessage)
            edge(userMessage forwardTo callLLMWithTools)
            edge(callLLMWithTools forwardTo executeTool onToolCall { true })
            edge(callLLMWithTools forwardTo finalStructuredResponse onAssistantMessage { true })
            edge(executeTool forwardTo sendToolResultToLlm)
            edge(sendToolResultToLlm forwardTo executeTool onToolCall { true })
            edge(sendToolResultToLlm forwardTo finalStructuredResponse onAssistantMessage { true })
            edge(finalStructuredResponse forwardTo finalState)
            edge(finalState forwardTo nodeFinish)
        }

        nodeStart then fetchJira then nodeFinish
    }

private fun structuredResponseExamples(): List<RefinementState.IssueFetched> =
    listOf(
        RefinementState.IssueFetched(
            key = "TRT-637",
            summary = "Persist tool invocations for chat history",
            description =
                """
                Currently `tool_invocations` are stored in-memory only. After
                chat service restart we lose tool call traces. We need to
                persist them in a new `tool_invocations` table with columns:
                id (uuid), message_id (fk), tool_name, arguments (jsonb),
                result (jsonb), created_at (timestamp).
                """.trimIndent(),
            comments =
                listOf(
                    "@krystian Initial draft. Open question: do we keep failed tool calls or filter them out?",
                    "Filter them — they pollute UI history. We can log to OTEL instead.",
                    "OK, going with filter. Adding `success` flag column anyway for future use.",
                ),
            assignee = "Krystian Witek",
        ),
        RefinementState.IssueFetched(
            key = "TRT-902",
            summary = "Spike: evaluate MCP servers for code search",
            description = "Investigate which MCP servers offer indexed code search across multiple repos. Timebox: 2 days.",
            comments = emptyList(),
            assignee = null,
        ),
    )
