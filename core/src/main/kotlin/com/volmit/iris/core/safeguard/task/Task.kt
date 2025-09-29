package com.volmit.iris.core.safeguard.task

import com.volmit.iris.core.safeguard.Mode
import com.volmit.iris.util.format.Form
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class Task(
    val id: String,
    val name: String = Form.capitalizeWords(id.replace(" ", "_").lowercase()),
) {

    abstract fun run(): ValueWithDiagnostics<Mode>

    companion object {
        fun of(id: String, name: String = id, action: () -> ValueWithDiagnostics<Mode>) = object : Task(id, name) {
            override fun run() = action()
        }

        fun of(id: String, action: () -> ValueWithDiagnostics<Mode>) = object : Task(id) {
            override fun run() = action()
        }

        fun task(action: () -> ValueWithDiagnostics<Mode>) = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Task>> { _, _ ->
            ReadOnlyProperty { _, property -> of(property.name, action) }
        }
    }
}