package com.meucoach.app.story

import com.meucoach.core.domain.story.StoryMapStyle
import java.io.File

/**
 * Disk cache for the story map (MAP-06, primeira fatia).
 *
 * - Style JSON em disco: o `liberty` customizado é baixado uma vez por estilo
 *   e reaproveitado no reexport, sem novo download.
 * - LRU simples por `lastModified`: após cada escrita, arquivos antigos são
 *   removidos até o diretório `stories` ficar abaixo de [MAX_CACHE_BYTES].
 * - A limpeza total no reset de dados continua em `DataResetCoordinator`
 *   (apaga `cacheDir/stories`); aqui só contemos o crescimento.
 *
 * O manifesto com pixels da rota continua sendo o sidecar `.route` do
 * [StoryMapSnapshotStore]; o `manifest.json` por variante
 * (`<activityId>/<variantHash>/`) chega na próxima fatia do MAP-06.
 */
internal object StoryMapDiskCache {
    const val STYLE_VERSION = 1
    const val MAX_CACHE_BYTES = 200L * 1024L * 1024L

    fun stylesDir(cacheDir: File): File = File(File(cacheDir, "stories"), "styles")

    fun styleFile(cacheDir: File, style: StoryMapStyle): File {
        val name = "style-" + style.name.lowercase() + "-v$STYLE_VERSION.json"
        return File(stylesDir(cacheDir), name)
    }

    fun readStyle(cacheDir: File, style: StoryMapStyle): String? {
        val file = styleFile(cacheDir, style)
        if (!file.isFile) return null
        return runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    fun writeStyle(cacheDir: File, style: StoryMapStyle, json: String) {
        if (json.isBlank()) return
        val file = styleFile(cacheDir, style)
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json)
        }
    }

    fun storiesSize(storiesDir: File): Long {
        if (!storiesDir.isDirectory) return 0L
        return storiesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Remove os arquivos mais antigos até o total ficar abaixo do teto.
     * Arquivos `.route` andam junto com o bitmap: remover só o bitmap deixa
     * um sidecar órfão que o [StoryMapSnapshotStore.read] já rejeita, então a
     * ordem de remoção apaga o sidecar junto quando o bitmap sai.
     */
    fun trim(storiesDir: File, maxBytes: Long = MAX_CACHE_BYTES) {
        if (!storiesDir.isDirectory) return
        var total = storiesSize(storiesDir)
        if (total <= maxBytes) return
        val files = storiesDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()
        for (file in files) {
            if (total <= maxBytes) break
            total -= deleteWithSidecar(file)
        }
    }

    private fun deleteWithSidecar(file: File): Long {
        var freed = 0L
        val size = file.length()
        if (runCatching { file.delete() }.getOrDefault(false)) freed += size
        val sidecar = sidecarFor(file) ?: return freed
        if (!sidecar.isFile) return freed
        val sidecarSize = sidecar.length()
        if (runCatching { sidecar.delete() }.getOrDefault(false)) freed += sidecarSize
        return freed
    }

    private fun sidecarFor(file: File): File? {
        if (file.extension != "jpg" && file.extension != "png") return null
        return File(file.parentFile, "${file.nameWithoutExtension}.route")
    }
}
