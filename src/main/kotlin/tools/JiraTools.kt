package tools

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import connections.JiraMcp
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

private val JIRA_FIELDS =
    listOf(
        "summary",
        "status",
        "description",
        "priority",
        "issuetype",
        "assignee",
        "reporter",
        "labels",
        "comment",
        "created",
        "updated",
    )

@Tool
@LLMDescription(
    "Lists Atlassian Cloud sites (Jira/Confluence) the authenticated user has access to. Returns each site's cloudId, name, and url.",
)
suspend fun getAccessibleAtlassianResources(): String {
    val result =
        JiraMcp.client.callTool(
            name = "getAccessibleAtlassianResources",
            arguments = buildJsonObject {},
        )
    return getToolCallResultText(result)
}

@Tool
@LLMDescription(
    "Get details of a single Jira issue. Returns only key fields (summary, status, description, priority, issuetype, assignee, reporter, labels, comments, created, updated) to keep the response compact.",
)
suspend fun getJiraIssue(
    @LLMDescription("Atlassian cloud id obtained from getAccessibleAtlassianResources")
    cloudId: String,
    @LLMDescription("Issue key (e.g. TRT-586) or numeric id")
    issueIdOrKey: String,
): String {
    val result =
        JiraMcp.client.callTool(
            name = "getJiraIssue",
            arguments =
                buildJsonObject {
                    put("cloudId", JsonPrimitive(cloudId))
                    put("issueIdOrKey", JsonPrimitive(issueIdOrKey))
                    put("fields", jiraFieldsArg())
                },
        )
    return getToolCallResultText(result)
}

@Tool
@LLMDescription("Searches Jira issues using JQL. Returns only key fields per issue to keep the response compact.")
suspend fun searchJiraIssuesUsingJql(
    @LLMDescription("Atlassian cloud id obtained from getAccessibleAtlassianResources")
    cloudId: String,
    @LLMDescription("JQL query, e.g. \"assignee = currentUser() AND status = 'In Progress'\"")
    jql: String,
    @LLMDescription("Maximum number of issues to return. Default 25.")
    maxResults: Int = 25,
): String {
    val result =
        JiraMcp.client.callTool(
            name = "searchJiraIssuesUsingJql",
            arguments =
                buildJsonObject {
                    put("cloudId", JsonPrimitive(cloudId))
                    put("jql", JsonPrimitive(jql))
                    put("maxResults", JsonPrimitive(maxResults))
                    put("fields", jiraFieldsArg())
                },
        )
    return getToolCallResultText(result)
}

private fun getToolCallResultText(result: CallToolResult): String = (result.content.firstOrNull() as TextContent).text

private fun jiraFieldsArg(): JsonArray = JsonArray(JIRA_FIELDS.map { JsonPrimitive(it) })
