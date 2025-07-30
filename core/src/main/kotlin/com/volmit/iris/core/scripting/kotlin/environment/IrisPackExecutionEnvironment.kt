package com.volmit.iris.core.scripting.kotlin.environment

import com.volmit.iris.core.loader.IrisData
import com.volmit.iris.core.scripting.environment.PackEnvironment
import com.volmit.iris.core.scripting.kotlin.base.DataScript
import com.volmit.iris.core.scripting.kotlin.base.NoiseScript
import com.volmit.iris.core.scripting.kotlin.runner.Script
import com.volmit.iris.core.scripting.kotlin.runner.valueOrThrow
import com.volmit.iris.util.math.RNG
import kotlin.reflect.KClass

open class IrisPackExecutionEnvironment(
    private val data: IrisData
) : IrisSimpleExecutionEnvironment(data.dataFolder), PackEnvironment {

    override fun getData() = data

    override fun compile(script: String, type: KClass<*>): Script {
        val loaded = data.scriptLoader.load(script)
        return compileCache.get(script)
            .computeIfAbsent(type) { _ -> runner.compile(type, loaded.loadFile, loaded.source) }
            .valueOrThrow("Failed to compile script $script")
    }

    override fun execute(script: String) =
        execute(script, DataScript::class.java, data.parameters())

    override fun evaluate(script: String) =
        evaluate(script, DataScript::class.java, data.parameters())

    override fun createNoise(script: String, rng: RNG) =
        evaluate(script, NoiseScript::class.java, data.parameters("rng" to rng))

    private fun IrisData.parameters(vararg values: Pair<String, Any?>): Map<String, Any?> {
        return mapOf(
            "data" to this,
            *values,
        )
    }
}