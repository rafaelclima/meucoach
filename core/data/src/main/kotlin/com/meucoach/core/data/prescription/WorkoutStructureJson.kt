package com.meucoach.core.data.prescription

import com.meucoach.core.domain.prescription.BlockKind
import com.meucoach.core.domain.prescription.WorkoutBlock
import com.meucoach.core.domain.prescription.WorkoutStructure
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON codec for [WorkoutStructure] (ADR 0047 §2.1, ADR 0048 §2.3).
 *
 * The payload is versioned. Anything the current app cannot interpret — a
 * `null`/blank old row, malformed JSON, an unknown block kind or a future
 * structure version — decodes as `null`, never as an empty or partial
 * prescription. Invalid structures also decode as `null` because the domain
 * validates the time budget on construction.
 */
internal object WorkoutStructureJson {
    const val CURRENT_VERSION = 1

    fun toJson(structure: WorkoutStructure): String = JSONObject().apply {
        put("version", structure.version)
        put("warmupMin", structure.warmupMin)
        put("cooldownMin", structure.cooldownMin)
        put("totalMin", structure.totalMin)
        put(
            "blocks",
            JSONArray().apply {
                structure.blocks.forEach { put(blockToJson(it)) }
            },
        )
    }.toString()

    fun fromJson(value: String?): WorkoutStructure? {
        if (value.isNullOrBlank() || value.trim() == JSON_NULL) return null
        val root = runCatching { JSONObject(value) }.getOrNull() ?: return null
        val version = root.optInt("version", 0)
        if (version != CURRENT_VERSION) return null
        return runCatching {
            val blocks = root.getJSONArray("blocks")
            WorkoutStructure(
                version = version,
                warmupMin = root.getInt("warmupMin"),
                blocks = List(blocks.length()) { index -> blockFromJson(blocks.getJSONObject(index)) },
                cooldownMin = root.getInt("cooldownMin"),
                totalMin = root.getInt("totalMin"),
            )
        }.getOrNull()
    }

    private fun blockToJson(block: WorkoutBlock): JSONObject = JSONObject().apply {
        put("kind", block.kind.name)
        block.repetitions?.let { put("repetitions", it) }
        block.workSec?.let { put("workSec", it) }
        block.recoverySec?.let { put("recoverySec", it) }
        block.target?.let { put("target", it) }
        block.note?.let { put("note", it) }
    }

    private fun blockFromJson(json: JSONObject): WorkoutBlock = WorkoutBlock(
        kind = BlockKind.valueOf(json.getString("kind")),
        repetitions = json.optNullableInt("repetitions"),
        workSec = json.optNullableInt("workSec"),
        recoverySec = json.optNullableInt("recoverySec"),
        target = json.optNullableString("target"),
        note = json.optNullableString("note"),
    )

    private fun JSONObject.optNullableInt(name: String): Int? = if (has(name) && !isNull(name)) getInt(name) else null

    private fun JSONObject.optNullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name).takeIf { it.isNotEmpty() }

    private const val JSON_NULL = "null"
}
