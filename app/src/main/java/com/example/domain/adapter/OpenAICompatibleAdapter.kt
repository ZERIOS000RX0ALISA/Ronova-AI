package com.example.domain.adapter

import com.example.data.model.Attachment
import com.example.data.model.ConnectionStatus
import com.example.data.model.EndpointProfile
import com.example.data.model.ModelInfo
import com.example.data.model.StreamChunk
import com.example.data.model.UnifiedChatRequest
import com.example.data.model.UnifiedChatResponse
import com.example.data.model.UnifiedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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

class OpenAICompatibleAdapter(
    override val endpointProfile: EndpointProfile,
    private val client: OkHttpClient = defaultClient
) : ModelAdapter {

    override val id: String = "openai-compatible"

    private val activeCall = AtomicReference<Call?>(null)

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private fun normalizeBaseUrl(url: String): String {
        val trimmed = url.trim().removeSuffix("/")
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "http://$trimmed"
        } else {
            trimmed
        }
    }

    private fun buildRequest(url: String, method: String = "GET", body: okhttp3.RequestBody? = null): Request {
        val builder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")

        // Include API key only if provided and non-empty (never logged)
        val apiKey = endpointProfile.apiKey?.trim()
        if (!apiKey.isNullOrEmpty()) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> body?.let { builder.post(it) } ?: builder.get()
            else -> builder.method(method, body)
        }
        return builder.build()
    }

    override suspend fun listModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl)
        if (baseUrl.toHttpUrlOrNull() == null) {
            return@withContext Result.failure(
                IllegalArgumentException("Malformed base URL: '$baseUrl'. Please verify the address format.")
            )
        }

        val modelsUrl = "$baseUrl/models"
        val request = buildRequest(modelsUrl)
        val call = client.newCall(request)
        activeCall.set(call)

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string()
                    val errorMsg = mapHttpError(resp.code, baseUrl, "/models", errorBody)
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val bodyString = resp.body?.string()
                if (bodyString.isNullOrBlank()) {
                    return@withContext Result.failure(
                        IOException("Empty response received from $modelsUrl")
                    )
                }

                val models = parseModelsList(bodyString)
                if (models.isEmpty()) {
                    return@withContext Result.failure(
                        IOException("Connected successfully, but the server returned 0 available models.")
                    )
                }
                Result.success(models)
            }
        } catch (e: Exception) {
            Result.failure(mapException(e, baseUrl))
        } finally {
            activeCall.set(null)
        }
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
                        message = "Connected successfully in ${latency}ms. Discovered ${models.size} model(s).",
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

    private fun buildMessagesArray(messages: List<UnifiedMessage>): JSONArray {
        val messagesArray = JSONArray()
        for (msg in messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)

            if (msg.attachments.isEmpty()) {
                msgObj.put("content", msg.content)
            } else {
                val contentArray = JSONArray()
                var fullText = msg.content
                for (att in msg.attachments) {
                    if (att.isImage && !att.base64Data.isNullOrEmpty()) {
                        val imgObj = JSONObject()
                        imgObj.put("type", "image_url")
                        val urlObj = JSONObject()
                        urlObj.put("url", "data:${att.mimeType};base64,${att.base64Data}")
                        imgObj.put("image_url", urlObj)
                        contentArray.put(imgObj)
                    } else if (!att.extractedText.isNullOrEmpty()) {
                        fullText += "\n\n[Attachment: ${att.name}]\n${att.extractedText}"
                    }
                }
                val textObj = JSONObject()
                textObj.put("type", "text")
                textObj.put("text", fullText)
                contentArray.put(textObj)
                msgObj.put("content", contentArray)
            }
            messagesArray.put(msgObj)
        }
        return messagesArray
    }

    override suspend fun sendMessage(request: UnifiedChatRequest): Result<UnifiedChatResponse> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl)
        val chatUrl = "$baseUrl/chat/completions"

        val payload = JSONObject().apply {
            put("model", request.model)
            put("messages", buildMessagesArray(request.messages))
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
            if (request.thinkingEnabled && supportsThinking()) {
                val budget = request.thinkingBudget ?: endpointProfile.thinkingBudget ?: 2048
                put("reasoning_effort", if (budget > 3000) "high" else "medium")
            }
            put("stream", false)
        }

        val httpRequestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = buildRequest(chatUrl, method = "POST", body = httpRequestBody)
        val call = client.newCall(httpRequest)
        activeCall.set(call)

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string()
                    val errorMsg = mapHttpError(resp.code, baseUrl, "/chat/completions", errorBody)
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val bodyString = resp.body?.string()
                    ?: return@withContext Result.failure(IOException("Empty response body received from chat endpoint."))

                val json = try {
                    JSONObject(bodyString)
                } catch (e: JSONException) {
                    return@withContext Result.failure(
                        IOException("Non-OpenAI-compatible response shape from chat endpoint: ${e.message}")
                    )
                }

                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext Result.failure(
                        IOException("Invalid response: 'choices' array missing or empty.")
                    )
                }

                val firstChoice = choices.getJSONObject(0)
                val messageObj = firstChoice.optJSONObject("message")

                // Extract reasoning_content safely (never stringify null)
                val rawReasoning = when {
                    messageObj != null && messageObj.has("reasoning_content") && !messageObj.isNull("reasoning_content") ->
                        messageObj.optString("reasoning_content")
                    messageObj != null && messageObj.has("reasoning") && !messageObj.isNull("reasoning") ->
                        messageObj.optString("reasoning")
                    messageObj != null && messageObj.has("thought") && !messageObj.isNull("thought") ->
                        messageObj.optString("thought")
                    else -> null
                }
                val reasoning = if (!rawReasoning.isNullOrBlank() && rawReasoning != "null") rawReasoning else null

                // Extract content safely (never include reasoning, never "null")
                val rawContent = if (messageObj != null && messageObj.has("content") && !messageObj.isNull("content")) {
                    messageObj.optString("content")
                } else null
                val content = if (!rawContent.isNullOrEmpty() && rawContent != "null") rawContent else ""

                val finishReason = firstChoice.optString("finish_reason", null)?.takeIf { it != "null" }
                val id = json.optString("id", System.currentTimeMillis().toString())

                Result.success(
                    UnifiedChatResponse(
                        id = id,
                        text = content,
                        reasoningContent = reasoning,
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
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl)
        val chatUrl = "$baseUrl/chat/completions"

        val payload = JSONObject().apply {
            put("model", request.model)
            put("messages", buildMessagesArray(request.messages))
            request.temperature?.let { put("temperature", it) }
            request.maxTokens?.let { put("max_tokens", it) }
            if (request.thinkingEnabled && supportsThinking()) {
                val budget = request.thinkingBudget ?: endpointProfile.thinkingBudget ?: 2048
                put("reasoning_effort", if (budget > 3000) "high" else "medium")
            }
            put("stream", true)
        }

        val httpRequestBody = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
        val httpRequest = buildRequest(chatUrl, method = "POST", body = httpRequestBody)
            .newBuilder()
            .header("Accept", "text/event-stream")
            .build()

        val call = client.newCall(httpRequest)
        activeCall.set(call)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                val errorMsg = mapHttpError(response.code, baseUrl, "/chat/completions", errorBody)
                response.close()
                throw IOException(errorMsg)
            }

            val body = response.body ?: throw IOException("Empty response stream.")
            val reader = BufferedReader(InputStreamReader(body.byteStream(), Charsets.UTF_8))

            reader.use { bufferedReader ->
                var line: String?
                var streamFinished = false

                while (bufferedReader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty() || currentLine.startsWith(":")) {
                        continue // SSE keepalive or comment
                    }

                    if (currentLine.startsWith("data:")) {
                        val dataPayload = currentLine.removePrefix("data:").trim()
                        if (dataPayload == "[DONE]") {
                            streamFinished = true
                            emit(StreamChunk(text = "", isFinished = true, finishReason = "stop"))
                            break
                        }

                        try {
                            val chunkJson = JSONObject(dataPayload)
                            val choices = chunkJson.optJSONArray("choices")
                            if (choices != null && choices.length() > 0) {
                                val choice = choices.getJSONObject(0)
                                val delta = choice.optJSONObject("delta")

                                // Parsing rule:
                                // 1. delta.reasoning_content present, non-null, non-empty
                                val rawReasoning = when {
                                    delta != null && delta.has("reasoning_content") && !delta.isNull("reasoning_content") ->
                                        delta.optString("reasoning_content")
                                    delta != null && delta.has("reasoning") && !delta.isNull("reasoning") ->
                                        delta.optString("reasoning")
                                    delta != null && delta.has("thought") && !delta.isNull("thought") ->
                                        delta.optString("thought")
                                    else -> null
                                }
                                val reasoning = if (!rawReasoning.isNullOrEmpty() && rawReasoning != "null") rawReasoning else null

                                // 2. delta.content present, non-null, non-empty
                                val rawContent = if (delta != null && delta.has("content") && !delta.isNull("content")) {
                                    delta.optString("content")
                                } else null
                                val content = if (!rawContent.isNullOrEmpty() && rawContent != "null") rawContent else ""

                                val finishReason = if (choice.has("finish_reason") && !choice.isNull("finish_reason")) {
                                    choice.optString("finish_reason").takeIf { it != "null" }
                                } else null

                                // 3. if a field is null, absent, or an empty string -> do not append / emit text
                                if (reasoning != null || content.isNotEmpty() || finishReason != null) {
                                    val isDone = finishReason != null
                                    emit(
                                        StreamChunk(
                                            text = content,
                                            reasoningContent = reasoning,
                                            isFinished = isDone,
                                            finishReason = finishReason,
                                            rawData = dataPayload
                                        )
                                    )
                                    if (isDone) {
                                        streamFinished = true
                                    }
                                }
                            }
                        } catch (e: JSONException) {
                            // Skip unparseable delta or non-standard chunk
                        }
                    }
                }

                if (!streamFinished) {
                    emit(StreamChunk(text = "", isFinished = true, finishReason = "completed"))
                }
            }
        } catch (e: Exception) {
            throw mapException(e, baseUrl)
        } finally {
            activeCall.set(null)
        }
    }.flowOn(Dispatchers.IO)

    override fun supportsVision(): Boolean = endpointProfile.capabilities.vision
    override fun supportsTools(): Boolean = endpointProfile.capabilities.tools
    override fun supportsThinking(): Boolean = endpointProfile.capabilities.thinking
    override fun supportsStreaming(): Boolean = true

    override fun cancel() {
        activeCall.get()?.cancel()
        activeCall.set(null)
    }

    private fun detectModelCapabilities(id: String): Triple<Boolean, Boolean, Boolean> {
        val lower = id.lowercase()
        val supportsVision = lower.contains("vision") || lower.contains("vl") || lower.contains("4o") || lower.contains("4-turbo") || lower.contains("llava") || lower.contains("minicpm")
        val supportsThinking = lower.contains("r1") || lower.contains("reason") || lower.contains("think") || lower.contains("o1") || lower.contains("o3") || lower.contains("qwq")
        val supportsTools = lower.contains("tool") || lower.contains("function") || lower.contains("gpt-") || lower.contains("qwen") || lower.contains("llama")
        return Triple(supportsVision, supportsThinking, supportsTools)
    }

    private fun parseModelsList(jsonString: String): List<ModelInfo> {
        val list = mutableListOf<ModelInfo>()
        try {
            val trimmed = jsonString.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val item = array.get(i)
                    if (item is JSONObject) {
                        val id = item.optString("id").ifEmpty { item.optString("name") }
                        if (id.isNotEmpty()) {
                            val (vision, thinking, tools) = detectModelCapabilities(id)
                            list.add(
                                ModelInfo(
                                    id = id,
                                    name = item.optString("name", id),
                                    ownedBy = item.optString("owned_by", null),
                                    supportsVision = vision,
                                    supportsThinking = thinking,
                                    supportsTools = tools
                                )
                            )
                        }
                    } else if (item is String && item.isNotEmpty()) {
                        val (vision, thinking, tools) = detectModelCapabilities(item)
                        list.add(
                            ModelInfo(
                                id = item,
                                name = item,
                                supportsVision = vision,
                                supportsThinking = thinking,
                                supportsTools = tools
                            )
                        )
                    }
                }
            } else {
                val root = JSONObject(trimmed)
                val dataArray = root.optJSONArray("data") ?: root.optJSONArray("models")
                if (dataArray != null) {
                    for (i in 0 until dataArray.length()) {
                        val item = dataArray.optJSONObject(i)
                        if (item != null) {
                            val id = item.optString("id").ifEmpty { item.optString("name") }
                            if (id.isNotEmpty()) {
                                val (vision, thinking, tools) = detectModelCapabilities(id)
                                list.add(
                                    ModelInfo(
                                        id = id,
                                        name = item.optString("name", id),
                                        ownedBy = item.optString("owned_by", null),
                                        supportsVision = vision,
                                        supportsThinking = thinking,
                                        supportsTools = tools
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Non-OpenAI-compatible format
        }
        return list
    }

    private fun mapHttpError(code: Int, baseUrl: String, path: String): String {
        return when (code) {
            401 -> "Unauthorized (401): Invalid or missing API key for this endpoint."
            403 -> "Forbidden (403): Access denied. Your API key does not have permission or account is restricted."
            404 -> "Endpoint not found (404): The path '$path' was not found at '$baseUrl'. Check if '/v1' is needed in the URL."
            429 -> "Rate limited (429): You have exceeded the rate limit or quota for this model provider."
            500, 502, 503, 504 -> "Server error ($code): The remote endpoint '$baseUrl' returned an internal error. Please try again later."
            else -> "HTTP request failed with status code $code ($baseUrl$path)."
        }
    }

    private fun mapException(e: Exception, baseUrl: String): Exception {
        return when (e) {
            is UnknownHostException -> IOException(
                "DNS / host resolution failure: Unable to resolve host '${e.message ?: baseUrl}'. Check URL spelling or network connection.",
                e
            )
            is ConnectException -> IOException(
                "Unreachable host: Connection refused at '$baseUrl'. Ensure the local server (Ollama, LM Studio, etc.) is running and listening on the given IP and port.",
                e
            )
            is SocketTimeoutException -> IOException(
                "Connection timeout: The endpoint at '$baseUrl' did not respond within the timeout limit (12s). Check if the IP is correct and accessible from this device.",
                e
            )
            is IOException -> e
            else -> IOException("Network error: ${e.localizedMessage ?: "Unknown connection failure"}", e)
        }
    }
}
