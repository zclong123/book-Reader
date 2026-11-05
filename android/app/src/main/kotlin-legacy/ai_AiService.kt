package com.classics.readingroom.ai

import com.classics.readingroom.data.Book
import com.classics.readingroom.data.ChapterGuide
import com.classics.readingroom.data.Dossier
import com.classics.readingroom.data.Lecture
import com.classics.readingroom.data.LectureSection
import com.classics.readingroom.data.Quote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AiPreset(val key: String, val label: String, val baseURL: String, val model: String, val hint: String)

data class AiSettings(
    val preset: String = "ollama",
    val baseURL: String = "http://localhost:11434/v1",
    val model: String = "qwen2.5:7b",
    val apiKey: String = "",
    val temperature: Double = 0.7
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("preset", preset)
        put("baseURL", baseURL)
        put("model", model)
        put("apiKey", apiKey)
        put("temperature", temperature)
    }

    companion object {
        fun fromJson(obj: JSONObject?) = AiSettings(
            preset = obj?.optString("preset")?.ifBlank { "ollama" } ?: "ollama",
            baseURL = obj?.optString("baseURL") ?: "http://localhost:11434/v1",
            model = obj?.optString("model") ?: "qwen2.5:7b",
            apiKey = obj?.optString("apiKey") ?: "",
            temperature = obj?.optDouble("temperature", 0.7) ?: 0.7
        )
    }
}

class AiException(message: String) : Exception(message)

/**
 * 连接任意兼容 OpenAI Chat Completions 的服务（本地模型、云端服务或自建接口）。
 * 生成内容只保存在本机。
 */
object AiService {

    val PRESETS = listOf(
        AiPreset("ollama", "本地 Ollama", "http://localhost:11434/v1", "qwen2.5:7b",
            "需先在电脑上运行 Ollama 并拉取模型；手机与电脑在同一局域网时请把地址改成电脑的 IP。"),
        AiPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324:free",
            "需要填写 OpenRouter 的 API Key，可用模型以服务商当前提供的选项为准。"),
        AiPreset("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct",
            "需要硅基流动 API Key，部分小模型有免费额度。"),
        AiPreset("custom", "自定义 OpenAI 兼容接口", "", "", "任何兼容 /chat/completions 的接口都可以使用。")
    )

    fun preset(key: String): AiPreset = PRESETS.firstOrNull { it.key == key } ?: PRESETS.first()

    fun applyPreset(settings: AiSettings, key: String): AiSettings {
        val item = preset(key)
        return settings.copy(
            preset = item.key,
            baseURL = item.baseURL.ifBlank { settings.baseURL },
            model = item.model.ifBlank { settings.model }
        )
    }

    suspend fun generateBook(title: String, author: String, category: String, settings: AiSettings): Dossier =
        withContext(Dispatchers.IO) {
            val content = chat(settings, buildSystemPrompt(), buildUserPrompt(title, author, category, null, null))
            parseDossier(extractJson(content), title, author, category)
        }

    /** 为目录中只有简介的书生成完整精读详情 */
    suspend fun generateDetail(book: Book, settings: AiSettings): Dossier = withContext(Dispatchers.IO) {
        val user = buildUserPrompt(book.title, book.author, book.category, book.era, book.tags)
        val content = chat(settings, buildSystemPrompt(), user)
        parseDossier(extractJson(content), book.title, book.author, book.category).copy(
            id = book.id,
            country = book.country,
            era = book.era,
            summary = book.summary,
            tags = book.tags
        )
    }

    /** 按读者要求改写既有精读内容，未要求修改的部分原样保留 */
    suspend fun revise(bookTitle: String, detail: Dossier, instruction: String, scope: String, settings: AiSettings): Dossier =
        withContext(Dispatchers.IO) {
            val system = buildString {
                appendLine("你是一位中文名著精读编辑。读者会给你一份已有的精读档案 JSON 和修改要求。")
                appendLine("请按修改要求改写指定部分，保持 JSON 结构与字段名完全一致，未要求修改的部分原样保留。")
                append("只输出一个 JSON 对象，不要输出 Markdown 代码块，不要输出解释文字。")
            }
            val target = when (scope) {
                "lecture" -> JSONObject().put("lecture", detail.lecture.toJson())
                "essence" -> JSONObject().put("essence", JSONObject().apply {
                    put("story", JSONArray(detail.story))
                    put("insights", JSONArray(detail.insights))
                    put("quotes", JSONArray().apply { detail.quotes.forEach { put(it.toJson()) } })
                })
                else -> JSONObject().apply {
                    put("essence", JSONObject().apply {
                        put("story", JSONArray(detail.story))
                        put("insights", JSONArray(detail.insights))
                        put("quotes", JSONArray().apply { detail.quotes.forEach { put(it.toJson()) } })
                    })
                    put("lecture", detail.lecture.toJson())
                    put("chapters", JSONArray().apply { detail.chapters.forEach { put(it.toJson()) } })
                }
            }
            val scopeLabel = when (scope) {
                "lecture" -> "仅演讲书稿"
                "essence" -> "仅阅读精华"
                else -> "精读详情全部"
            }
            val user = buildString {
                appendLine("书名：$bookTitle（作者：${detail.author.ifBlank { "佚名" }}）")
                appendLine("修改范围：$scopeLabel")
                appendLine("修改要求：${instruction.ifBlank { "请把内容润色得更流畅、更有可读性" }}")
                appendLine("现有内容 JSON：")
                appendLine(target.toString())
                append("请输出修改后的完整 JSON（结构与输入一致）。")
            }
            val content = chat(settings, system, user)
            val raw = extractJson(content)
            val essence = raw.optJSONObject("essence")
            val lecture = raw.optJSONObject("lecture")
            detail.copy(
                story = essence?.optJSONArray("story")?.let { toStringList(it) } ?: detail.story,
                insights = essence?.optJSONArray("insights")?.let { toStringList(it) } ?: detail.insights,
                quotes = essence?.optJSONArray("quotes")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Quote::fromJson) }
                } ?: detail.quotes,
                lecture = if (lecture != null) Lecture.fromJson(lecture) else detail.lecture,
                chapters = raw.optJSONArray("chapters")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(ChapterGuide::fromJson) }
                } ?: detail.chapters,
                savedAt = System.currentTimeMillis()
            )
        }

    /** 测试连接：发一条极短请求 */
    suspend fun test(settings: AiSettings): String = withContext(Dispatchers.IO) {
        chat(settings, "你是助手。", "只回复两个字：可用", 32)
    }

    private fun buildSystemPrompt(): String = listOf(
        "你是一位中文名著精读编辑，擅长把一本书整理成适合普通读者收听的精读档案。",
        "请根据用户给出的书名，生成一份结构化精读档案。",
        "只输出一个 JSON 对象，不要输出 Markdown 代码块，不要输出解释文字。",
        "JSON 必须包含以下字段：",
        "{",
        "\"title\": \"书名\",",
        "\"subtitle\": \"一句有文采的副标题\",",
        "\"author\": \"作者\",",
        "\"authorIntro\": \"100字左右作者小传\",",
        "\"country\": \"国别\",",
        "\"era\": \"年代\",",
        "\"category\": \"类别，取值：古典小说、文史哲、诗词曲赋、近现代文学、外国文学\",",
        "\"tags\": [\"5个主题标签\"],",
        "\"readingMinutes\": 整数，预估阅读分钟数,",
        "\"summary\": \"一句话简介\",",
        "\"essence\": {",
        "  \"story\": [\"4段故事情节或思想脉络，每段120到200字\"],",
        "  \"insights\": [\"4条阅读所获，每条120到200字，要有观点，不要复述简介\"],",
        "  \"quotes\": [{\"text\":\"原文金句\",\"source\":\"出处\",\"note\":\"一句短评\"}]，共5条",
        "},",
        "\"lecture\": {",
        "  \"title\": \"每天精读一本书：书名\",",
        "  \"durationMinutes\": 25到30之间的整数,",
        "  \"tone\": \"讲稿语气，要符合作品气质\",",
        "  \"opening\": \"300到500字开场\",",
        "  \"sections\": [{\"title\":\"一、小标题\",\"paragraphs\":[\"3到4段，每段100到180字\"]}]，共9到11节,",
        "  \"closing\": \"200到300字收束\"",
        "},",
        "\"chapters\": [{\"title\":\"关键章节名\",\"guide\":\"章节导读\",\"excerpt\":\"一句节选\"}]，共5个",
        "}",
        "讲稿总字数控制在4000到5000字，目标时长25到30分钟，只讲干货，不要客套。",
        "内容要准确，不确定的细节使用学界通行说法，不要编造具体页码或数据。",
        "如果书名不是公认名著，也要按书名的字面含义生成合理内容，并在副标题中注明“自定义解读”。"
    ).joinToString("\n")

    private fun buildUserPrompt(title: String, author: String, category: String, era: String?, tags: List<String>?): String =
        buildString {
            appendLine("书名：$title")
            appendLine("作者：${author.ifBlank { "佚名" }}")
            appendLine("类别：${category.ifBlank { "文史哲" }}")
            if (!era.isNullOrBlank()) appendLine("年代：$era")
            if (!tags.isNullOrEmpty()) appendLine("主题标签：${tags.joinToString("、")}")
            append("请直接输出完整 JSON。")
        }

    private fun chat(settings: AiSettings, system: String, user: String, maxTokens: Int = 6000): String {
        val baseURL = settings.baseURL.trim().trimEnd('/')
        val model = settings.model.trim()
        if (baseURL.isEmpty()) throw AiException("请先填写服务地址")
        if (model.isEmpty()) throw AiException("请先填写模型名称")

        val connection = (URL("$baseURL/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 180_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (settings.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", system))
            put(JSONObject().put("role", "user").put("content", user))
        }
        val body = JSONObject().apply {
            put("model", model)
            put("temperature", settings.temperature)
            put("max_tokens", maxTokens)
            put("messages", messages)
        }

        try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body.toString())
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()
            if (code !in 200..299) throw AiException(errorMessage(code, text))
            val payload = runCatching { JSONObject(text) }.getOrNull() ?: throw AiException("服务返回的内容无法解析")
            val choices = payload.optJSONArray("choices")
            val message = choices?.optJSONObject(0)?.optJSONObject("message")
            val content = message?.opt("content")
            val result = when (content) {
                is String -> content
                is JSONArray -> (0 until content.length()).joinToString("") { i ->
                    content.optJSONObject(i)?.optString("text").orEmpty()
                }
                else -> payload.optJSONObject("message")?.optString("content").orEmpty()
            }
            if (result.isBlank()) throw AiException("服务没有返回内容")
            return result
        } catch (e: AiException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw AiException("请求超时，请稍后重试或换一个更快的模型")
        } catch (e: java.net.UnknownHostException) {
            throw AiException("无法连接服务，请检查地址与网络")
        } catch (e: java.io.IOException) {
            throw AiException("网络异常：${e.message ?: "连接失败"}")
        } finally {
            connection.disconnect()
        }
    }

    private fun errorMessage(code: Int, text: String): String {
        val detail = runCatching {
            val payload = JSONObject(text)
            payload.optJSONObject("error")?.optString("message")?.ifBlank { null }
                ?: payload.optString("message").ifBlank { null }
        }.getOrNull()
        if (detail != null) return detail
        return when (code) {
            401, 403 -> "API Key 无效或没有权限"
            404 -> "接口地址或模型名称不正确"
            429 -> "请求过于频繁或额度已用完"
            else -> "请求失败（HTTP $code）"
        }
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

    private fun parseDossier(raw: JSONObject, title: String, author: String, category: String): Dossier {
        val essence = raw.optJSONObject("essence") ?: JSONObject()
        val lectureObj = raw.optJSONObject("lecture") ?: JSONObject()
        val story = toStringList(essence.optJSONArray("story"))
        val insights = toStringList(essence.optJSONArray("insights"))
        val quotes = essence.optJSONArray("quotes")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Quote::fromJson) }
        }.orEmpty()
        val sections = lectureObj.optJSONArray("sections")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                LectureSection(obj.optString("title", "精读段落"), toStringList(obj.optJSONArray("paragraphs")))
            }
        }.orEmpty()
        val chapters = raw.optJSONArray("chapters")?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(ChapterGuide::fromJson) }
        }.orEmpty()
        val bookTitle = raw.optString("title").ifBlank { title }
        return Dossier(
            id = "",
            title = bookTitle,
            subtitle = raw.optString("subtitle").ifBlank { "自定义解读" },
            author = raw.optString("author").ifBlank { author.ifBlank { "佚名" } },
            authorIntro = raw.optString("authorIntro"),
            country = raw.optString("country").ifBlank { "中国" },
            era = raw.optString("era").ifBlank { "当代" },
            category = raw.optString("category").ifBlank { category },
            tags = toStringList(raw.optJSONArray("tags")),
            readingMinutes = raw.optInt("readingMinutes", 120),
            summary = raw.optString("summary").ifBlank { "《$bookTitle》的精读档案。" },
            story = story,
            insights = insights,
            quotes = quotes,
            lecture = Lecture(
                title = lectureObj.optString("title").ifBlank { "每天精读一本书：$bookTitle" },
                durationMinutes = lectureObj.optInt("durationMinutes", 25),
                tone = lectureObj.optString("tone").ifBlank { "平实、亲切" },
                opening = lectureObj.optString("opening"),
                sections = sections,
                closing = lectureObj.optString("closing")
            ),
            chapters = chapters,
            savedAt = System.currentTimeMillis()
        )
    }

    private fun toStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = ArrayList<String>(array.length())
        for (i in 0 until array.length()) {
            val value = array.optString(i)
            if (value.isNotBlank()) list.add(value)
        }
        return list
    }
}
