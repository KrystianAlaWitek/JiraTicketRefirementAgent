package agents

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.feature.handler.llm.LLMCallCompletedContext
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.agent.chatAgentStrategy
import ai.koog.agents.features.eventHandler.feature.EventHandler
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.model.PromptExecutor

fun createBasicAgent(
    toolRegistry: ToolRegistry,
    promptExecutor: PromptExecutor,
    metrics: AgentMetrics,
): GraphAIAgent<String, String> {
    val agent =
        AIAgent(
            promptExecutor = promptExecutor,
            agentConfig = createAssistantAgentConfig(),
            toolRegistry = toolRegistry,
            strategy = chatAgentStrategy(),
        ) {
            handleEvents {
                install(EventHandler) {
                    onLLMCallStarting { _ ->
                        println("[LLM starting →]")
                    }
                    onLLMCallCompleted { ctx ->
                        println("[LLM completed ←]")
                        fetchMetrics(ctx, metrics)
                    }
                    onAgentExecutionFailed { ctx ->
                        println("[AGENT ✗] ${ctx.throwable.message}")
                    }
                    onToolCallStarting { ctx ->
                        if (ctx.toolName != "__ask_user__") {
                            println("[Tool call starting] ${ctx.toolName} with args ${ctx.toolArgs}")
                        }
                    }
                    onToolCallCompleted { ctx ->
                        if (ctx.toolName != "__ask_user__") {
                            println("[Tool call completed] ${ctx.toolName} with args ${ctx.toolArgs}")
                        }
                        metrics.recordToolCall(ctx.toolName)
                    }
                    onToolCallFailed { ctx ->
                        println("[Tool call failed] ${ctx.toolName} with args ${ctx.toolArgs}")
                        metrics.recordToolFail(ctx.toolName)
                    }
                }
            }
        }
    return agent
}

// TODO zrobic to lepiej
private fun fetchMetrics(
    ctx: LLMCallCompletedContext,
    metrics: AgentMetrics,
) {
    var input = 0
    var output = 0
    var total = 0
    for (msg in ctx.responses) {
        val mi = msg.metaInfo
        input += mi.inputTokensCount ?: 0
        output += mi.outputTokensCount ?: 0
        total += mi.totalTokensCount ?: 0
    }
    metrics.recordLLMCall(
        input = input.takeIf { it > 0 },
        output = output.takeIf { it > 0 },
        total = total.takeIf { it > 0 },
    )
}
