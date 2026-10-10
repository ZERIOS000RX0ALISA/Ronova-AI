package com.example

import com.example.data.model.Attachment
import com.example.data.model.EndpointProfile
import com.example.data.model.MessageBlock
import com.example.data.model.ProfileCategory
import com.example.data.model.ProviderType
import com.example.data.model.UnifiedChatRequest
import com.example.data.model.UnifiedMessage
import com.example.data.repository.ConversationRepository
import com.example.domain.adapter.AnthropicAdapter
import com.example.domain.adapter.GeminiAdapter
import com.example.domain.adapter.ModelAdapterFactory
import com.example.ui.renderer.MessageParser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RonovaStage2Test {

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
    fun testGeminiAdapter_listModelsSuccess() = runBlocking {
        val geminiModelsJson = """
            {
              "models": [
                {
                  "name": "models/gemini-2.5-flash",
                  "displayName": "Gemini 2.5 Flash",
                  "description": "Fast and versatile",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/gemini-2.5-pro",
                  "displayName": "Gemini 2.5 Pro",
                  "description": "Complex reasoning",
                  "supportedGenerationMethods": ["generateContent"]
                }
              ]
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(geminiModelsJson))

        val profile = EndpointProfile(
            name = "Google Gemini Test",
            category = ProfileCategory.FLASH,
            providerType = ProviderType.GEMINI,
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
            apiKey = "AIzaSyTestKey",
            selectedModel = "gemini-2.5-flash"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val result = adapter.listModels()

        assertTrue(result.isSuccess)
        val models = result.getOrNull()
        assertNotNull(models)
        assertEquals(2, models?.size)
        assertEquals("gemini-2.5-flash", models?.get(0)?.id)
    }

    @Test
    fun testGeminiAdapter_streamingSeparatesReasoningAndContent() = runBlocking {
        val sseData = """
            data: {"candidates":[{"content":{"parts":[{"text":"Thinking step 1","thought":true}]}}]}

            data: {"candidates":[{"content":{"parts":[{"text":"The capital of France is Paris."}]}}]}

            data: [DONE]

        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseData))

        val profile = EndpointProfile(
            name = "Gemini Thought Test",
            category = ProfileCategory.FLASH,
            providerType = ProviderType.GEMINI,
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
            apiKey = "AIzaSyTestKey",
            selectedModel = "gemini-2.5-flash"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val chunks = adapter.streamMessage(
            UnifiedChatRequest(
                messages = listOf(UnifiedMessage(role = "user", content = "Capital of France?")),
                model = "gemini-2.5-flash",
                thinkingEnabled = true
            )
        ).toList()

        // 1. Thought chunk
        val thoughtChunk = chunks.firstOrNull { it.reasoningContent != null }
        assertNotNull(thoughtChunk)
        assertEquals("Thinking step 1", thoughtChunk?.reasoningContent)
        assertEquals("", thoughtChunk?.text)
        assertFalse("Thought chunk must never contain 'null'", thoughtChunk?.reasoningContent?.contains("null") == true)

        // 2. Answer chunk
        val answerChunk = chunks.firstOrNull { it.text.isNotEmpty() }
        assertNotNull(answerChunk)
        assertEquals("The capital of France is Paris.", answerChunk?.text)
        assertEquals(null, answerChunk?.reasoningContent)
        assertFalse("Answer chunk must never start with 'null'", answerChunk?.text?.startsWith("null") == true)
    }

    @Test
    fun testAnthropicAdapter_streamingSeparatesReasoningAndContent() = runBlocking {
        val sseData = """
            data: {"type":"content_block_delta","delta":{"type":"thinking_delta","thinking":"Analyzing the question..."}}

            data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"The answer is 42."}}

            data: {"type":"message_stop"}

        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(sseData))

        val profile = EndpointProfile(
            name = "Claude Thought Test",
            category = ProfileCategory.SYSTEM,
            providerType = ProviderType.ANTHROPIC,
            baseUrl = mockWebServer.url("/v1").toString().removeSuffix("/"),
            apiKey = "sk-ant-test",
            selectedModel = "claude-3-7-sonnet-20250219"
        )

        val adapter = ModelAdapterFactory.create(profile)
        val chunks = adapter.streamMessage(
            UnifiedChatRequest(
                messages = listOf(UnifiedMessage(role = "user", content = "Ultimate question?")),
                model = "claude-3-7-sonnet-20250219",
                thinkingEnabled = true
            )
        ).toList()

        val thoughtChunk = chunks.firstOrNull { it.reasoningContent != null }
        assertNotNull(thoughtChunk)
        assertEquals("Analyzing the question...", thoughtChunk?.reasoningContent)
        assertEquals("", thoughtChunk?.text)

        val answerChunk = chunks.firstOrNull { it.text.isNotEmpty() }
        assertNotNull(answerChunk)
        assertEquals("The answer is 42.", answerChunk?.text)
        assertEquals(null, answerChunk?.reasoningContent)
    }

    @Test
    fun testMessageParser_codeBlockExtraction() {
        val raw = """
            Here is a Kotlin function:
            ```kotlin
            fun hello(): String {
                return "world"
            }
            ```
            That's all!
        """.trimIndent()

        val blocks = MessageParser.parse(raw)
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Markdown)
        assertTrue(blocks[1] is MessageBlock.Code)
        assertTrue(blocks[2] is MessageBlock.Markdown)

        val codeBlock = blocks[1] as MessageBlock.Code
        assertEquals("kotlin", codeBlock.language)
        assertTrue(codeBlock.content.contains("fun hello()"))
    }

    @Test
    fun testMessageParser_tableExtraction() {
        val raw = """
            | Model | Provider | Category |
            |---|---|---|
            | Gemini 2.5 | Google | Flash |
            | Claude 3.7 | Anthropic | System |
        """.trimIndent()

        val blocks = MessageParser.parse(raw)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Table)

        val table = blocks[0] as MessageBlock.Table
        assertEquals(listOf("Model", "Provider", "Category"), table.columns)
        assertEquals(2, table.rows.size)
        assertEquals("Gemini 2.5", table.rows[0][0])
    }

    @Test
    fun testMessageParser_streamingUnclosedCodeBlock() {
        // Unclosed code block while streaming tokens
        val raw = """
            ```python
            def compute(x):
                return x * 2
        """.trimIndent()

        val blocks = MessageParser.parse(raw)
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is MessageBlock.Code)
        val code = blocks[0] as MessageBlock.Code
        assertEquals("python", code.language)
        assertTrue(code.content.contains("def compute(x):"))
    }

    @Test
    fun testMessageParser_mathBlockExtraction() {
        val raw = """
            The quadratic equation solution is:
            $$
            x = \frac{-b \pm \sqrt{b^2 - 4ac}}{2a}
            $$
            where a != 0.
        """.trimIndent()

        val blocks = MessageParser.parse(raw)
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is MessageBlock.LatexBlock)
        val math = blocks[1] as MessageBlock.LatexBlock
        assertTrue(math.content.contains("sqrt"))
    }

    @Test
    fun testAttachmentSerialization_roundTrip() {
        val original = listOf(
            Attachment(
                name = "test.jpg",
                mimeType = "image/jpeg",
                sizeBytes = 1024,
                base64Data = "dGVzdA==",
                isImage = true
            ),
            Attachment(
                name = "code.py",
                mimeType = "text/plain",
                sizeBytes = 256,
                extractedText = "print('hello')",
                lineCount = 1,
                isImage = false
            )
        )

        val json = ConversationRepository.serializeAttachments(original)
        assertNotNull(json)

        val deserialized = ConversationRepository.deserializeAttachments(json)
        assertEquals(2, deserialized.size)
        assertEquals("test.jpg", deserialized[0].name)
        assertEquals("dGVzdA==", deserialized[0].base64Data)
        assertEquals("code.py", deserialized[1].name)
        assertEquals("print('hello')", deserialized[1].extractedText)
        assertEquals(1, deserialized[1].lineCount)
    }
}
