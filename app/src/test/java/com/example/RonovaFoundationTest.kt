package com.example

import com.example.data.model.EndpointProfile
import com.example.data.model.ProfileCategory
import com.example.data.model.UnifiedChatRequest
import com.example.data.model.UnifiedMessage
import com.example.domain.adapter.ModelAdapterFactory
import com.example.domain.adapter.OpenAICompatibleAdapter
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RonovaFoundationTest {

    private lateinit var mockWebServer: MockWebServer

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testOpenAICompatibleAdapter_listModelsSuccess() = runBlocking {
        val json = """
            {
              "object": "list",
              "data": [
                {"id": "qwen2.5-7b", "object": "model", "owned_by": "ollama"},
                {"id": "llama3.2-3b", "object": "model", "owned_by": "ollama"}
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(json))

        val profile = EndpointProfile(
            name = "Local Test",
            category = ProfileCategory.ASTRA,
            baseUrl = mockWebServer.url("/v1").toString(),
            selectedModel = "qwen2.5-7b"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val result = adapter.listModels()

        assertTrue(result.isSuccess)
        val models = result.getOrNull()
        assertNotNull(models)
        assertEquals(2, models?.size)
        assertEquals("qwen2.5-7b", models?.get(0)?.id)
    }

    @Test
    fun testOpenAICompatibleAdapter_connectionTestFailure_unauthorized() = runBlocking {
        mockWebServer.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"Invalid API key"}"""))

        val profile = EndpointProfile(
            name = "Cloud Test",
            category = ProfileCategory.FLASH,
            baseUrl = mockWebServer.url("/v1").toString(),
            apiKey = "sk-badkey",
            selectedModel = "gpt-4o"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val testResult = adapter.testConnection()

        assertTrue(testResult.isFailure)
        val errorMsg = testResult.exceptionOrNull()?.message ?: ""
        assertTrue(errorMsg.contains("Unauthorized (401)"))
    }

    @Test
    fun testOpenAICompatibleAdapter_sendMessage() = runBlocking {
        val chatResponseJson = """
            {
              "id": "chatcmpl-123",
              "object": "chat.completion",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Hello from Ronova AI!"
                  },
                  "finish_reason": "stop"
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(chatResponseJson))

        val profile = EndpointProfile(
            name = "Local Test",
            category = ProfileCategory.ASTRA,
            baseUrl = mockWebServer.url("/v1").toString(),
            selectedModel = "qwen2.5-7b"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val response = adapter.sendMessage(
            UnifiedChatRequest(
                messages = listOf(UnifiedMessage(role = "user", content = "Hi")),
                model = "qwen2.5-7b"
            )
        )

        assertTrue(response.isSuccess)
        assertEquals("Hello from Ronova AI!", response.getOrNull()?.text)
    }

    @Test
    fun testProfileMaskingInToString() {
        val profile = EndpointProfile(
            name = "Secret Profile",
            category = ProfileCategory.SYSTEM,
            baseUrl = "https://api.openai.com/v1",
            apiKey = "sk-secret-token-12345",
            selectedModel = "gpt-4"
        )

        val str = profile.toString()
        assertFalse(str.contains("sk-secret-token-12345"))
        assertTrue(str.contains("masked"))
    }

    @Test
    fun testOpenAICompatibleAdapter_streamingSeparatesReasoningAndContent() = runBlocking {
        val sseStream = """
            data: {"choices":[{"delta":{"reasoning_content":"Step 1: Calculate sum"}}]}

            data: {"choices":[{"delta":{"content":"42"}}]}

            data: [DONE]

        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseStream))

        val profile = EndpointProfile(
            name = "Local Test",
            category = ProfileCategory.ASTRA,
            baseUrl = mockWebServer.url("/v1").toString(),
            selectedModel = "deepseek-r1"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val chunks = mutableListOf<com.example.data.model.StreamChunk>()

        adapter.streamMessage(
            UnifiedChatRequest(
                messages = listOf(UnifiedMessage(role = "user", content = "What is the answer?")),
                model = "deepseek-r1"
            )
        ).collect { chunk ->
            chunks.add(chunk)
        }

        // Chunk 1 has reasoning, but empty answer text (not "null")
        val reasoningChunk = chunks.firstOrNull { it.reasoningContent != null }
        assertNotNull(reasoningChunk)
        assertEquals("Step 1: Calculate sum", reasoningChunk?.reasoningContent)
        assertEquals("", reasoningChunk?.text)
        assertFalse(reasoningChunk?.text?.contains("null") == true)

        // Chunk 2 has answer text, but null reasoning (not string "null")
        val contentChunk = chunks.firstOrNull { it.text.isNotEmpty() }
        assertNotNull(contentChunk)
        assertEquals("42", contentChunk?.text)
        assertEquals(null, contentChunk?.reasoningContent)
        assertFalse(contentChunk?.text?.contains("null") == true)
    }

    @Test
    fun testOpenAICompatibleAdapter_plainStreamNeverPrefixesNull() = runBlocking {
        val sseStream = """
            data: {"choices":[{"delta":{"content":"Hello world!"}}]}

            data: [DONE]

        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseStream))

        val profile = EndpointProfile(
            name = "Plain Test",
            category = ProfileCategory.FLASH,
            baseUrl = mockWebServer.url("/v1").toString(),
            selectedModel = "gpt-4o"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val chunks = mutableListOf<com.example.data.model.StreamChunk>()

        adapter.streamMessage(
            UnifiedChatRequest(
                messages = listOf(UnifiedMessage(role = "user", content = "Hello")),
                model = "gpt-4o"
            )
        ).collect { chunk ->
            chunks.add(chunk)
        }

        val firstChunk = chunks.first { it.text.isNotEmpty() }
        assertEquals("Hello world!", firstChunk.text)
        assertFalse("Content must never contain 'null'", firstChunk.text.startsWith("null"))
        assertEquals(null, firstChunk.reasoningContent)
    }

    @Test
    fun testMessageModel_reasoningSeparation() {
        val message = com.example.data.model.Message(
            conversationId = "conv-1",
            role = "assistant",
            content = "This is a clean answer",
            reasoningContent = null
        )

        assertEquals("This is a clean answer", message.content)
        assertFalse("Message content must never start with 'null'", message.content.startsWith("null"))
        assertEquals(null, message.reasoningContent)
    }
}
