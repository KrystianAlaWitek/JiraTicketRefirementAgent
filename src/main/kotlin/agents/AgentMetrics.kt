package agents

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory collector for one agent session.
 *
 * - LLM call counters + token totals (input/output/total) accumulated from
 *   `onLLMCallCompleted` events. Token counts come from
 *   `Message.Response.metaInfo.totalTokensCount` / `inputTokensCount` /
 *   `outputTokensCount` (filled by OpenAI; left as null by some providers).
 * - Tool call counts (success and failure) grouped by source. The source is
 *   inferred from the tool name by [classifySource] — Koog's event context does
 *   not carry the originating MCP transport.
 *
 * Counts only top-level main agent activity. The `fetchJira` sub-agent shows up
 * as a single `fetchJira × N` line under `local-subagent`; whatever the
 * sub-agent does internally (its own LLM calls + MCP tool calls) is NOT
 * included here. To open that black box, the same `EventHandler` block would
 * need to be installed in `FetchJiraAgentTool` — left as a TODO for later.
 */
class AgentMetrics {
    private val toolCalls = ConcurrentHashMap<String, AtomicInteger>()
    private val toolFails = ConcurrentHashMap<String, AtomicInteger>()
    private val llmCalls = AtomicInteger(0)
    private val inputTokens = AtomicInteger(0)
    private val outputTokens = AtomicInteger(0)
    private val totalTokens = AtomicInteger(0)

    fun recordToolCall(name: String) {
        toolCalls.computeIfAbsent(name) { AtomicInteger(0) }.incrementAndGet()
    }

    fun recordToolFail(name: String) {
        toolFails.computeIfAbsent(name) { AtomicInteger(0) }.incrementAndGet()
    }

    fun recordLLMCall(
        input: Int?,
        output: Int?,
        total: Int?,
    ) {
        llmCalls.incrementAndGet()
        input?.let { inputTokens.addAndGet(it) }
        output?.let { outputTokens.addAndGet(it) }
        total?.let { totalTokens.addAndGet(it) }
    }

    fun printSummary() {
        val bar = "━".repeat(60)
        println()
        println(bar)
        println("Agent metrics")
        println(bar)
        println(
            "LLM calls: ${llmCalls.get()}   " +
                "tokens — input=${inputTokens.get()}  output=${outputTokens.get()}  total=${totalTokens.get()}",
        )
        val totalToolCalls = toolCalls.values.sumOf { it.get() }
        val totalFails = toolFails.values.sumOf { it.get() }
        println("Tool calls: $totalToolCalls   (failed: $totalFails)")

        if (toolCalls.isEmpty()) {
            println("  (no tool calls recorded)")
        } else {
            val grouped =
                toolCalls.entries
                    .groupBy({ classifySource(it.key) }, { it.key to it.value.get() })
                    .toSortedMap()
            for ((source, entries) in grouped) {
                val sum = entries.sumOf { it.second }
                val perTool =
                    entries
                        .sortedByDescending { it.second }
                        .joinToString(", ") { "${it.first}×${it.second}" }
                println("  ${source.padEnd(16)} × $sum   ($perTool)")
            }
        }

        if (totalFails > 0) {
            println("Failed tools:")
            toolFails.forEach { (name, count) ->
                println("  $name × ${count.get()}")
            }
        }
        println(bar)
    }

    companion object {
        /**
         * Maps a tool name to a coarse "source" bucket. Statyczne mapowanie po
         * nazwie — Koog's `ToolCallCompletedContext` nie pamięta z którego
         * transportu MCP tool przyszedł.
         */
        fun classifySource(toolName: String): String =
            when {
                toolName in LOCAL_KOOG_TOOLS -> "local-koog"
                toolName == "fetchJira" -> "local-subagent"
                toolName in ATLASSIAN_MCP_TOOLS -> "atlassian-mcp"
                toolName.startsWith("get_") ||
                    toolName.startsWith("search_") ||
                    toolName.startsWith("list_") -> "github-mcp"
                else -> "unknown"
            }

        private val LOCAL_KOOG_TOOLS = setOf("__ask_user__", "__exit__")
        private val ATLASSIAN_MCP_TOOLS =
            setOf(
                "getAccessibleAtlassianResources",
                "getJiraIssue",
                "searchJiraIssuesUsingJql",
            )
    }
}
