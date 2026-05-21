package connections

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.HttpHeaders.Authorization
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation

object JiraMcp {
    private const val TOKEN =
        "712020-9157ec26-6991-466d-913b-0f118cc435d4:Wb_ziTLYrLSJCCC1:pTf_ZPOJ431trZguC-s1U5H4w9D2zDN7"
    private const val URL = "https://mcp.atlassian.com/v1/mcp"

    private var _client: Client? = null
    val client: Client
        get() = _client ?: error("JiraMcp not initialized — call JiraMcp.init() first")

    suspend fun init() {
        if (_client != null) return
        val authClient =
            HttpClient(CIO) {
                install(SSE)
                install(Logging) { level = LogLevel.INFO }
                defaultRequest { header(Authorization, "Bearer $TOKEN") }
            }
        val transport = StreamableHttpClientTransport(client = authClient, url = URL)
        _client =
            Client(clientInfo = Implementation(name = "agentKoog", version = "1.0.0"))
                .apply { connect(transport) }
    }
}
