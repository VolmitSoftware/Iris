package com.volmit.iris.core.scripting.kotlin.base

import com.volmit.iris.core.loader.IrisRegistrant
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.providedProperties

@KotlinScript(fileExtension = "proc.kts", compilationConfiguration = PreprocessorScriptDefinition::class)
abstract class PreprocessorScript

object PreprocessorScriptDefinition : ScriptCompilationConfiguration(listOf(EngineScriptDefinition), {
    providedProperties("object" to IrisRegistrant::class)
}) {
    private fun readResolve(): Any = PreprocessorScriptDefinition
}