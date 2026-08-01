package com.yukisoffd.lyracode.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ApiProfileTest {
    @Test
    fun legacyStyleProfileEnablesAllSavedModels() {
        val profile = profile(savedModels = listOf("gpt-a", "gpt-b"))

        assertEquals(listOf("gpt-a", "gpt-b"), profile.enabledModels)
        assertFalse(profile.useResponsesApi)
    }

    @Test
    fun refreshingDetectedModelsPreservesEnabledSelection() {
        val profile = profile(
            savedModels = listOf("gpt-a", "gpt-b"),
            enabledModels = listOf("gpt-b"),
        )

        val refreshed = profile.copy(savedModels = listOf("gpt-a", "gpt-b", "gpt-c"))

        assertEquals(listOf("gpt-b"), refreshed.enabledModels)
    }

    @Test
    fun responsesEndpointUsesProviderBaseUrl() {
        val profile = profile(savedModels = listOf("gpt-a"), useResponsesApi = true)

        assertEquals("https://api.example.com/v1/responses", profile.responsesEndpoint)
    }

    private fun profile(
        savedModels: List<String>,
        enabledModels: List<String> = savedModels,
        useResponsesApi: Boolean = false,
    ) = ApiProfile(
        id = "test",
        name = "Test",
        apiKey = "key",
        baseUrl = "https://api.example.com/v1/",
        selectedModel = savedModels.firstOrNull().orEmpty(),
        savedModels = savedModels,
        enabledModels = enabledModels,
        useResponsesApi = useResponsesApi,
    )
}
