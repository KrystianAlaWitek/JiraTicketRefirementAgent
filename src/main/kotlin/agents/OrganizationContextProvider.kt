package agents

import java.nio.file.Files
import java.nio.file.Path

object OrganizationContextProvider {
    private const val FILE_NAME = "ala-organization-context.md"

    val content: String by lazy { load() }

    private fun load(): String {
        val path = Path.of(FILE_NAME)
        require(Files.exists(path)) {
            "Organization context file not found at ${path.toAbsolutePath()}. "
        }
        return Files.readString(path).trim()
    }
}
