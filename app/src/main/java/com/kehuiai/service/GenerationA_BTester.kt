@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
/**
 * GenerationA_BTester.kt
 * 生成参数 A/B 测试框架
 * 
 * 功能：
 * - 参数对比实验设计
 * - 多维参数网格搜索
 * - 统计显著性分析
 * - 最优参数推荐
 * - 进化式参数优化 (遗传算法)
 */
package com.kehuiai.service

import android.util.Log
import kotlin.math.*
import kotlin.random.Random

/**
 * A/B 测试配置
 */
data class ABTestConfig(
    val name: String,
    val description: String,
    val parameterVariants: Map<String, List<Any>>,
    val metric: TestMetric = TestMetric.OVERALL_QUALITY,
    val maxIterations: Int = 20,
    val populationSize: Int = 10,
    val mutationRate: Float = 0.15f,
    val crossoverRate: Float = 0.7f,
    val earlyStoppingThreshold: Float = 0.95f,
    val earlyStoppingGenerations: Int = 3
)

/**
 * 测试指标
 */
enum class TestMetric {
    OVERALL_QUALITY,    // 整体质量
    SHARPNESS,         // 清晰度
    AESTHETIC_SCORE,   // 美学评分
    TEXT_ADHERENCE,    // 文本遵循度
    CREATIVITY,        // 创意度
    NOVELTY,           // 新颖性
    DIVERSITY,         // 多样性
    SPEED,             // 生成速度
    MEMORY_EFFICIENCY  // 内存效率
}

/**
 * 单个测试结果
 */
data class TestResult(
    val variantId: String,
    val parameters: Map<String, Any>,
    val scores: Map<TestMetric, Float>,
    val overallScore: Float,
    val confidenceInterval: Pair<Float, Float>,
    val sampleSize: Int,
    val timestamp: Long
)

/**
 * 测试报告
 */
data class ABTestReport(
    val testName: String,
    val startTime: Long,
    val endTime: Long,
    val totalVariants: Int,
    val winningVariant: TestResult?,
    val allResults: List<TestResult>,
    val parameterImportance: Map<String, Float>,
    val optimalParameters: Map<String, Any>,
    val statisticalSignificance: Float,
    val convergenceHistory: List<Float>,
    val recommendations: List<String>
)

/**
 * 参数范围
 */
data class ParameterRange(
    val name: String,
    val type: ParameterType,
    val minValue: Double,
    val maxValue: Double,
    val step: Double,
    val isLogarithmic: Boolean = false
)

enum class ParameterType {
    FLOAT, INT, ENUM, BOOL
}

/**
 * 参数个体 (用于遗传算法)
 */
data class ParameterIndividual(
    val genes: Map<String, Any>,
    val fitness: Float = 0f,
    val generation: Int = 0
)

/**
 * 网格搜索结果
 */
data class GridSearchResult(
    val bestParameters: Map<String, Any>,
    val bestScore: Float,
    val allScores: List<Map<String, Any>>
)

/**
 * A/B 测试引擎
 */
class GenerationA_BTester {
    
    companion object {
        private const val TAG = "ABTester"
        
        // 置信度参数
        private const val CONFIDENCE_LEVEL = 0.95f
        private const val MIN_SAMPLE_SIZE = 3
        
        // 统计参数
        private const val T_CRITICAL = 2.0f  // 95% 置信度
    }
    
    private val _activeTests = mutableMapOf<String, ABTestState>()
    
    // ===== 主要接口 =====
    
    /**
     * 创建参数网格搜索
     */
    fun createGridSearch(
        config: ABTestConfig,
        ranges: List<ParameterRange>
    ): GridSearchState {
        val state = GridSearchState(
            config = config,
            ranges = ranges,
            currentIndex = 0,
            results = mutableListOf()
        )
        
        val totalCombinations = calculateTotalCombinations(ranges)
        Log.d(TAG, "创建网格搜索: ${config.name}, 总组合数: $totalCombinations")
        
        _activeTests[config.name] = state
        return state
    }
    
    /**
     * 创建遗传算法优化
     */
    fun createGeneticOptimization(
        config: ABTestConfig,
        ranges: List<ParameterRange>
    ): GeneticAlgorithmState {
        val initialPopulation = generateInitialPopulation(config.populationSize, ranges)
        
        val state = GeneticAlgorithmState(
            config = config,
            ranges = ranges,
            population = initialPopulation,
            bestIndividual = null,
            generation = 0,
            convergenceHistory = mutableListOf(),
            noImprovementCount = 0
        )
        
        Log.d(TAG, "创建遗传优化: ${config.name}, 种群大小: ${config.populationSize}")
        
        _activeTests[config.name] = state
        return state
    }
    
    /**
     * 获取下一个测试参数组合
     */
    fun getNextParameters(testName: String): Map<String, Any>? {
        val state = _activeTests[testName] ?: return null
        
        return when (state) {
            is GridSearchState -> getNextGridParameters(state)
            is GeneticAlgorithmState -> getNextGeneticParameters(state)
            else -> null
        }
    }
    
    /**
     * 记录测试结果
     */
    fun recordResult(
        testName: String,
        variantId: String,
        parameters: Map<String, Any>,
        scores: Map<TestMetric, Float>
    ) {
        val state = _activeTests[testName] ?: return
        
        val metric = when (state) {
            is GridSearchState -> state.config.metric
            is GeneticAlgorithmState -> state.config.metric
            else -> TestMetric.OVERALL_QUALITY
        }
        val overallScore = scores[metric] 
            ?: scores[TestMetric.OVERALL_QUALITY] 
            ?: scores.values.average().toFloat()
        
        val result = TestResult(
            variantId = variantId,
            parameters = parameters,
            scores = scores,
            overallScore = overallScore,
            confidenceInterval = computeConfidenceInterval(scores.values.toList()),
            sampleSize = 1,
            timestamp = System.currentTimeMillis()
        )
        
        when (state) {
            is GridSearchState -> state.results.add(result)
            is GeneticAlgorithmState -> updateGeneticPopulation(state, parameters, overallScore)
            else -> {}
        }
        
        Log.d(TAG, "记录结果: $variantId, 分数: $overallScore")
    }
    
    /**
     * 运行完整网格搜索
     */
    suspend fun runGridSearchFull(
        config: ABTestConfig,
        ranges: List<ParameterRange>,
        evaluator: suspend (Map<String, Any>) -> Map<TestMetric, Float>
    ): GridSearchResult {
        val allCombinations = generateAllCombinations(ranges)
        val results = mutableListOf<Pair<Map<String, Any>, Float>>()
        
        for (params in allCombinations) {
            val scores = evaluator(params)
            val score = scores[config.metric] ?: scores[TestMetric.OVERALL_QUALITY] 
                ?: scores.values.average().toFloat()
            results.add(params to score)
        }
        
        val best = results.maxByOrNull { it.second } ?: results.firstOrNull()
        if (best == null) {
            return GridSearchResult(
                bestParameters = emptyMap(),
                bestScore = 0f,
                allScores = emptyList()
            )
        }
        
        return GridSearchResult(
            bestParameters = best.first,
            bestScore = best.second,
            allScores = results.map { it.first }
        )
    }
    
    /**
     * 获取当前测试状态
     */
    fun getTestState(testName: String): ABTestState? = _activeTests[testName]
    
    /**
     * 生成测试报告
     */
    fun generateReport(testName: String): ABTestReport? {
        val state = _activeTests[testName] ?: return null
        
        return when (state) {
            is GridSearchState -> generateGridReport(state)
            is GeneticAlgorithmState -> generateGeneticReport(state)
            else -> null
        }
    }
    
    /**
     * 多臂老虎机 (Thompson Sampling) 选择下一个参数
     */
    fun selectNextThompsonSampling(
        results: Map<String, List<Float>>
    ): String {
        if (results.isEmpty()) return ""
        
        // 对每个臂 (参数变体) 计算后验
        val samples = results.mapValues { (_, scores) ->
            if (scores.size < 2) {
                // 使用无信息先验 (Beta 分布)
                Random.nextDouble(0.0, 1.0)
            } else {
                // 使用 Beta posterior
                val alpha = scores.sum() + 1
                val beta = scores.size - scores.sum() + 1
                betaSample(alpha.toDouble(), beta.toDouble())
            }
        }
        
        return samples.maxByOrNull { it.value }?.key ?: ""
    }
    
    /**
     * 计算参数重要性 (基于相关系数)
     */
    fun computeParameterImportance(
        results: List<TestResult>,
        ranges: List<ParameterRange>
    ): Map<String, Float> {
        if (results.isEmpty()) return emptyMap()
        
        val importance = mutableMapOf<String, Float>()
        
        for (range in ranges) {
            val scores = mutableListOf<Float>()
            val values = mutableListOf<Double>()
            
            for (result in results) {
                val value = result.parameters[range.name]
                if (value != null && result.overallScore > 0) {
                    val numValue = when (value) {
                        is Number -> value.toDouble()
                        else -> 0.0
                    }
                    values.add(numValue)
                    scores.add(result.overallScore)
                }
            }
            
            if (values.size >= 3) {
                val correlation = pearsonCorrelation(values, scores)
                importance[range.name] = abs(correlation).toFloat()
            } else {
                importance[range.name] = 0f
            }
        }
        
        return importance
    }
    
    /**
     * 交叉验证评分
     */
    fun crossValidate(
        results: List<TestResult>,
        k: Int = 5
    ): Map<String, Float> {
        if (results.size < k) return emptyMap()
        
        val scores = results.map { it.overallScore }
        val foldSize = scores.size / k
        
        val foldScores = mutableMapOf<String, Float>()
        
        for (i in 0 until k) {
            val start = i * foldSize
            val end = if (i == k - 1) scores.size else (i + 1) * foldSize
            
            val validationSet = scores.subList(start, end)
            val trainSet = scores.filterIndexed { idx, _ -> idx < start || idx >= end }
            
            val trainMean = trainSet.average().toFloat()
            val trainStd = sqrt(trainSet.map { (it - trainMean) * (it - trainMean) }.average().toDouble()).toFloat()
            
            // 用训练集统计预测验证集
            for (vScore in validationSet) {
                val zScore = if (trainStd > 0f) (vScore - trainMean) / trainStd else 0f
                val foldKey = "fold_$i"
                foldScores[foldKey] = abs(zScore)
            }
        }
        
        return foldScores
    }
    
    // ===== 网格搜索私有方法 =====
    
    private fun calculateTotalCombinations(ranges: List<ParameterRange>): Int {
        return ranges.map { range ->
            when (range.type) {
                ParameterType.FLOAT, ParameterType.INT -> {
                    if (range.isLogarithmic) {
                        val logMin = ln(maxOf(range.minValue, 1e-10))
                        val logMax = ln(range.maxValue)
                        ((logMax - logMin) / range.step).toInt() + 1
                    } else {
                        ((range.maxValue - range.minValue) / range.step).toInt() + 1
                    }
                }
                ParameterType.ENUM -> (range.maxValue - range.minValue + 1).toInt()
                ParameterType.BOOL -> 2
            }
        }.fold(1) { acc, size -> acc * size }
    }
    
    private fun getNextGridParameters(state: GridSearchState): Map<String, Any>? {
        val allCombinations = generateAllCombinations(state.ranges)
        
        if (state.currentIndex >= allCombinations.size) {
            return null
        }
        
        return allCombinations[state.currentIndex++]
    }
    
    private fun generateAllCombinations(ranges: List<ParameterRange>): List<Map<String, Any>> {
        if (ranges.isEmpty()) return listOf(emptyMap())
        
        val result = mutableListOf<Map<String, Any>>()
        
        fun generate(index: Int, current: MutableMap<String, Any>) {
            if (index == ranges.size) {
                result.add(current.toMap())
                return
            }
            
            val range = ranges[index]
            val values = generateRangeValues(range)
            
            for (value in values) {
                current[range.name] = value
                generate(index + 1, current)
            }
            
            current.remove(range.name)
        }
        
        generate(0, mutableMapOf())
        return result
    }
    
    private fun generateRangeValues(range: ParameterRange): List<Any> {
        val values = mutableListOf<Any>()
        
        when (range.type) {
            ParameterType.FLOAT -> {
                if (range.isLogarithmic) {
                    val logMin = ln(maxOf(range.minValue, 1e-10))
                    val logMax = ln(range.maxValue)
                    var value = logMin
                    while (value <= logMax) {
                        values.add(exp(value).toFloat())
                        value += range.step
                    }
                } else {
                    var value = range.minValue
                    while (value <= range.maxValue) {
                        values.add(value.toFloat())
                        value += range.step
                    }
                }
            }
            ParameterType.INT -> {
                var value = range.minValue.toInt()
                while (value <= range.maxValue.toInt()) {
                    values.add(value)
                    value += range.step.toInt()
                }
            }
            ParameterType.ENUM -> {
                for (i in range.minValue.toInt()..range.maxValue.toInt()) {
                    values.add(i)
                }
            }
            ParameterType.BOOL -> {
                values.add(true)
                values.add(false)
            }
        }
        
        return values
    }
    
    private fun generateGridReport(state: GridSearchState): ABTestReport {
        val results = state.results
        
        val winningVariant = results.maxByOrNull { it.overallScore }
        
        // 参数重要性
        val parameterImportance = computeParameterImportance(results, state.ranges)
        
        // 最优参数
        val optimalParameters = winningVariant?.parameters ?: emptyMap()
        
        return ABTestReport(
            testName = state.config.name,
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            totalVariants = results.size,
            winningVariant = winningVariant,
            allResults = results,
            parameterImportance = parameterImportance,
            optimalParameters = optimalParameters,
            statisticalSignificance = computeStatisticalSignificance(results),
            convergenceHistory = listOf(winningVariant?.overallScore ?: 0f),
            recommendations = generateRecommendations(winningVariant, results)
        )
    }
    
    // ===== 遗传算法私有方法 =====
    
    private fun generateInitialPopulation(
        size: Int,
        ranges: List<ParameterRange>
    ): List<ParameterIndividual> {
        return (0 until size).map { 
            val genes = mutableMapOf<String, Any>()
            for (range in ranges) {
                genes[range.name] = randomValue(range)
            }
            ParameterIndividual(genes = genes, generation = 0)
        }
    }
    
    private fun randomValue(range: ParameterRange): Any {
        return when (range.type) {
            ParameterType.FLOAT -> {
                if (range.isLogarithmic) {
                    val logMin = ln(maxOf(range.minValue, 1e-10))
                    val logMax = ln(range.maxValue)
                    val logVal = Random.nextDouble(logMin, logMax)
                    exp(logVal).toFloat()
                } else {
                    Random.nextDouble(range.minValue, range.maxValue).toFloat()
                }
            }
            ParameterType.INT -> Random.nextInt(
                range.minValue.toInt(), 
                range.maxValue.toInt() + 1
            )
            ParameterType.ENUM -> Random.nextInt(
                range.minValue.toInt(), 
                range.maxValue.toInt() + 1
            )
            ParameterType.BOOL -> Random.nextBoolean()
        }
    }
    
    private fun getNextGeneticParameters(state: GeneticAlgorithmState): Map<String, Any>? {
        val currentGen = state.generation
        val pop = state.population
        
        if (currentGen >= state.config.maxIterations) {
            return null
        }
        
        // 选择当前代的一个个体
        return pop.firstOrNull()?.genes ?: emptyMap()
    }
    
    private fun updateGeneticPopulation(
        state: GeneticAlgorithmState,
        parameters: Map<String, Any>,
        fitness: Float
    ) {
        val idx = state.population.indexOfFirst { 
            it.genes.entries.all { (k, v) -> parameters[k] == v }
        }
        
        if (idx >= 0) {
            val updatedPopulation = state.population.toMutableList()
            updatedPopulation[idx] = updatedPopulation[idx].copy(fitness = fitness)
            state.population = updatedPopulation
        }
        
        // 检查收敛
        val bestFitness = state.population.maxOfOrNull { it.fitness } ?: 0f
        val currentBest = state.bestIndividual?.fitness ?: 0f
        
        if (bestFitness > currentBest) {
            state.bestIndividual = state.population.maxByOrNull { it.fitness }
            state.noImprovementCount = 0
        } else {
            state.noImprovementCount++
        }
        
        state.convergenceHistory.add(bestFitness)
        
        // 早停检查
        if (bestFitness >= state.config.earlyStoppingThreshold) {
            Log.d(TAG, "早停: 达到目标分数 ${bestFitness}")
            return
        }
        
        if (state.noImprovementCount >= state.config.earlyStoppingGenerations) {
            Log.d(TAG, "早停: ${state.config.earlyStoppingGenerations} 代无改进")
            return
        }
        
        // 下一代
        if (idx >= 0) {
            state.generation++
            if (state.generation < state.config.maxIterations) {
                evolvePopulation(state)
            }
        }
    }
    
    private fun evolvePopulation(state: GeneticAlgorithmState) {
        val currentPop = state.population.sortedByDescending { it.fitness }
        
        // 精英保留
        val eliteCount = maxOf(1, state.config.populationSize / 4)
        val elite = currentPop.take(eliteCount)
        
        // 选择 + 交叉 + 变异
        val newPop = elite.toMutableList()
        
        while (newPop.size < state.config.populationSize) {
            // 锦标赛选择
            val parent1 = tournamentSelect(currentPop)
            val parent2 = tournamentSelect(currentPop)
            
            // 交叉
            val child = if (Random.nextFloat() < state.config.crossoverRate) {
                crossover(parent1, parent2, state.ranges)
            } else {
                parent1
            }
            
            // 变异
            val mutated = mutate(child, state.ranges, state.config.mutationRate)
            newPop.add(mutated)
        }
        
        state.population = newPop.take(state.config.populationSize)
            .map { it.copy(generation = state.generation) }
    }
    
    private fun tournamentSelect(population: List<ParameterIndividual>): ParameterIndividual {
        if (population.isEmpty()) {
            return ParameterIndividual(emptyMap(), 0f, 0)
        }
        val tournamentSize = maxOf(2, population.size / 4)
        return (0 until tournamentSize)
            .mapNotNull { population.getOrNull(Random.nextInt(population.size)) }
            .maxByOrNull { it.fitness } ?: population.first()
    }
    
    private fun crossover(
        parent1: ParameterIndividual,
        parent2: ParameterIndividual,
        ranges: List<ParameterRange>
    ): ParameterIndividual {
        val childGenes = mutableMapOf<String, Any>()
        
        for (range in ranges) {
            val p1Val = parent1.genes[range.name]
            val p2Val = parent2.genes[range.name]
            
            childGenes[range.name] = if (Random.nextBoolean()) p1Val ?: randomValue(range)
                                     else p2Val ?: randomValue(range)
        }
        
        return ParameterIndividual(genes = childGenes, generation = parent1.generation)
    }
    
    private fun mutate(
        individual: ParameterIndividual,
        ranges: List<ParameterRange>,
        mutationRate: Float
    ): ParameterIndividual {
        val mutatedGenes = individual.genes.toMutableMap()
        
        for (range in ranges) {
            if (Random.nextFloat() < mutationRate) {
                mutatedGenes[range.name] = randomValue(range)
            }
        }
        
        return ParameterIndividual(genes = mutatedGenes, generation = individual.generation)
    }
    
    private fun generateGeneticReport(state: GeneticAlgorithmState): ABTestReport {
        val winningVariant = state.bestIndividual?.let { best ->
            TestResult(
                variantId = "genetic_best",
                parameters = best.genes,
                scores = mapOf(TestMetric.OVERALL_QUALITY to best.fitness),
                overallScore = best.fitness,
                confidenceInterval = Pair(best.fitness * 0.9f, best.fitness * 1.1f),
                sampleSize = state.generation,
                timestamp = System.currentTimeMillis()
            )
        }
        
        val parameterImportance = computeParameterImportance(
            state.population.map { 
                TestResult(
                    variantId = "",
                    parameters = it.genes,
                    scores = mapOf(TestMetric.OVERALL_QUALITY to it.fitness),
                    overallScore = it.fitness,
                    confidenceInterval = Pair(0f, 1f),
                    sampleSize = 1,
                    timestamp = 0
                )
            },
            state.ranges
        )
        
        return ABTestReport(
            testName = state.config.name,
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            totalVariants = state.population.size * state.generation,
            winningVariant = winningVariant,
            allResults = emptyList(),
            parameterImportance = parameterImportance,
            optimalParameters = state.bestIndividual?.genes ?: emptyMap(),
            statisticalSignificance = state.convergenceHistory.lastOrNull() ?: 0f,
            convergenceHistory = state.convergenceHistory,
            recommendations = generateRecommendations(winningVariant, emptyList())
        )
    }
    
    // ===== 统计方法 =====
    
    private fun computeConfidenceInterval(scores: List<Float>): Pair<Float, Float> {
        if (scores.size < MIN_SAMPLE_SIZE) {
            return Pair(0f, 1f)
        }
        
        val mean = scores.average().toFloat()
        val std = sqrt(scores.map { (it - mean) * (it - mean) }.average().toDouble()).toFloat()
        val sem = std / sqrt(scores.size.toFloat())  // 标准误差
        
        val margin = T_CRITICAL * sem
        return Pair((mean - margin).coerceAtLeast(0f), (mean + margin).coerceAtMost(1f))
    }
    
    private fun computeStatisticalSignificance(results: List<TestResult>): Float {
        if (results.size < 2) return 0f
        
        val scores = results.map { it.overallScore }
        val mean = scores.average().toFloat()
        val std = sqrt(scores.map { (it - mean) * (it - mean) }.average().toDouble()).toFloat()
        
        if (std < 0.001f) return 1f
        
        // 简化的效应量 Cohen's d
        val effectSize = std / (scores.maxOrNull() ?: 1f)
        return (effectSize.coerceIn(0f, 1f))
    }
    
    private fun pearsonCorrelation(x: List<Double>, y: List<Float>): Double {
        val n = minOf(x.size, y.size)
        if (n < 3) return 0.0
        
        val meanX = x.take(n).average()
        val meanY = y.take(n).average()
        
        var numerator = 0.0
        var denomX = 0.0
        var denomY = 0.0
        
        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            numerator += dx * dy
            denomX += dx * dx
            denomY += dy * dy
        }
        
        val denom = sqrt(denomX * denomY)
        return if (denom > 0) numerator / denom else 0.0
    }
    
    private fun betaSample(alpha: Double, beta: Double): Double {
        // 使用 Gamma 分布生成 Beta 样本
        val gammaA = gammaSample(alpha)
        val gammaB = gammaSample(beta)
        return gammaA / (gammaA + gammaB)
    }
    
    private fun gammaSample(shape: Double): Double {
        // 简化: 使用指数近似
        return -ln(Random.nextDouble(0.0, 1.0)) * shape
    }
    
    private fun generateRecommendations(
        winner: TestResult?,
        allResults: List<TestResult>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (winner != null) {
            recommendations.add("最优参数组合已找到，可直接使用")
            
            for ((param, value) in winner.parameters) {
                recommendations.add("$param = $value")
            }
        }
        
        if (allResults.size > 5) {
            val scoreVariance = allResults.map { it.overallScore }.variance()
            if (scoreVariance > 0.1) {
                recommendations.add("参数对结果影响显著，建议进一步微调")
            }
        }
        
        return recommendations
    }
    
    private fun List<Float>.variance(): Float {
        if (size < 2) return 0f
        val mean = average().toFloat()
        return map { (it - mean) * (it - mean) }.average().toFloat()
    }
    
    // ===== 状态类 =====
    
    sealed class ABTestState
    data class GridSearchState(
        val config: ABTestConfig,
        val ranges: List<ParameterRange>,
        var currentIndex: Int,
        val results: MutableList<TestResult>
    ) : ABTestState()
    
    data class GeneticAlgorithmState(
        val config: ABTestConfig,
        val ranges: List<ParameterRange>,
        var population: List<ParameterIndividual>,
        var bestIndividual: ParameterIndividual?,
        var generation: Int,
        val convergenceHistory: MutableList<Float>,
        var noImprovementCount: Int
    ) : ABTestState()
}
