package art.arcane.iris.core.scripting.kotlin.base

import art.arcane.iris.core.loader.IrisData
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.providedProperties

@KotlinScript(fileExtension = "data.kts", compilationConfiguration = DataScriptDefinition::class)
abstract class DataScript

object DataScriptDefinition : ScriptCompilationConfiguration(listOf(SimpleScriptDefinition), {
    providedProperties("data" to IrisData::class)
}) {
    private fun readResolve(): Any = DataScriptDefinition
}