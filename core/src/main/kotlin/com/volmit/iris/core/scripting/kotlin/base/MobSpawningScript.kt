package com.volmit.iris.core.scripting.kotlin.base

import org.bukkit.Location
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.providedProperties

@KotlinScript(fileExtension = "spawn.kts", compilationConfiguration = MobSpawningScriptDefinition::class)
abstract class MobSpawningScript

object MobSpawningScriptDefinition : ScriptCompilationConfiguration(listOf(EngineScriptDefinition), {
    providedProperties("location" to Location::class)
}) {
    private fun readResolve(): Any = MobSpawningScriptDefinition
}