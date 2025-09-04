package com.volmit.iris.core.scripting.kotlin.base

import org.bukkit.entity.Entity
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.providedProperties

@KotlinScript(fileExtension = "postspawn.kts", compilationConfiguration = PostMobSpawningScriptDefinition::class)
abstract class PostMobSpawningScript

object PostMobSpawningScriptDefinition : ScriptCompilationConfiguration(listOf(MobSpawningScriptDefinition), {
    providedProperties("entity" to Entity::class)
}) {
    private fun readResolve(): Any = PostMobSpawningScriptDefinition
}