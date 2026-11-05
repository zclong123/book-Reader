package com.classics.readingroom.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AiSettings(
    val preset: String,
    val baseURL: String,
    val model: String,
    val apiKey: String,
    val temperature: Double
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("preset", preset)
        put("baseURL", baseURL)
        put("model", model)
        put("apiKey", apiKey)
        put("temperature", temperature)
    }

    companion object {
        fun fromJson(obj: JSONObject?): AiSettings = AiSettings(
            preset = obj?.optString("preset", AiService.PRESETS.first().key) ?: AiService.PRESETS.first().key,
            baseURL = obj?.optString("baseURL", AiService.PRESETS.first().baseURL) ?: AiService.PRESETS.first().baseURL,
            model = obj?.optString("model", AiService.PRESETS.first().model) ?: AiService.PRESETS.first().model,
            apiKey = obj?.optString("apiKey") ?: "",
            temperature = obj?.optDouble("temperature", 0.7) ?: 0.7
        )
    }
}

data class AiPreset(val key: String, val label: String, val baseURL: String, val model: String, val hint: String)

/** 调用兼容 OpenAI Chat Completions 的模型服务 */
object AiService {

    val PRESETS = listOf(
        AiPreset(
            "ollama",
            "本地 Ollama",
            "http://10.0.2.2:11434/v1",
            "qwen2.5:7b",
            "先在电脑上安装 Ollama 并拉取模型；模拟器用 10.0.2.2 访问电脑，真机请填电脑的局域网地址。"
        ),
        AiPreset(
            "openrouter",
            "OpenRouter",
            "https://openrouter.ai/api/v1",
            "deepseek/deepseek-chat",
            "需要填写 OpenRouter 的 API Key，模型名称按服务商当前提供的列表填写（如 deepseek/deepseek-chat）。"
        ),
        AiPreset(
            "siliconflow",
            "硅基流动",
            "https://api.siliconflow.cn/v1",
            "Qwen/Qwen2.5-7B-Instruct",
            "需要硅基流动的 API Key，部分小模型有额度赠送。"
        ),
        AiPreset(
            "custom",
            "自定义接口",
            "",
            "",
            "任何兼容 /chat/completions 的接口都可以使用，填到 /v1 为止即可，App 会自动拼上 /chat/completions。"
        )
    )

    fun defaultSettings(): AiSettings = AiSettings.fromJson(null)

    class AiException(message: String) : Exception(message)

    suspend fun chat(
        settings: AiSettings,
        system: String,
        user: String,
        maxTokens: Int = 6000,
        temperature: Double = settings.temperature
    ): String = withContext(Dispatchers.IO) {
        val baseURL = settings.baseURL.trim().trimEnd('/')
        if (baseURL.isEmpty()) throw AiException("请先填写服务地址")
        if (settings.model.isBlank()) throw AiException("请先填写模型名称")

        // 部分服务对 max_tokens 有上限，过大直接返回 400
        val tokenLimit = maxTokens.coerceAtMost(MAX_TOKENS_LIMIT)
        val payload = JSONObject().apply {
            put("model", settings.model.trim())
            put("temperature", temperature)
            put("max_tokens", tokenLimit)
            put(
                "messages",
                JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", system))
                    put(JSONObject().put("role", "user").put("content", user))
                }
            )
        }

        val endpoint = if (baseURL.endsWith("/chat/completions")) baseURL else "$baseURL/chat/completions"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 300_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (settings.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
        }

        try {
            val requestBody = payload.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(requestBody.size)
            connection.outputStream.use { it.write(requestBody) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            } ?: ""
            if (code !in 200..299) throw AiException(errorMessage(code, body))
            extractContent(body, finishReason(body))
        } catch (e: AiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw AiException("模型响应超时。内容较长时生成一份档案可能需要一两分钟，请重试或换更快的模型。")
        } catch (e: Exception) {
            throw AiException("无法连接模型服务（${e.javaClass.simpleName}: ${e.message ?: "网络错误"}）。请检查服务地址、手机网络，以及该服务是否允许 App 直连（部分服务需要 HTTPS 或白名单域名）。")
        } finally {
            connection.disconnect()
        }
    }

    private fun finishReason(body: String): String =
        runCatching { JSONObject(body).optJSONArray("choices")?.optJSONObject(0)?.optString("finish_reason") }.getOrNull().orEmpty()

    private const val MAX_TOKENS_LIMIT = 8000

    private fun extractContent(body: String, finishReason: String = ""): String {
        val obj = runCatching { JSONObject(body) }.getOrNull() ?: throw AiException("服务返回的内容无法解析")
        val message = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
        val content = when {
            message?.has("content") == true && message.get("content") is JSONArray ->
                (message.get("content") as JSONArray).let { array ->
                    (0 until array.length()).joinToString("") { i -> array.optJSONObject(i)?.optString("text").orEmpty() }
                }
            message != null -> message.optString("content")
            else -> obj.optJSONObject("message")?.optString("content").orEmpty()
        }
        if (content.isBlank()) {
            throw AiException(
                when (finishReason) {
                    "length" -> "模型输出被长度上限截断，没有返回可用内容，请换一个上下文更长的模型"
                    else -> "模型没有返回内容（finish_reason=$finishReason）"
                }
            )
        }
        return content
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = runCatching {
            val obj = JSONObject(body)
            obj.optJSONObject("error")?.optString("message")
                ?: obj.optString("message")
                ?: obj.optString("error")
        }.getOrNull()
        if (!detail.isNullOrBlank()) return detail
        return when (code) {
            401, 403 -> "API Key 无效或没有权限"
            404 -> "接口地址或模型名称不正确"
            429 -> "请求过于频繁或额度已用完"
            else -> "请求失败（HTTP $code）"
        }
    }

    /* ---------------- 提示词 ---------------- */

    fun detailPrompt(book: Book): Pair<String, String> {
        val system = buildPrompt()
        val user = """
            书名：${book.title}
            作者：${book.author.ifBlank { "佚名" }}
            国别：${book.country}
            年代：${book.era}
            类别：${book.category}
            主题标签：${book.tags.joinToString("、")}
            请结合以上信息直接输出完整 JSON。
        """.trimIndent()
        return system to user
    }

    fun revisePrompt(book: Book, detail: Dossier, instruction: String, scope: String): Pair<String, String> {
        val system = """
            你是一位中文名著精读编辑。读者会给你一份已有的精读档案 JSON 和修改要求。
            请按修改要求改写指定部分，保持 JSON 结构与字段名完全一致，未要求修改的部分原样保留。
            只输出一个 JSON 对象，不要输出 Markdown 代码块，不要输出解释文字。
        """.trimIndent()
        val target = JSONObject().apply {
            when (scope) {
                "lecture" -> put("lecture", detail.lecture.toJson())
                "essence" -> put(
                    "essence",
                    JSONObject().apply {
                        put("story", JSONArray(detail.story))
                        put("insights", JSONArray(detail.insights))
                        put("quotes", JSONArray().apply { detail.quotes.forEach { put(it.toJson()) } })
                    }
                )
                else -> put("essence", detail.toJson().optJSONObject("essence")).put("lecture", detail.lecture.toJson())
            }
        }
        val scopeLabel = when (scope) {
            "lecture" -> "仅演讲书稿"
            "essence" -> "仅阅读精华"
            else -> "精读详情全部"
        }
        val user = """
            书名：${book.title}（作者：${book.author.ifBlank { "佚名" }}）
            修改范围：$scopeLabel
            修改要求：${instruction.ifBlank { "请把内容润色得更流畅、更有可读性" }}
            现有内容 JSON（已紧凑，便于传输）：
            ${target.toString()}
            请输出修改后的完整 JSON（结构与输入一致）。
        """.trimIndent()
        return system to user
    }

    private fun buildPrompt(): String = """
        你是一位中文名著精读编辑，擅长把一本书整理成适合普通读者收听的精读档案。
        请根据用户给出的书名，生成一份结构化精读档案。
        只输出一个 JSON 对象，不要输出 Markdown 代码块，不要输出解释文字。
        JSON 必须包含以下字段：
        {
        "title": "书名",
        "subtitle": "一句有文采的副标题",
        "author": "作者",
        "authorIntro": "100字左右作者小传",
        "country": "国别",
        "era": "年代",
        "category": "类别，取值：古典小说、文史哲、诗词曲赋、近现代文学、外国文学、科普新知",
        "tags": ["5个主题标签"],
        "readingMinutes": 整数，预估阅读分钟数,
        "summary": "一句话简介",
        "essence": {
          "story": ["4段故事情节或思想脉络，每段120到200字"],
          "insights": ["4条阅读所获，每条120到200字，要有观点，不要复述简介"],
          "quotes": [{"text":"原文金句","source":"出处","note":"一句短评"}]，共5条
        },
        "lecture": {
          "title": "每天精读一本书：书名",
          "durationMinutes": 25到30之间的整数,
          "tone": "讲稿语气，要符合作品气质",
          "opening": "300到500字开场",
          "sections": [{"title":"一、小标题","paragraphs":["3到4段，每段100到180字"]}]，共9到11节,
          "closing": "200到300字收束"
        },
        "chapters": [{"title":"关键章节名","guide":"章节导读","excerpt":"一句节选"}]，共5个
        }
        讲稿总字数控制在6500到7800字之间，目标时长25到30分钟，只讲干货。
    输出必须是一个完整的 JSON 对象；无法写满篇幅时宁可精简，也不要截断。
        内容要准确，不确定的细节使用学界通行说法，不要编造具体页码或数据。
        如果书名不是公认名著，也要按书名的字面含义生成合理内容，并在副标题中注明“自定义解读”。
    """.trimIndent()

    /* ---------------- 解析 ---------------- */

    /** 设置页的“测试连接”：只发一句话，快速确认地址、密钥与网络是否正常 */
    suspend fun testConnection(settings: AiSettings): String {
        val reply = chat(
            settings = settings,
            system = "你是连通性测试助手，请只回复四个字：连接成功",
            user = "请回复：连接成功",
            maxTokens = 32,
            temperature = 0.0
        )
        return reply.trim().take(60)
    }

    fun extractJson(text: String): JSONObject {
        val content = text.trim()
        if (content.isEmpty()) throw AiException("模型没有返回内容")
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE).find(content)
        val candidate = fenced?.groupValues?.get(1) ?: content
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("模型返回的内容里没有找到 JSON")
        return runCatching { JSONObject(candidate.substring(start, end + 1)) }
            .getOrElse { throw AiException("模型返回的 JSON 格式不完整，请重试或换一个模型") }
    }

    /** 把模型输出规整成一部书的档案（可指定范围），同名作者等基础信息以目录为准 */
    fun normalize(raw: JSONObject, base: Book, scope: String = "all"): Dossier {
        val essenceObj = raw.optJSONObject("essence")
        val lectureObj = raw.optJSONObject("lecture")
        val lecture = Lecture.fromJson(lectureObj)
        return Dossier(
            id = base.id,
            title = base.title.ifBlank { raw.optString("title") },
            subtitle = raw.optString("subtitle", base.subtitle),
            author = base.author.ifBlank { raw.optString("author", "佚名") },
            authorIntro = raw.optString("authorIntro", base.authorIntro),
            country = base.country,
            era = base.era,
            category = base.category,
            tags = if (base.tags.isNotEmpty()) base.tags else raw.optJSONArray("tags").toStringList().take(6),
            readingMinutes = if (base.readingMinutes > 0) base.readingMinutes else raw.optInt("readingMinutes", 120),
            summary = raw.optString("summary", base.summary),
            story = essenceObj?.optJSONArray("story").toStringList(),
            insights = essenceObj?.optJSONArray("insights").toStringList(),
            quotes = essenceObj?.optJSONArray("quotes").toObjectList { Quote.fromJson(it) }.filter { it.text.isNotBlank() } ?: emptyList(),
            lecture = lecture,
            chapters = raw.optJSONArray("chapters").toObjectList { ChapterGuide.fromJson(it) }.filter { it.title.isNotBlank() },
            isFallback = false
        )
    }

    /** AI 修改只覆盖指定部分，其余内容保持原样 */
    fun merge(base: Dossier, raw: JSONObject, scope: String): Dossier {
        val essenceObj = raw.optJSONObject("essence")
        val lectureObj = raw.optJSONObject("lecture")
        val chaptersArray = raw.optJSONArray("chapters")
        val useEssence = scope == "all" || scope == "essence"
        val useLecture = scope == "all" || scope == "lecture"
        return base.copy(
            story = if (useEssence && essenceObj?.has("story") == true) essenceObj.optJSONArray("story").toStringList() else base.story,
            insights = if (useEssence && essenceObj?.has("insights") == true) essenceObj.optJSONArray("insights").toStringList() else base.insights,
            quotes = if (useEssence && essenceObj?.has("quotes") == true) {
                essenceObj.optJSONArray("quotes").toObjectList { Quote.fromJson(it) }.filter { it.text.isNotBlank() }
            } else base.quotes,
            lecture = if (useLecture && lectureObj != null) Lecture.fromJson(lectureObj) else base.lecture,
            chapters = if (scope == "all" && chaptersArray != null) {
                chaptersArray.toObjectList { ChapterGuide.fromJson(it) }.filter { it.title.isNotBlank() }
            } else base.chapters,
            savedAt = System.currentTimeMillis()
        )
    }
}
