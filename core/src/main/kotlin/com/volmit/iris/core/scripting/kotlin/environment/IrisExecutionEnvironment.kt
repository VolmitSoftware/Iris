package com.volmit.iris.core.scripting.kotlin.environment

import com.volmit.iris.core.loader.IrisRegistrant
import com.volmit.iris.core.scripting.environment.EngineEnvironment
import com.volmit.iris.core.scripting.func.BiomeLookup
import com.volmit.iris.core.scripting.kotlin.base.EngineScript
import com.volmit.iris.core.scripting.kotlin.base.MobSpawningScript
import com.volmit.iris.core.scripting.kotlin.base.PostMobSpawningScript
import com.volmit.iris.core.scripting.kotlin.base.PreprocessorScript
import com.volmit.iris.engine.framework.Engine
import org.bukkit.Location
import org.bukkit.entity.Entity

data class IrisExecutionEnvironment(
    private val engine: Engine
) : IrisPackExecutionEnvironment(engine.data), EngineEnvironment {
    override fun getEngine() = engine

    override fun execute(script: String) =
        execute(script, EngineScript::class.java, engine.parameters())

    override fun evaluate(script: String) =
        evaluate(script, EngineScript::class.java, engine.parameters())

    override fun spawnMob(script: String, location: Location) =
        evaluate(script, MobSpawningScript::class.java, engine.parameters("location" to location))

    override fun postSpawnMob(script: String, location: Location, mob: Entity) =
        execute(script, PostMobSpawningScript::class.java, engine.parameters("location" to location, "entity" to mob))

    override fun preprocessObject(script: String, `object`: IrisRegistrant) =
        execute(script, PreprocessorScript::class.java, engine.parameters("object" to `object`))

    private fun Engine.parameters(vararg values: Pair<String, Any?>): Map<String, Any?> {
        return mapOf(
            "data" to data,
            "engine" to this,
            "complex" to complex,
            "seed" to seedManager.seed,
            "dimension" to dimension,
            "biome" to BiomeLookup(::getSurfaceBiome),
            *values,
        )
    }
}