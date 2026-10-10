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

class GeminiAdapter(
    override val endpointProfile: EndpointProfile,
    private val client: OkHttpClient = defaultClient
) : ModelAdapter {

    override val id: String = "gemini"

    private val activeCall = AtomicReference<Call?>(null)

    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

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

    private fun getCleanModelId(model: String): String {
        val trimmed = model.trim()
        return if (trimmed.startsWith("models/")) trimmed.removePrefix("models/") else trimmed
    }

    override suspend fun listModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com" })
        val apiKey = endpointProfile.apiKey?.trim().orEmpty()
        val url = "$baseUrl/v1beta/models?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Accept", "application/json")
            .get()
            .build()

        val call = client.newCall(request)
        activeCall.set(call)

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorMsg = mapHttpError(resp.code, baseUrl, "/v1beta/models", resp.body?.string())
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val bodyString = resp.body?.string().orEmpty()
                val list = mutableListOf<ModelInfo>()

                try {
                    val root = JSONObject(bodyString)
                    val modelsArray = root.optJSONArray("models") ?: JSONArray()
                    for (i in 0 until modelsArray.length()) {
                        val obj = modelsArray.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val rawId = name.removePrefix("models/")
                        val displayName = obj.optString("displayName", rawId)
                        val description = obj.optString("description", "")
                        val supportedMethods = obj.optJSONArray("supportedGenerationMethods")
                        var canGenerate = false
                        if (supportedMethods != null) {
                            for (j in 0 until supportedMethods.length()) {
                                if (supportedMethods.getString(j) == "generateContent") {
                                    canGenerate = true
                                    break
                                }
                            }
                        } else {
                            canGenerate = true
                        }

                        if (canGenerate && rawId.isNotEmpty()) {
                            val isThinkingModel = rawId.contains("thinking") || rawId.contains("2.5") || rawId.contains("3.")
                            list.add(
                                ModelInfo(
                                    id = rawId,
                                    name = displayName,
                                    description = description,
                                    ownedBy = "google",
                                    supportsVision = true,
                                    supportsThinking = isThinkingModel,
                                    supportsTools = true
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    return@withContext Result.failure(IOException("Failed to parse Gemini models list: ${e.message}"))
                }

                if (list.isEmpty()) {
                    // Fallback to recommended catalog models if API returned empty
                    list.addAll(getGeminiCatalog())
                }
                Result.success(list)
            }
        } catch (e: Exception) {
            Result.failure(mapException(e, baseUrl))
        } finally {
            activeCall.set(null)
        }
    }

    private fun getGeminiCatalog(): List<ModelInfo> {
        return listOf(
            ModelInfo(
                id = "gemini-2.5-flash",
                name = "Gemini 2.5 Flash",
                description = "High-speed multimodal intelligence with adaptive reasoning",
                ownedBy = "google",
                supportsVision = true,
                supportsThinking = true,
                supportsTools = true
            ),
            ModelInfo(
                id = "gemini-2.5-pro",
                name = "Gemini 2.5 Pro",
                description = "Advanced reasoning for complex coding and multimodal analysis",
                ownedBy = "google",
                supportsVision = true,
                supportsThinking = true,
                supportsTools = true
            ),
            ModelInfo(
                id = "gemini-3.1-pro-preview",
                name = "Gemini 3.1 Pro Preview",
                description = "Next-gen frontier reasoning model with explicit thought parts",
                ownedBy = "google",
                supportsVision = true,
                supportsThinking = true,
                supportsTools = true
            ),
            ModelInfo(
                id = "gemini-3.5-flash",
                name = "Gemini 3.5 Flash",
                description = "Ultra-fast lightweight general intelligence",
                ownedBy = "google",
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
                        message = "Connected to Gemini API in ${latency}ms. Discovered ${models.size} models.",
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
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com" })
        val apiKey = endpointProfile.apiKey?.trim().orEmpty()
        val cleanModel = getCleanModelId(request.model.ifEmpty { endpointProfile.selectedModel })
        val url = "$baseUrl/v1beta/models/$cleanModel:generateContent?key=$apiKey"

        val payload = buildGeminiPayload(request)
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)
        activeCall.set(call)

        try {
            val response = call.execute()
            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorMsg = mapHttpError(resp.code, baseUrl, "/generateContent", resp.body?.string())
                    return@withContext Result.failure(IOException(errorMsg))
                }

                val bodyString = resp.body?.string().orEmpty()
                val json = JSONObject(bodyString)
                val candidates = json.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    return@withContext Result.failure(IOException("No candidates returned from Gemini."))
                }

                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")

                val reasoningBuilder = StringBuilder()
                val contentBuilder = StringBuilder()

                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.getJSONObject(i)
                        val text = part.optString("text", "")
                        val isThought = part.optBoolean("thought", false)

                        if (isThought) {
                            if (text.isNotEmpty() && text != "null") reasoningBuilder.append(text)
                        } else {
                            if (text.isNotEmpty() && text != "null") contentBuilder.append(text)
                        }
                    }
                }

                val finishReason = firstCandidate.optString("finishReason", null)
                val finalReasoning = reasoningBuilder.toString().takeIf { it.isNotEmpty() }
                val finalContent = contentBuilder.toString()

                Result.success(
                    UnifiedChatResponse(
                        id = System.currentTimeMillis().toString(),
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
        val baseUrl = normalizeBaseUrl(endpointProfile.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com" })
        val apiKey = endpointProfile.apiKey?.trim().orEmpty()
        val cleanModel = getCleanModelId(request.model.ifEmpty { endpointProfile.selectedModel })
        val url = "$baseUrl/v1beta/models/$cleanModel:streamGenerateContent?alt=sse&key=$apiKey"

        val payload = buildGeminiPayload(request)
        val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)

        val httpRequest = Request.Builder()
            .url(url)
            .header("x-goog-api-key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(body)
            .build()

        val call = client.newCall(httpRequest)
        activeCall.set(call)

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                val errorMsg = mapHttpError(response.code, baseUrl, "/streamGenerateContent", response.body?.string())
                response.close()
                throw IOException(errorMsg)
            }

            val respBody = response.body ?: throw IOException("Empty stream response from Gemini.")
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
                            emit(StreamChunk(text = "", isFinished = true, finishReason = "STOP"))
                            break
                        }

                        try {
                            val chunkJson = JSONObject(dataPayload)
                            val candidates = chunkJson.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val candidate = candidates.getJSONObject(0)
                                val contentObj = candidate.optJSONObject("content")
                                val parts = contentObj?.optJSONArray("parts")
                                val finishReason = candidate.optString("finishReason", null)?.takeIf { it != "null" }

                                if (parts != null) {
                                    for (i in 0 until parts.length()) {
                                        val part = parts.getJSONObject(i)
                                        val text = part.optString("text", "")
                                        val isThought = part.optBoolean("thought", false)

                                        if (text.isNotEmpty() && text != "null") {
                                            if (isThought) {
                                                emit(
                                                    StreamChunk(
                                                        text = "",
                                                        reasoningContent = text,
                                                        isFinished = false,
                                                        rawData = dataPayload
                                                    )
                                                )
                                            } else {
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
                                }

                                if (finishReason != null && finishReason != "null") {
                                    streamFinished = true
                                    emit(StreamChunk(text = "", isFinished = true, finishReason = finishReason))
                                }
                            }
                        } catch (e: JSONException) {
                            // Skip delta parsing error
                        }
                    }
                }

                if (!streamFinished) {
                    emit(StreamChunk(text = "", isFinished = true, finishReason = "STOP"))
                }
            }
        } catch (e: Exception) {
            throw mapException(e, baseUrl)
        } finally {
            activeCall.set(null)
        }
    }.flowOn(Dispatchers.IO)

    private fun buildGeminiPayload(request: UnifiedChatRequest): JSONObject {
        val root = JSONObject()
        val contentsArray = JSONArray()

        for (msg in request.messages) {
            val contentObj = JSONObject()
            contentObj.put("role", if (msg.role == "assistant") "model" else "user")
            val partsArray = JSONArray()

            // 1. Text part (strictly answer content)
            if (msg.content.isNotEmpty()) {
                val textPart = JSONObject()
                textPart.put("text", msg.content)
                partsArray.put(textPart)
            }

            // 2. Attachments parts
            for (att in msg.attachments) {
                if (att.isImage && !att.base64Data.isNullOrEmpty()) {
                    val inlinePart = JSONObject()
                    val inlineData = JSONObject()
                    inlineData.put("mimeType", att.mimeType)
                    inlineData.put("data", att.base64Data)
                    inlinePart.put("inlineData", inlineData)
                    partsArray.put(inlinePart)
                } else if (!att.extractedText.isNullOrEmpty()) {
                    val textPart = JSONObject()
                    textPart.put("text", "[Attachment: ${att.name}]\n${att.extractedText}")
                    partsArray.put(textPart)
                }
            }

            if (partsArray.length() > 0) {
                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
            }
        }

        root.put("contents", contentsArray)

        // Generation config
        val genConfig = JSONObject()
        request.temperature?.let { genConfig.put("temperature", it) }
        request.maxTokens?.let { genConfig.put("maxOutputTokens", it) }

        // Thinking config support
        if (request.thinkingEnabled && supportsThinking()) {
            val thinkingConfig = JSONObject()
            val budget = request.thinkingBudget ?: endpointProfile.thinkingBudget ?: 2048
            thinkingConfig.put("thinkingLevel", if (budget > 3000) "high" else "low")
            genConfig.put("thinkingConfig", thinkingConfig)
        }

        if (genConfig.length() > 0) {
            root.put("generationConfig", genConfig)
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
            401, 403 -> "Gemini Authentication Failed ($code): Invalid API key or permission denied. $detail"
            404 -> "Model or endpoint not found (404) at $baseUrl$path. $detail"
            429 -> "Gemini Rate Limit Exceeded (429): Quota exhausted for this API key. $detail"
            500, 503 -> "Google Gemini server error ($code). Please try again shortly."
            else -> "Gemini API error ($code): $detail"
        }
    }

    private fun mapException(e: Exception, baseUrl: String): Exception {
        return when (e) {
            is UnknownHostException -> IOException("DNS failure: Unable to reach Gemini endpoint '$baseUrl'. Check internet connection.", e)
            is ConnectException -> IOException("Connection refused while connecting to Gemini ($baseUrl).", e)
            is SocketTimeoutException -> IOException("Connection timed out waiting for Gemini response.", e)
            is IOException -> e
            else -> IOException("Gemini error: ${e.localizedMessage ?: "Unknown failure"}", e)
        }
    }
}
