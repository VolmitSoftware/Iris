package com.volmit.iris.core.scripting.kotlin.base

import com.volmit.iris.core.scripting.func.UpdateExecutor
import com.volmit.iris.util.mantle.MantleChunk
import org.bukkit.Chunk
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.providedProperties

@KotlinScript(fileExtension = "update.kts", compilationConfiguration = ChunkUpdateScriptDefinition::class)
abstract class ChunkUpdateScript

object ChunkUpdateScriptDefinition : ScriptCompilationConfiguration(listOf(EngineScriptDefinition), {
    providedProperties(
        "mantleChunk" to MantleChunk::class,
        "chunk" to Chunk::class,
        "executor" to UpdateExecutor::class
    )
}) {
    private fun readResolve(): Any = MobSpawningScriptDefinition
}