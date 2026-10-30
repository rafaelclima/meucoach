package com.meucoach.core.domain.golden

import org.json.JSONArray
import org.json.JSONObject

internal fun readGolden(path: String): JSONObject {
    val stream = checkNotNull(GoldenJson::class.java.getResourceAsStream(path)) {
        "Missing golden fixture $path"
    }
    return stream.bufferedReader().use { JSONObject(it.readText()) }
}

internal fun JSONArray.toStringList(): List<String> = List(length()) { index -> getString(index) }

internal object GoldenJson
