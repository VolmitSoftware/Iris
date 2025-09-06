package com.volmit.iris.core.scripting.kotlin.runner

import kotlin.script.experimental.api.EvaluationResult
import kotlin.script.experimental.api.ResultWithDiagnostics

interface Script {
    fun evaluate(properties: Map<String, Any?>?): ResultWithDiagnostics<EvaluationResult>
}
