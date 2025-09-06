package com.volmit.iris.core.scripting.kotlin.base

import com.volmit.iris.util.math.RNG
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.providedProperties

@KotlinScript(fileExtension = "noise.kts", compilationConfiguration = NoiseScriptDefinition::class)
abstract class NoiseScript

object NoiseScriptDefinition : ScriptCompilationConfiguration(listOf(DataScriptDefinition), {
    providedProperties("rng" to RNG::class)
}) {

    private fun readResolve(): Any = NoiseScriptDefinition
}