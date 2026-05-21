package strategy

import kotlinx.serialization.Serializable

/**
 * State propagated between subgraphs in the refinement workflow.
 *
 * Each variant represents a discrete phase:
 *   Initial → IssueFetched → RepoIdentified → CodeInspected → RefinementProposed
 *
 * Selective propagation: each variant carries forward only the fields the
 * downstream phases actually need. cloudId from Jira fetch dies in fetchJira;
 * assignee dies after fetch; findings only live from CodeInspected onwards.
 *
 * Each variant jest `@Serializable` żeby Koog mógł go zwrócić bezpośrednio
 * z `nodeLLMRequestStructured<strategy.RefinementState.X>()` (single source of truth,
 * brak osobnego DTO + mappera).
 */
@Serializable
sealed interface RefinementState {
    /** Input do pierwszego subgrafu — raw user message przed pobraniem ticketa. */
    @Serializable
    data class Initial(
        val rawInput: String,
    ) : RefinementState

    /**
     * Output fetchJira. cloudId zostaje w fetchJira (niepotrzebne dalej).
     *
     * Pola: dokładnie te, które LLM ma wypełnić ze ścisłego summary
     * w finalize call'u — patrz FetchJiraSubgraph.FETCH_JIRA_SYSTEM_PROMPT.
     */
    @Serializable
    data class IssueFetched(
        val key: String, // np. "TRT-746"
        val summary: String,
        val description: String,
        val comments: List<String>,
        val assignee: String?, // displayName, nullable (unassigned)
    ) : RefinementState

    /**
     * Output findRepo.
     * TODO faza 2: dopracować/skorygować pola gdy projektujemy findRepo subgraph.
     * Wstępne założenie: pola z IssueFetched które potrzebne dalej + repo coords.
     */
    @Serializable
    data class RepoIdentified(
        val key: String,
        val summary: String,
        val description: String,
        val comments: List<String>,
        val repoOwner: String, // np. "Ala-com"
        val repoName: String, // np. "backend-spaces"
    ) : RefinementState

    /**
     * Output inspectCode.
     * TODO faza 3: dopracować pola gdy projektujemy inspectCode subgraph.
     */
    @Serializable
    data class CodeInspected(
        val key: String,
        val summary: String,
        val description: String,
        val comments: List<String>,
        val repoOwner: String,
        val repoName: String,
        val findings: List<CodeFinding>,
    ) : RefinementState

    /**
     * Output proposeRefinement (terminal state).
     * TODO faza 4: dopracować pola gdy projektujemy proposeRefinement subgraph.
     */
    @Serializable
    data class RefinementProposed(
        val key: String,
        val originalDescription: String,
        val proposedDescription: String, // Markdown w formacie TRT-637-style
    ) : RefinementState
}

/**
 * Punkt mapy kodu zbudowanej w inspectCode.
 * TODO faza 3: zweryfikować że ten kształt wystarcza dla proposeRefinement.
 */
@Serializable
data class CodeFinding(
    val path: String, // np. "spaces-service/src/.../KafkaTenantEventListener.kt"
    val summary: String, // 1–2 zdania: co robi ten plik w kontekście ticketa
)
