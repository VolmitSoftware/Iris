package com.volmit.iris.core.scripting.kotlin.base

import com.volmit.iris.core.scripting.func.BiomeLookup
import com.volmit.iris.engine.IrisComplex
import com.volmit.iris.engine.framework.Engine
import com.volmit.iris.engine.`object`.IrisDimension
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.providedProperties

@KotlinScript(fileExtension = "engine.kts", compilationConfiguration = EngineScriptDefinition::class)
abstract class EngineScript

object EngineScriptDefinition : ScriptCompilationConfiguration(listOf(DataScriptDefinition), {
    providedProperties(
        "engine" to Engine::class,
        "complex" to IrisComplex::class,
        "seed" to Long::class,
        "dimension" to IrisDimension::class,
        "biome" to BiomeLookup::class,
    )
}) {

    private fun readResolve(): Any = EngineScriptDefinition
}