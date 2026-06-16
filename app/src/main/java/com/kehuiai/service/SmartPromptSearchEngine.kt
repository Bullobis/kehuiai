/**
 * SmartPromptSearchEngine.kt
 * 智能提示词搜索引擎 - 基于 TF-IDF + N-gram 的语义搜索
 * 
 * 功能：
 * - 全文搜索提示词历史
 * - 基于词根/同义词的语义扩展
 * - 搜索结果相关性评分
 * - 热门关键词统计
 * - 搜索建议自动补全
 */
package com.kehuiai.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * 搜索索引条目
 */
data class SearchIndexEntry(
    val promptId: String,
    val text: String,
    val normalizedText: String,
    val wordFrequencies: Map<String, Int>,
    val timestamp: Long,
    val tags: List<String>,
    val isFavorite: Boolean
)

/**
 * 搜索结果
 */
data class SearchResult(
    val promptId: String,
    val text: String,
    val score: Float,
    val matchedTerms: List<String>,
    val timestamp: Long,
    val isFavorite: Boolean
)

/**
 * 搜索统计
 */
data class SearchStats(
    val totalSearches: Int,
    val popularTerms: List<Pair<String, Int>>,
    val averageResultCount: Float,
    val lastSearchTime: Long
)

/**
 * 搜索建议
 */
data class SearchSuggestion(
    val term: String,
    val frequency: Int,
    val category: SuggestionCategory
)

enum class SuggestionCategory {
    KEYWORD,    // 关键词
    TAG,        // 标签
    STYLE,      // 风格
    SUBJECT,    // 主体
    QUALITY     // 质量修饰词
}

/**
 * TF-IDF 向量 (用于余弦相似度计算)
 */
data class TfIdfVector(
    val terms: Map<String, Float>,
    val magnitude: Float
)

/**
 * 智能提示词搜索引擎
 */
class SmartPromptSearchEngine(private val context: Context) {

    companion object {
        private const val TAG = "PromptSearchEngine"
        private const val PREFS_NAME = "prompt_search_prefs"
        private const val KEY_TOTAL_SEARCHES = "total_searches"
        private const val KEY_LAST_SEARCH_TIME = "last_search_time"
        private const val KEY_AVG_RESULTS = "avg_results"
        private const val MIN_TERM_LENGTH = 2
        private const val MAX_RESULTS = 50
        private const val MIN_SCORE_THRESHOLD = 0.05f
        
        // 停用词列表
        private val STOP_WORDS = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "as", "is", "was", "are", "were", "been",
            "be", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "shall", "can", "need",
            "this", "that", "these", "those", "i", "you", "he", "she", "it",
            "we", "they", "my", "your", "his", "her", "its", "our", "their",
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都",
            "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你",
            "会", "着", "没有", "看", "好", "自己", "这", "那", "它"
        )
        
        // 词根映射 (简化版词干提取)
        private val STEM_MAPPINGS = mapOf(
            "beautiful" to "beauti",
            "beautifully" to "beauti",
            "beauty" to "beauti",
            "realistic" to "realist",
            "realism" to "realist",
            "realistically" to "realist",
            "detailed" to "detail",
            "detailing" to "detail",
            "details" to "detail",
            "photograph" to "photo",
            "photography" to "photo",
            "photographic" to "photo",
            "photorealistic" to "photo",
            "animated" to "anim",
            "animation" to "anim",
            "anime" to "anim",
            "artistic" to "art",
            "artistically" to "art",
            "illustration" to "illustr",
            "illustrated" to "illustr",
            "portrait" to "portrait",
            "portraits" to "portrait",
            "landscape" to "landscap",
            "landscapes" to "landscap",
            "character" to "character",
            "characters" to "character",
            "fantasy" to "fantasi",
            "fantastical" to "fantasi",
            "science" to "scienc",
            "scientific" to "scienc",
            "modern" to "modern",
            "traditional" to "tradition",
            "vibrant" to "vibrant",
            "colorful" to "color",
            "colourful" to "color",
            "elegant" to "elegant",
            "gorgeous" to "gorgeous",
            "stunning" to "stun",
            "dramatic" to "dramat",
            "peaceful" to "peace",
            "serene" to "serene",
            "mysterious" to "myster",
            "mystic" to "mystic",
            "ancient" to "ancient",
            "medieval" to "mediev",
            "futuristic" to "futur",
            "cyberpunk" to "cyberpunk",
            "steampunk" to "steampunk",
            "noir" to "noir",
            "gothic" to "gothic",
            "baroque" to "baroqu",
            "impressionist" to "impression",
            "abstract" to "abstract"
        )
    }
    
    // ===== 状态 =====
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _searchIndex = MutableStateFlow<Map<String, SearchIndexEntry>>(emptyMap())
    val searchIndex: StateFlow<Map<String, SearchIndexEntry>> = _searchIndex.asStateFlow()
    
    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<SearchStats> = _stats.asStateFlow()
    
    // TF-IDF 相关
    private var _idfValues = mapOf<String, Float>()
    private var _documentCount = 0
    
    // 热门搜索词统计
    private val _searchTermFrequency = MutableStateFlow<Map<String, Int>>(loadTermFrequency())
    
    // ===== 核心方法 =====
    
    /**
     * 索引一组提示词
     */
    fun indexPrompts(entries: List<IndexedPrompt>) {
        val startTime = System.currentTimeMillis()
        
        val newIndex = mutableMapOf<String, SearchIndexEntry>()
        for (entry in entries) {
            val text = entry.text
            val normalized = normalizeText(text)
            val words = tokenize(normalized)
            val frequencies = computeWordFrequencies(words)
            
            newIndex[entry.id] = SearchIndexEntry(
                promptId = entry.id,
                text = text,
                normalizedText = normalized,
                wordFrequencies = frequencies,
                timestamp = entry.timestamp,
                tags = entry.tags,
                isFavorite = entry.isFavorite
            )
        }
        
        // 更新 TF-IDF IDF 值
        _documentCount = newIndex.size
        _idfValues = computeIdfValues(newIndex.values.toList())
        _searchIndex.value = newIndex
        
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "索引构建完成: ${newIndex.size} 条记录, 耗时 ${elapsed}ms")
    }
    
    /**
     * 添加单个提示词到索引
     */
    fun addToIndex(entry: IndexedPrompt) {
        val normalized = normalizeText(entry.text)
        val words = tokenize(normalized)
        val frequencies = computeWordFrequencies(words)
        
        val indexEntry = SearchIndexEntry(
            promptId = entry.id,
            text = entry.text,
            normalizedText = normalized,
            wordFrequencies = frequencies,
            timestamp = entry.timestamp,
            tags = entry.tags,
            isFavorite = entry.isFavorite
        )
        
        val currentIndex = _searchIndex.value.toMutableMap()
        currentIndex[entry.id] = indexEntry
        _searchIndex.value = currentIndex
        
        // 更新 IDF
        _documentCount = currentIndex.size
        _idfValues = computeIdfValues(currentIndex.values.toList())
    }
    
    /**
     * 从索引中移除
     */
    fun removeFromIndex(promptId: String) {
        val currentIndex = _searchIndex.value.toMutableMap()
        currentIndex.remove(promptId)
        _searchIndex.value = currentIndex
        
        _documentCount = currentIndex.size
        _idfValues = computeIdfValues(currentIndex.values.toList())
    }
    
    /**
     * 更新索引条目
     */
    fun updateEntry(entry: IndexedPrompt) {
        removeFromIndex(entry.id)
        addToIndex(entry)
    }
    
    /**
     * 执行搜索
     */
    fun search(
        query: String,
        maxResults: Int = MAX_RESULTS,
        includeFavoritesOnly: Boolean = false,
        sortBy: SearchSortOrder = SearchSortOrder.RELEVANCE
    ): List<SearchResult> {
        if (query.isBlank()) return emptyList()
        
        val startTime = System.currentTimeMillis()
        
        // 记录搜索词频率
        recordSearchTerm(query)
        
        // 语义扩展查询词
        val expandedQuery = expandQuery(query)
        
        // 对每个文档计算 TF-IDF 向量
        val queryVector = computeQueryVector(expandedQuery)
        
        val index = _searchIndex.value
        val results = mutableListOf<SearchResult>()
        
        for ((promptId, docEntry) in index) {
            // 过滤收藏
            if (includeFavoritesOnly && !docEntry.isFavorite) continue
            
            // 计算 TF-IDF 向量
            val docVector = computeDocumentVector(docEntry.wordFrequencies)
            
            // 计算余弦相似度
            val score = cosineSimilarity(queryVector, docVector)
            
            if (score >= MIN_SCORE_THRESHOLD) {
                // 找出匹配的词
                val matchedTerms = findMatchedTerms(query, docEntry)
                
                results.add(SearchResult(
                    promptId = promptId,
                    text = docEntry.text,
                    score = score,
                    matchedTerms = matchedTerms,
                    timestamp = docEntry.timestamp,
                    isFavorite = docEntry.isFavorite
                ))
            }
        }
        
        // 排序
        val sortedResults = when (sortBy) {
            SearchSortOrder.RELEVANCE -> results.sortedByDescending { it.score }
            SearchSortOrder.RECENT -> results.sortedByDescending { it.timestamp }
            SearchSortOrder.FAVORITE -> results.sortedByDescending { 
                if (it.isFavorite) 1 else 0 
            }.sortedByDescending { it.timestamp }
        }
        
        val finalResults = sortedResults.take(maxResults)
        
        // 更新统计
        updateStats(finalResults.size, startTime)
        
        return finalResults
    }
    
    /**
     * 模糊搜索 (支持拼写错误容错)
     */
    fun fuzzySearch(query: String, maxResults: Int = MAX_RESULTS): List<SearchResult> {
        val normalizedQuery = normalizeText(query)
        val queryWords = tokenize(normalizedQuery).filter { it.length >= MIN_TERM_LENGTH }
        if (queryWords.isEmpty()) return emptyList()
        
        recordSearchTerm(query)
        
        val index = _searchIndex.value
        val results = mutableListOf<SearchResult>()
        
        for ((promptId, docEntry) in index) {
            val docWords = tokenize(docEntry.normalizedText).toSet()
            
            var matchCount = 0
            val matchedTerms = mutableListOf<String>()
            
            for (qWord in queryWords) {
                // 精确匹配
                if (docWords.contains(qWord)) {
                    matchCount += 2
                    matchedTerms.add(qWord)
                } else {
                    // 模糊匹配 (编辑距离)
                    for (docWord in docWords) {
                        if (docWord.length >= 3 && editDistance(qWord, docWord) <= 1) {
                            matchCount += 1
                            matchedTerms.add(docWord)
                            break
                        }
                    }
                }
            }
            
            if (matchCount > 0) {
                val score = matchCount.toFloat() / (queryWords.size * 2)
                results.add(SearchResult(
                    promptId = promptId,
                    text = docEntry.text,
                    score = score,
                    matchedTerms = matchedTerms.distinct(),
                    timestamp = docEntry.timestamp,
                    isFavorite = docEntry.isFavorite
                ))
            }
        }
        
        updateStats(results.size, System.currentTimeMillis())
        return results.sortedByDescending { it.score }.take(maxResults)
    }
    
    /**
     * 获取搜索建议 (自动补全)
     */
    fun getSuggestions(partialQuery: String, limit: Int = 8): List<SearchSuggestion> {
        if (partialQuery.length < 2) return emptyList()
        
        val normalized = normalizeText(partialQuery)
        val prefix = normalized.lowercase()
        val suggestions = mutableListOf<SearchSuggestion>()
        
        // 从索引中的词频获取建议
        val termFreq = _searchTermFrequency.value
        val allTerms = termFreq.keys.toList()
        
        // 前缀匹配
        for (term in allTerms) {
            if (term.startsWith(prefix)) {
                val category = categorizeTerm(term)
                suggestions.add(SearchSuggestion(
                    term = term,
                    frequency = termFreq[term] ?: 0,
                    category = category
                ))
            }
        }
        
        // 从文档内容中获取建议
        val index = _searchIndex.value
        val contentTerms = mutableMapOf<String, Int>()
        for (entry in index.values) {
            for ((word, freq) in entry.wordFrequencies) {
                if (word.startsWith(prefix) && word !in STOP_WORDS) {
                    contentTerms[word] = (contentTerms[word] ?: 0) + freq
                }
            }
        }
        
        for ((term, freq) in contentTerms) {
            if (suggestions.size >= limit) break
            if (suggestions.none { it.term == term }) {
                suggestions.add(SearchSuggestion(
                    term = term,
                    frequency = freq,
                    category = categorizeTerm(term)
                ))
            }
        }
        
        return suggestions.sortedByDescending { it.frequency }.take(limit)
    }
    
    /**
     * 获取热门搜索词
     */
    fun getPopularTerms(limit: Int = 20): List<Pair<String, Int>> {
        return _searchTermFrequency.value.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key to it.value }
    }
    
    /**
     * 获取关键词提取
     */
    fun extractKeywords(text: String, topN: Int = 10): List<Pair<String, Float>> {
        val normalized = normalizeText(text)
        val words = tokenize(normalized).filter { 
            it.length >= MIN_TERM_LENGTH && it !in STOP_WORDS 
        }
        val frequencies = computeWordFrequencies(words)
        
        val totalWords = words.size.toFloat()
        val keywords = mutableListOf<Pair<String, Float>>()
        
        for ((word, count) in frequencies) {
            val tf = count / totalWords
            val idf = _idfValues[word] ?: ln((_documentCount + 1).toFloat())
            val tfidf = tf * idf
            keywords.add(word to tfidf)
        }
        
        return keywords.sortedByDescending { it.second }.take(topN)
    }
    
    // ===== 私有方法 =====
    
    /**
     * 文本归一化
     */
    private fun normalizeText(text: String): String {
        return text
            .lowercase()
            .replace(Regex("[^\\w\\s\\u4e00-\\u9fff]"), " ")  // 保留中文、英文、数字、下划线、空格
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * 分词
     */
    private fun tokenize(text: String): List<String> {
        val tokens = mutableListOf<String>()
        
        // 英文分词 (保留 n-gram)
        val englishPattern = Regex("([a-zA-Z]+)")
        for (match in englishPattern.findAll(text)) {
            val word = match.value.lowercase()
            if (word.length >= MIN_TERM_LENGTH && word !in STOP_WORDS) {
                tokens.add(word)
                
                // 添加 bigram
                val stemmed = STEM_MAPPINGS[word] ?: word
                if (tokens.size > 0) {
                    val prev = tokens.last()
                    val prevStemmed = STEM_MAPPINGS[prev] ?: prev
                    tokens.add("${prevStemmed}_$stemmed")
                }
            }
        }
        
        // 中文分词 (简单的最大匹配)
        val chinesePattern = Regex("[\\u4e00-\\u9fff]+")
        for (match in chinesePattern.findAll(text)) {
            val chinese = match.value
            if (chinese.length >= 2) {
                // 提取 2-gram
                for (i in 0 until chinese.length - 1) {
                    val bigram = chinese.substring(i, i + 2)
                    tokens.add(bigram)
                }
                // 提取 3-gram
                for (i in 0 until chinese.length - 2) {
                    val trigram = chinese.substring(i, i + 3)
                    tokens.add(trigram)
                }
            }
        }
        
        return tokens
    }
    
    /**
     * 计算词频
     */
    private fun computeWordFrequencies(tokens: List<String>): Map<String, Int> {
        val freq = mutableMapOf<String, Int>()
        for (token in tokens) {
            freq[token] = (freq[token] ?: 0) + 1
        }
        return freq
    }
    
    /**
     * 计算 IDF 值
     */
    private fun computeIdfValues(documents: List<SearchIndexEntry>): Map<String, Float> {
        val docCount = documents.size.toFloat()
        val df = mutableMapOf<String, Int>()  // 文档频率
        
        for (doc in documents) {
            val uniqueTerms = doc.wordFrequencies.keys
            for (term in uniqueTerms) {
                df[term] = (df[term] ?: 0) + 1
            }
        }
        
        return df.mapValues { (_, docFreq) ->
            ln((docCount + 1) / (docFreq + 1)) + 1
        }
    }
    
    /**
     * 计算查询向量
     */
    private fun computeQueryVector(expandedQuery: List<String>): TfIdfVector {
        val words = tokenize(normalizedText(expandedQuery.joinToString(" ")))
        val frequencies = computeWordFrequencies(words)
        val totalTerms = words.size.toFloat()
        
        val termScores = mutableMapOf<String, Float>()
        for ((term, count) in frequencies) {
            val tf = count / totalTerms
            val idf = _idfValues[term] ?: 1f
            termScores[term] = tf * idf
        }
        
        val magnitude = sqrt(termScores.values.sumOf { (it * it).toDouble() }).toFloat()
        return TfIdfVector(termScores, magnitude.coerceAtLeast(0.001f))
    }
    
    /**
     * 计算文档向量
     */
    private fun computeDocumentVector(wordFrequencies: Map<String, Int>): TfIdfVector {
        val totalTerms = wordFrequencies.values.sum().toFloat().coerceAtLeast(1f)
        
        val termScores = mutableMapOf<String, Float>()
        for ((term, count) in wordFrequencies) {
            val tf = count / totalTerms
            val idf = _idfValues[term] ?: 1f
            termScores[term] = tf * idf
        }
        
        val magnitude = sqrt(termScores.values.sumOf { (it * it).toDouble() }).toFloat()
        return TfIdfVector(termScores, magnitude.coerceAtLeast(0.001f))
    }
    
    /**
     * 余弦相似度
     */
    private fun cosineSimilarity(vec1: TfIdfVector, vec2: TfIdfVector): Float {
        // 计算点积
        var dotProduct = 0f
        for ((term, score1) in vec1.terms) {
            val score2 = vec2.terms[term] ?: 0f
            dotProduct += score1 * score2
        }
        
        // 余弦相似度 = 点积 / (|vec1| * |vec2|)
        return dotProduct / (vec1.magnitude * vec2.magnitude)
    }
    
    /**
     * 语义扩展查询词
     */
    private fun expandQuery(query: String): List<String> {
        val expanded = mutableSetOf<String>()
        val normalized = normalizeText(query)
        val words = tokenize(normalized)
        
        for (word in words) {
            expanded.add(word)
            
            // 添加词根
            val stem = STEM_MAPPINGS[word] ?: word
            expanded.add(stem)
            
            // 添加同义词组
            val synonyms = getSynonyms(word)
            expanded.addAll(synonyms)
        }
        
        return expanded.toList()
    }
    
    /**
     * 获取同义词 (简化版)
     */
    private fun getSynonyms(word: String): List<String> {
        val synonymGroups = mapOf(
            "beautiful" to listOf("pretty", "lovely", "stunning", "gorgeous"),
            "realistic" to listOf("photorealistic", "natural", "lifelike"),
            "detailed" to listOf("intricate", "elaborate", "complex"),
            "modern" to listOf("contemporary", "current", "new"),
            "ancient" to listOf("old", "historical", "classic"),
            "colorful" to listOf("vibrant", "bright", "rainbow"),
            "dark" to listOf("shadowy", "dim", "gloomy"),
            "light" to listOf("bright", "illuminated", "glowing"),
            "peaceful" to listOf("calm", "serene", "tranquil"),
            "dramatic" to listOf("intense", "striking", "powerful"),
            "elegant" to listOf("graceful", "refined", "sophisticated"),
            "mysterious" to listOf("enigmatic", "cryptic", "secret"),
            "fantasy" to listOf("magical", "mythical", "dreamlike"),
            "futuristic" to listOf("sci-fi", "advanced", "high-tech"),
            "anime" to listOf("manga", "cartoon", "illustrated"),
            "portrait" to listOf("face", "portraiture", "portraiting"),
            "landscape" to listOf("scenery", "vista", "panorama"),
            "portrait" to listOf("portraiture", "character study"),
            "artistic" to listOf("creative", "artful", "illustrative"),
            "photo" to listOf("photograph", "photography", "snapshot"),
            "美丽" to listOf("漂亮", "好看", "精美"),
            "真实" to listOf("写实", "逼真", "自然"),
            "详细" to listOf("精细", "精致", "细腻"),
            "现代" to listOf("当代", "时尚", "新潮"),
            "古典" to listOf("传统", "经典", "复古")
        )
        
        return synonymGroups[word.lowercase()] ?: emptyList()
    }
    
    /**
     * 找出匹配的词
     */
    private fun findMatchedTerms(query: String, entry: SearchIndexEntry): List<String> {
        val matched = mutableListOf<String>()
        val queryWords = tokenize(normalizeText(query))
        
        for (qWord in queryWords) {
            val qStem = STEM_MAPPINGS[qWord] ?: qWord
            for (docWord in entry.wordFrequencies.keys) {
                val docStem = STEM_MAPPINGS[docWord] ?: docWord
                if (qStem == docStem || qWord == docWord) {
                    matched.add(docWord)
                }
            }
        }
        
        return matched.distinct()
    }
    
    /**
     * 编辑距离 (Levenshtein)
     */
    private fun editDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        if (m == 0) return n
        if (n == 0) return m
        
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }
        
        return dp[m][n]
    }
    
    /**
     * 归一化文本 (私有辅助)
     */
    private fun normalizedText(text: String): String {
        return normalizeText(text)
    }
    
    /**
     * 记录搜索词
     */
    private fun recordSearchTerm(query: String) {
        val words = tokenize(normalizeText(query)).filter { it.length >= 3 }
        val termFreq = _searchTermFrequency.value.toMutableMap()
        
        for (word in words) {
            termFreq[word] = (termFreq[word] ?: 0) + 1
        }
        
        _searchTermFrequency.value = termFreq
        saveTermFrequency(termFreq)
    }
    
    /**
     * 更新统计
     */
    private fun updateStats(resultCount: Int, startTime: Long) {
        val current = _stats.value
        val totalSearches = current.totalSearches + 1
        val avgResults = ((current.averageResultCount * current.totalSearches) + resultCount) / totalSearches
        
        _stats.value = SearchStats(
            totalSearches = totalSearches,
            popularTerms = current.popularTerms,
            averageResultCount = avgResults,
            lastSearchTime = System.currentTimeMillis()
        )
        
        prefs.edit()
            .putInt(KEY_TOTAL_SEARCHES, totalSearches)
            .putFloat(KEY_AVG_RESULTS, avgResults)
            .putLong(KEY_LAST_SEARCH_TIME, System.currentTimeMillis())
            .apply()
    }
    
    /**
     * 加载统计
     */
    private fun loadStats(): SearchStats {
        return SearchStats(
            totalSearches = prefs.getInt(KEY_TOTAL_SEARCHES, 0),
            popularTerms = emptyList(),
            averageResultCount = prefs.getFloat(KEY_AVG_RESULTS, 0f),
            lastSearchTime = prefs.getLong(KEY_LAST_SEARCH_TIME, 0L)
        )
    }
    
    /**
     * 加载词频
     */
    private fun loadTermFrequency(): Map<String, Int> {
        val file = File(context.filesDir, "search_term_freq.json")
        if (!file.exists()) return emptyMap()
        
        return try {
            val json = file.readText()
            parseSimpleJson(json)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 保存词频
     */
    private fun saveTermFrequency(freq: Map<String, Int>) {
        try {
            val file = File(context.filesDir, "search_term_freq.json")
            file.writeText(freq.entries.joinToString("\n") { "${it.key}:${it.value}" })
        } catch (e: Exception) {
            Log.e(TAG, "保存词频失败: ${e.message}")
        }
    }
    
    /**
     * 解析简单 JSON
     */
    private fun parseSimpleJson(json: String): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        for (line in json.lines()) {
            val parts = line.split(":")
            if (parts.size == 2) {
                result[parts[0]] = parts[1].toIntOrNull() ?: 0
            }
        }
        return result
    }
    
    /**
     * 分类词条
     */
    private fun categorizeTerm(term: String): SuggestionCategory {
        val qualityKeywords = setOf("quality", "masterpiece", "best", "detailed", "intricate", "highres", "8k", "4k", "精美", "高质量", "细腻")
        val styleKeywords = setOf("style", "artistic", "painting", "illustration", "anime", "realistic", "photo", "风格", "画风", "艺术")
        val subjectKeywords = setOf("portrait", "landscape", "character", "person", "animal", "nature", "city", "人物", "风景", "角色", "动物")
        
        return when {
            qualityKeywords.any { term.contains(it) } -> SuggestionCategory.QUALITY
            styleKeywords.any { term.contains(it) } -> SuggestionCategory.STYLE
            subjectKeywords.any { term.contains(it) } -> SuggestionCategory.SUBJECT
            term.length <= 4 -> SuggestionCategory.KEYWORD
            else -> SuggestionCategory.KEYWORD
        }
    }
    
    /**
     * 清除索引
     */
    fun clearIndex() {
        _searchIndex.value = emptyMap()
        _documentCount = 0
        _idfValues = emptyMap()
    }
    
    /**
     * 获取索引大小
     */
    fun getIndexSize(): Int = _searchIndex.value.size
    
    /**
     * 导出索引统计
     */
    fun exportIndexStats(): IndexStats {
        val index = _searchIndex.value
        val allWords = index.values.flatMap { it.wordFrequencies.keys }.toSet()
        
        return IndexStats(
            documentCount = index.size,
            uniqueTermCount = allWords.size,
            averageDocLength = if (index.isNotEmpty()) {
                index.values.map { it.wordFrequencies.size }.average()
            } else 0.0,
            totalSearches = _stats.value.totalSearches,
            averageResultsPerSearch = _stats.value.averageResultCount
        )
    }
    
    data class IndexStats(
        val documentCount: Int,
        val uniqueTermCount: Int,
        val averageDocLength: Double,
        val totalSearches: Int,
        val averageResultsPerSearch: Float
    )
    
    data class IndexedPrompt(
        val id: String,
        val text: String,
        val timestamp: Long,
        val tags: List<String>,
        val isFavorite: Boolean
    )
    
    enum class SearchSortOrder {
        RELEVANCE,
        RECENT,
        FAVORITE
    }
}
