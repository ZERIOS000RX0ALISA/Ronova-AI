package com.example.domain.adapter

import com.example.data.model.ConnectionStatus
import com.example.data.model.EndpointProfile
import com.example.data.model.ModelInfo
import com.example.data.model.StreamChunk
import com.example.data.model.UnifiedChatRequest
import com.example.data.model.UnifiedChatResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AnthropicAdapter(
    override val endpointProfile: EndpointProfile,
    private val client: OkHttpClient = defaultClient
) : ModelAdapter {

    override val id: String = "anthropic"

    private val activeCall = AtomicReference<Call?>(null)

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private const val ANTHROPIC_VERSION = "2023-06-01"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    override suspend fun listModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl.ifEmpty { "https://api.anthropic.com/v1" })
        val apiKey = endpointProfile.apiKey?.trim().orEmpty()
        val url = if (baseUrl.endsWith("/v1")) "$baseUrl/models" else "$baseUrl/v1/models"

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Accept", "application/json")
            .get()
            .build()

        val call = client.newCall(request)
        activeCall.set(call)

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    // If /models endpoint is not available or returned an error, fallback to known Claude catalog
                    if (resp.code == 404) {
                        return@withContext Result.success(getClaudeCatalog())
                    }
                    val errorMsg = mapHttpError(resp.code, baseUrl, "/models", resp.body?.string())
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val bodyString = resp.body?.string().orEmpty()
                val list = mutableListOf<ModelInfo>()

                try {
                    val root = JSONObject(bodyString)
                    val data = root.optJSONArray("data") ?: JSONArray()
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val modelId = item.optString("id", "")
                        val displayName = item.optString("display_name", modelId)
                        if (modelId.isNotEmpty()) {
                            val isThinking = modelId.contains("3-7") || modelId.contains("thinking")
                            list.add(
                                ModelInfo(
                                    id = modelId,
                                    name = displayName,
                                    ownedBy = "anthropic",
                                    supportsVision = true,
                                    supportsThinking = isThinking,
                                    supportsTools = true
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    list.addAll(getClaudeCatalog())
                }

                if (list.isEmpty()) {
                    list.addAll(getClaudeCatalog())
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(mapException(e, baseUrl))
        } finally {
            activeCall.set(null)
        }
    }

    private fun getClaudeCatalog(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                id = "claude-3-7-sonnet-20250219",
                name = "Claude 3.7 Sonnet (Hybrid Reasoning)",
                description = "Frontier intelligence with explicit thinking blocks",
                ownedBy = "anthropic",
                supportsVision = true,
                supportsThinking = true,
                supportsTools = true
            ),
            ModelInfo(
                id = "claude-3-5-sonnet-20241022",
                name = "Claude 3.5 Sonnet",
                description = "Industry standard for coding and complex multimodal tasks",
                ownedBy = "anthropic",
                supportsVision = true,
                supportsThinking = false,
                supportsTools = true
            ),
            ModelInfo(
                id = "claude-3-5-haiku-20241022",
                name = "Claude 3.5 Haiku",
                description = "High-speed lightweight model",
                ownedBy = "anthropic",
                supportsVision = true,
                supportsThinking = false,
                supportsTools = true
            ),
            ModelInfo(
                id = "claude-3-opus-20240229",
                name = "Claude 3 Opus",
                description = "Deep nuanced reasoning",
                ownedBy = "anthropic",
                supportsVision = true,
                supportsThinking = false,
                supportsTools = true
            )
        )
    }

    override suspend fun testConnection(): Result<ConnectionStatus> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val listResult = listModels()
        val latency = System.currentTimeMillis() - startTime

        listResult.fold(
            onSuccess = { models ->
                Result.success(
                    ConnectionStatus(
                        isSuccessful = true,
                        latencyMs = latency,
                        message = "Connected to Anthropic API in ${latency}ms. Discovered ${models.size} models.",
                        modelCount = models.size,
                        models = models
                    )
                )
            },
            onFailure = { error ->
                Result.failure(error)
            }
        )
    }

    override suspend fun sendMessage(request: UnifiedChatRequest): Result<UnifiedChatResponse> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl.ifEmpty { "https://api.anthropic.com/v1" })
        val apiKey = endpointProfile.apiKey?.trim().orEmpty()
        val url = if (baseUrl.endsWith("/v1")) "$baseUrl/messages" else "$baseUrl/v1/messages"

        val payload = buildAnthropicPayload(request, stream = false)
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)
        activeCall.set(call)

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorMsg = mapHttpError(resp.code, baseUrl, "/messages", resp.body?.string())
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val bodyString = resp.body?.string().orEmpty()
                val json = JSONObject(bodyString)
                val contentArray = json.optJSONArray("content")

                val reasoningBuilder = StringBuilder()
                val answerBuilder = StringBuilder()

                if (contentArray != null) {
                    for (i in 0 until contentArray.length()) {
                        val block = contentArray.getJSONObject(i)
                        val blockType = block.optString("type")
                        if (blockType == "thinking") {
                            val thinkingText = block.optString("thinking", "")
                            if (thinkingText.isNotEmpty() && thinkingText != "null") {
                                reasoningBuilder.append(thinkingText)
                            }
                        } else if (blockType == "text") {
                            val text = block.optString("text", "")
                            if (text.isNotEmpty() && text != "null") {
                                answerBuilder.append(text)
                            }
                        }
                    }
                }

                val finishReason = json.optString("stop_reason", null)
                val finalReasoning = reasoningBuilder.toString().takeIf { it.isNotEmpty() }
                val finalContent = answerBuilder.toString()

                Result.success(
                    UnifiedChatResponse(
                        id = json.optString("id", System.currentTimeMillis().toString()),
                        text = finalContent,
                        reasoningContent = finalReasoning,
                        finishReason = finishReason
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(mapException(e, baseUrl))
        } finally {
            activeCall.set(null)
        }
    }

    override fun streamMessage(request: UnifiedChatRequest): Flow<StreamChunk> = flow {
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl.ifEmpty { "https://api.anthropic.com/v1" })
        val apiKey = endpointProfile.apiKey?.trim().orEmpty()
        val url = if (baseUrl.endsWith("/v1")) "$baseUrl/messages" else "$baseUrl/v1/messages"

        val payload = buildAnthropicPayload(request, stream = true)
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)
        activeCall.set(call)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorMsg = mapHttpError(response.code, baseUrl, "/messages", response.body?.string())
                response.close()
                throw IOException(errorMsg)
            }

            val respBody = response.body ?: throw IOException("Empty response stream from Anthropic.")
            val reader = BufferedReader(InputStreamReader(respBody.byteStream(), Charsets.UTF_8))

            reader.use { bufferedReader ->
                var line: String?
                var streamFinished = false

                while (bufferedReader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty() || currentLine.startsWith(":")) continue

                    if (currentLine.startsWith("data:")) {
                        val dataPayload = currentLine.removePrefix("data:").trim()
                        if (dataPayload == "[DONE]") {
                            streamFinished = true
                            emit(StreamChunk(text = "", isFinished = true, finishReason = "end_turn"))
                            break
                        }

                        try {
                            val eventObj = JSONObject(dataPayload)
                            val type = eventObj.optString("type")

                            when (type) {
                                "content_block_delta" -> {
                                    val delta = eventObj.optJSONObject("delta")
                                    val deltaType = delta?.optString("type")

                                    if (deltaType == "thinking_delta") {
                                        val thinking = delta.optString("thinking", "")
                                        if (thinking.isNotEmpty() && thinking != "null") {
                                            emit(
                                                StreamChunk(
                                                    text = "",
                                                    reasoningContent = thinking,
                                                    isFinished = false,
                                                    rawData = dataPayload
                                                )
                                            )
                                        }
                                    } else if (deltaType == "text_delta") {
                                        val text = delta.optString("text", "")
                                        if (text.isNotEmpty() && text != "null") {
                                            emit(
                                                StreamChunk(
                                                    text = text,
                                                    reasoningContent = null,
                                                    isFinished = false,
                                                    rawData = dataPayload
                                                )
                                            )
                                        }
                                    }
                                }

                                "message_delta" -> {
                                    val delta = eventObj.optJSONObject("delta")
                                    val stopReason = delta?.optString("stop_reason", null)
                                    if (stopReason != null && stopReason != "null") {
                                        streamFinished = true
                                        emit(StreamChunk(text = "", isFinished = true, finishReason = stopReason))
                                    }
                                }

                                "message_stop" -> {
                                    streamFinished = true
                                    emit(StreamChunk(text = "", isFinished = true, finishReason = "end_turn"))
                                }
                            }
                        } catch (_: JSONException) {
                            // Skip delta parsing error
                        }
                    }
                }

                if (!streamFinished) {
                    emit(StreamChunk(text = "", isFinished = true, finishReason = "end_turn"))
                }
            }
        } catch (e: Exception) {
            throw mapException(e, baseUrl)
        } finally {
            activeCall.set(null)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildAnthropicPayload(request: UnifiedChatRequest, stream: Boolean): JSONObject {
        val root = JSONObject()
        root.put("model", request.model.ifEmpty { endpointProfile.selectedModel })
        root.put("max_tokens", request.maxTokens ?: 4096)
        root.put("stream", stream)

        val messagesArray = JSONArray()

        for (msg in request.messages) {
            if (msg.role == "system") {
                root.put("system", msg.content)
                continue
            }

            val msgObj = JSONObject()
            msgObj.put("role", if (msg.role == "assistant") "assistant" else "user")

            val contentArray = JSONArray()

            // 1. Text part (strictly answer content)
            if (msg.content.isNotEmpty()) {
                val textBlock = JSONObject()
                textBlock.put("type", "text")
                textBlock.put("text", msg.content)
                contentArray.put(textBlock)
            }

            // 2. Attachments
            for (att in msg.attachments) {
                if (att.isImage && !att.base64Data.isNullOrEmpty()) {
                    val imageBlock = JSONObject()
                    imageBlock.put("type", "image")
                    val sourceObj = JSONObject()
                    sourceObj.put("type", "base64")
                    sourceObj.put("media_type", att.mimeType)
                    sourceObj.put("data", att.base64Data)
                    imageBlock.put("source", sourceObj)
                    contentArray.put(imageBlock)
                } else if (!att.extractedText.isNullOrEmpty()) {
                    val textBlock = JSONObject()
                    textBlock.put("type", "text")
                    textBlock.put("text", "[Attachment: ${att.name}]\n${att.extractedText}")
                    contentArray.put(textBlock)
                }
            }

            if (contentArray.length() > 0) {
                msgObj.put("content", contentArray)
                messagesArray.put(msgObj)
            }
        }

        root.put("messages", messagesArray)

        // Thinking config
        if (request.thinkingEnabled && supportsThinking()) {
            val budget = request.thinkingBudget ?: endpointProfile.thinkingBudget ?: 2048
            val thinkingObj = JSONObject()
            thinkingObj.put("type", "enabled")
            thinkingObj.put("budget_tokens", budget)
            root.put("thinking", thinkingObj)
        }

        return root
    }

    override fun supportsVision(): Boolean = true
    override fun supportsTools(): Boolean = false
    override fun supportsThinking(): Boolean = endpointProfile.capabilities.thinking
    override fun supportsStreaming(): Boolean = true

    override fun cancel() {
        activeCall.get()?.cancel()
        activeCall.set(null)
    }

    private fun mapHttpError(code: Int, baseUrl: String, path: String, responseBody: String?): String {
        val detail = try {
            if (!responseBody.isNullOrBlank()) {
                val json = JSONObject(responseBody)
                val errorObj = json.optJSONObject("error")
                errorObj?.optString("message") ?: responseBody.take(150)
            } else ""
        } catch (_: Exception) {
            responseBody?.take(150) ?: ""
        }

        return when (code) {
            400 -> "Bad Request (400): $detail"
            401 -> "Anthropic Authentication Failed (401): Invalid x-api-key. $detail"
            403 -> "Forbidden (403): Your Anthropic account lacks permission for this model. $detail"
            404 -> "Not found (404) at $baseUrl$path. $detail"
            429 -> "Anthropic Rate Limit Exceeded (429): $detail"
            529, 500, 503 -> "Anthropic servers overloaded ($code). Please try again shortly."
            else -> "Anthropic error ($code): $detail"
        }
    }

    private fun mapException(e: Exception, baseUrl: String): Exception {
        return when (e) {
            is UnknownHostException -> IOException("DNS failure: Unable to reach Anthropic endpoint '$baseUrl'. Check internet connection.", e)
            is ConnectException -> IOException("Connection refused while connecting to Anthropic ($baseUrl).", e)
            is SocketTimeoutException -> IOException("Connection timed out waiting for Anthropic response.", e)
            is IOException -> e
            else -> IOException("Anthropic error: ${e.localizedMessage ?: "Unknown failure"}", e)
        }
    }
}
