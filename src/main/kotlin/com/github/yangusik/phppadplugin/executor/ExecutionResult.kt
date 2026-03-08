package com.github.yangusik.phppadplugin.executor

import com.google.gson.JsonObject

data class ExecutionResult(
    val json: JsonObject? = null,
    val error: String? = null
) {
    val isError get() = error != null
}
