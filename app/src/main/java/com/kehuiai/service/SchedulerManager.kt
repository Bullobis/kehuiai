@file:Suppress("UNUSED_PARAMETER", "UNCHECKED_CAST", "DEPRECATION", "USELESS_ELVIS")
package com.kehuiai.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 调度器管理器
 * 支持多种采样调度器：Euler, DPM, DDIM, UniPC 等
 */
class SchedulerManager(private val context: Context) {

    companion object {
        private const val TAG = "SchedulerManager"
        
        // 调度器类型
        const val EULER = "euler"
        const val EULER_A = "euler_a"
        const val DPM_2M = "dpm_2m"
        const val DPM_2M_KARRAS = "dpm_2m_karras"
        const val DPM_SDE = "dpm_sde"
        const val DPM_SDE_KARRAS = "dpm_sde_karras"
        const val DDIM = "ddim"
        const val PLMS = "plms"
        const val UNI_PC = "uni_pc"
        const val UNI_PC_BH2 = "uni_pc_bh2"
        const val LCM = "lcm"
        const val TCD = "tcd"
        const val HEUN = "heun"
        const val DPMSOLVER = "dpmsolver"
        const val DPMSOLVER_INTERPOL = "dpmsolver_interpol"
        const val DPMSOLVER_PLUS = "dpmsolver++"
        const val DPMSOLVER_PLUS_KARRS = "dpmsolver++_karrras"
        const val SADEGARD = "sa_ddim"
        const val DDIM_INVERSE = "ddim_inverse"
        const val IPNDM = "ipndm"
        const val IPNDM_V = "ipndm_v"
    }

    // 当前调度器配置
    private var currentScheduler: SchedulerConfig? = null
    
    // 预设调度器配置
    private val presets = mutableMapOf<String, SchedulerConfig>()

    init {
        initializePresets()
    }

    /**
     * 获取所有可用调度器
     */
    fun getAvailableSchedulers(): List<SchedulerInfo> {
        return listOf(
            SchedulerInfo(EULER, "Euler", "快速简单", true),
            SchedulerInfo(EULER_A, "Euler a", "快速，推荐", true),
            SchedulerInfo(DPM_2M, "DPM++ 2M", "平衡速度质量", true),
            SchedulerInfo(DPM_2M_KARRAS, "DPM++ 2M Karras", "高质量", true),
            SchedulerInfo(DPM_SDE, "DPM++ SDE", "高质量， slower", true),
            SchedulerInfo(DPM_SDE_KARRAS, "DPM++ SDE Karras", "最高质量", true),
            SchedulerInfo(DDIM, "DDIM", "传统，稳定", true),
            SchedulerInfo(PLMS, "PLMS", "传统", false),
            SchedulerInfo(UNI_PC, "UniPC", "快速收敛", true),
            SchedulerInfo(LCM, "LCM", "超快速 (4步)", true),
            SchedulerInfo(TCD, "TCD", "超快速", true),
            SchedulerInfo(HEUN, "Heun", "中等速度", true),
            SchedulerInfo(DPMSOLVER, "DPM-Solver", "高精度", true),
            SchedulerInfo(DPMSOLVER_PLUS, "DPM-Solver++", "高精度", true)
        )
    }

    /**
     * 创建调度器配置
     */
    fun createScheduler(
        schedulerType: String,
        steps: Int = 20,
        guidanceScale: Float = 7f,
        eta: Float = 0f,
        @Suppress("UNUSED_PARAMETER") s_churn: Float = 0f,
        @Suppress("UNUSED_PARAMETER") s_tmin: Float = 0f,
        @Suppress("UNUSED_PARAMETER") s_tmax: Float = Float.POSITIVE_INFINITY,
        @Suppress("UNUSED_PARAMETER") s_noise: Float = 1f
    ): SchedulerConfig {
        
        val config = when (schedulerType) {
            LCM -> {
                // LCM 使用特殊配置
                SchedulerConfig(
                    type = schedulerType,
                    steps = steps,
                    guidanceScale = guidanceScale,
                    eta = 0f,
                    solverOrder = 1,
                    predictionType = "epsilon",
                    timestepSpacing = "linspace",
                    guidanceRescale = 0f,
                    solverType = "normal",
                    algorithmType = "normal",
                    useClippedQOverride = false,
                    downstairsType = "linear",
                    upstairsType = "linear",
                    kappa = 1f
                )
            }
            
            DPM_2M_KARRAS, DPM_SDE_KARRAS -> {
                // Karras 调度器
                SchedulerConfig(
                    type = schedulerType,
                    steps = steps,
                    guidanceScale = guidanceScale,
                    eta = eta,
                    solverOrder = 2,
                    predictionType = "epsilon",
                    timestepSpacing = "karras",
                    guidanceRescale = 0f,
                    solverType = "dpmsolver",
                    algorithmType = "deis",
                    useClippedQOverride = false,
                    downstairsType = "karras",
                    upstairsType = "karras",
                    kappa = 1f
                )
            }
            
            DDIM, PLMS -> {
                SchedulerConfig(
                    type = schedulerType,
                    steps = steps,
                    guidanceScale = guidanceScale,
                    eta = eta,
                    solverOrder = 1,
                    predictionType = "epsilon",
                    timestepSpacing = "linspace",
                    guidanceRescale = 0f,
                    solverType = "ddim",
                    algorithmType = "ddim",
                    useClippedQOverride = false,
                    downstairsType = "linear",
                    upstairsType = "linear",
                    kappa = 1f
                )
            }
            
            else -> {
                // 默认 Euler 配置
                SchedulerConfig(
                    type = schedulerType,
                    steps = steps,
                    guidanceScale = guidanceScale,
                    eta = eta,
                    solverOrder = 1,
                    predictionType = "epsilon",
                    timestepSpacing = "linspace",
                    guidanceRescale = 0f,
                    solverType = "euler",
                    algorithmType = "euler",
                    useClippedQOverride = false,
                    downstairsType = "linear",
                    upstairsType = "linear",
                    kappa = 1f
                )
            }
        }
        
        currentScheduler = config
        return config
    }

    /**
     * 快速生成（使用 LCM）
     */
    fun quickGenerate(
        baseScheduler: String = EULER,
        targetSteps: Int = 4
    ): SchedulerConfig {
        return createScheduler(
            schedulerType = LCM,
            steps = targetSteps,
            guidanceScale = 1f  // LCM 通常不需要 CFG
        )
    }

    /**
     * 高质量生成
     */
    fun highQualityGenerate(
        baseScheduler: String = DPM_2M_KARRAS,
        targetSteps: Int = 30
    ): SchedulerConfig {
        return createScheduler(
            schedulerType = baseScheduler,
            steps = targetSteps,
            guidanceScale = 7f,
            eta = 0f
        )
    }

    /**
     * 获取当前调度器
     */
    fun getCurrentScheduler(): SchedulerConfig? = currentScheduler

    /**
     * 保存为预设
     */
    fun saveAsPreset(name: String, config: SchedulerConfig) {
        presets[name] = config
        Log.i(TAG, "Saved preset: $name")
    }

    /**
     * 加载预设
     */
    fun loadPreset(name: String): SchedulerConfig? {
        return presets[name]?.also {
            currentScheduler = it
        }
    }

    /**
     * 获取预设列表
     */
    fun getPresets(): Map<String, SchedulerConfig> = presets.toMap()

    /**
     * 估算生成时间
     */
    fun estimateTime(schedulerType: String, steps: Int, width: Int, height: Int): Long {
        val pixels = width * height
        val baseTime = when {
            pixels > 1024 * 1024 -> 5000L  // > 1MP
            pixels > 512 * 512 -> 3000L   // > 0.25MP
            else -> 1500L
        }
        
        val schedulerMultiplier = when (schedulerType) {
            LCM -> 0.3f
            TCD -> 0.4f
            EULER, EULER_A -> 0.8f
            DPM_2M -> 1.0f
            DPM_2M_KARRAS -> 1.2f
            DPM_SDE -> 1.5f
            DDIM -> 1.0f
            UNI_PC -> 0.9f
            else -> 1.0f
        }
        
        val stepsMultiplier = steps / 20f
        
        return (baseTime * schedulerMultiplier * stepsMultiplier).toLong()
    }

    /**
     * 获取推荐调度器
     */
    fun getRecommendedScheduler(
        priority: String = "balanced",  // balanced, speed, quality
        hasGPU: Boolean = true
    ): String {
        return when {
            priority == "speed" && hasGPU -> LCM
            priority == "speed" -> UNI_PC
            priority == "quality" && hasGPU -> DPM_SDE_KARRAS
            priority == "quality" -> DDIM
            priority == "balanced" && hasGPU -> DPM_2M
            priority == "balanced" -> EULER_A
            else -> EULER
        }
    }

    // ==================== 内部方法 ====================

    private fun initializePresets() {
        // 默认预设
        presets["快速"] = createScheduler(LCM, steps = 4)
        presets["平衡"] = createScheduler(EULER_A, steps = 20)
        presets["高质量"] = createScheduler(DPM_2M_KARRAS, steps = 30)
        presets["极致"] = createScheduler(DPM_SDE_KARRAS, steps = 50)
    }
}

/**
 * 调度器信息
 */
data class SchedulerInfo(
    val type: String,
    val name: String,
    val description: String,
    val isRecommended: Boolean = false
)

/**
 * 调度器配置
 */
data class SchedulerConfig(
    val type: String,
    val steps: Int = 20,
    val guidanceScale: Float = 7f,
    val eta: Float = 0f,
    val solverOrder: Int = 1,
    val predictionType: String = "epsilon",
    val timestepSpacing: String = "linspace",
    val guidanceRescale: Float = 0f,
    val solverType: String = "euler",
    val algorithmType: String = "euler",
    val useClippedQOverride: Boolean = false,
    val downstairsType: String = "linear",
    val upstairsType: String = "linear",
    val kappa: Float = 1f
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "scheduler_type" to type,
            "num_steps" to steps,
            "guidance_scale" to guidanceScale,
            "eta" to eta,
            "solver_order" to solverOrder,
            "prediction_type" to predictionType,
            "timestep_spacing" to timestepSpacing,
            "guidance_rescale" to guidanceRescale,
            "solver_type" to solverType,
            "algorithm_type" to algorithmType,
            "use_clipped_q_override" to useClippedQOverride,
            "downstairs_type" to downstairsType,
            "upstairs_type" to upstairsType,
            "kappa" to kappa
        )
    }
}
