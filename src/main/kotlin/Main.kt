import agents.AgentMetrics
import agents.createBasicAgent
import agents.createFetchJiraAgentTool
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.prompt.executor.llms.all.simpleOpenAIExecutor
import connections.JiraMcp
import connections.createGithubMcpToolRegistry
import kotlinx.coroutines.runBlocking

fun main() {
    val openApiKey = System.getenv("OPEN_API_KEY") ?: error("OPEN_API_KEY is not set")
    runBlocking {
        JiraMcp.init()

        val promptExecutor = simpleOpenAIExecutor(openApiKey)
        val fetchJiraTool = createFetchJiraAgentTool(promptExecutor)

        val toolRegistry =
            ToolRegistry {
                tool(AskUser)
                tool(ExitTool)
                tool(fetchJiraTool)
                // TODO bedzie trzeba wyselekcjonowac z gita tylko te potrzebne
            }.plus(createGithubMcpToolRegistry())

        val metrics = AgentMetrics()
        val mainAgent = createBasicAgent(toolRegistry, promptExecutor, metrics)

        println("What ticket would you like to refine?")
        val initialMessage = readlnOrNull()?.trim() ?: error("No ticket provided")
        try {
            val result = mainAgent.run(initialMessage)
            println(result)
        } finally {
            metrics.printSummary()
        }
    }
}