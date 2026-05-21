package connections

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.metadata.McpServerInfo
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders.Authorization
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport

private val GITHUB_TOOLS =
    listOf(
        "get_repository_tree",
        "get_file_contents",
        "search_code",
        "search_repositories",
        "list_commits",
    ).joinToString(",")

suspend fun createGithubMcpToolRegistry(): ToolRegistry {
    val githubToken = System.getenv("GITHUB_TOKEN") ?: error("GITHUB_TOKEN is not set")
    val url = "https://api.githubcopilot.com/mcp/"

    val authClient =
        HttpClient(CIO) {
            install(SSE)
            install(Logging) {
                level = LogLevel.INFO
            }
            defaultRequest {
                header(Authorization, "Bearer $githubToken")
                // to jest taka whitelista z toolami dla LLM, aby uzywac tylko te co chcemy i nie marnowac tokenow albo nie halucynowac
                header("X-MCP-Tools", GITHUB_TOOLS)
            }
        }

    val transport =
        StreamableHttpClientTransport(
            client = authClient,
            url = url,
        )

    return McpToolRegistryProvider.fromTransport(
        transport = transport,
        serverInfo = McpServerInfo(url),
        name = "github-copilot-mcp",
        version = "1.0.0",
    )
}
