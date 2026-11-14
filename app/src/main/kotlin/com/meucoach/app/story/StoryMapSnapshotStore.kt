package com.meucoach.app.story

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Stages a [StoryMapSnapshot] between the preview and the background worker
 * without putting pixels in WorkManager `Data`. The PNG is the map bitmap and
 * the sidecar keeps the exact `pixelForLatLng` values; a missing or corrupt
 * sidecar makes the staged entry unusable, so the worker renders a fresh
 * snapshot instead of guessing the route (MAP-02).
 */
internal object StoryMapSnapshotStore {
    private const val MAGIC = 0x4D43504D // "MCMP"
    private const val VERSION = 1
    private const val MAX_POINTS = 20_000

    fun routeFile(mapFile: File): File = File(mapFile.parentFile, "${mapFile.nameWithoutExtension}.route")

    fun write(mapFile: File, snapshot: StoryMapSnapshot) {
        // Never let a stale sidecar describe a new bitmap.
        routeFile(mapFile).delete()
        mapFile.outputStream().use { output ->
            check(snapshot.bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                StoryMessages.MAP_STAGE_FAILED
            }
        }
        DataOutputStream(routeFile(mapFile).outputStream().buffered()).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeInt(snapshot.bitmap.width)
            output.writeInt(snapshot.bitmap.height)
            output.writeInt(snapshot.routePixels.size)
            snapshot.routePixels.forEach { point ->
                output.writeFloat(point.x)
                output.writeFloat(point.y)
            }
        }
    }

    fun read(mapFile: File): StoryMapSnapshot? {
        val routeFile = routeFile(mapFile)
        if (!mapFile.isFile || !routeFile.isFile) return null
        val bitmap = runCatching {
            BitmapFactory.decodeFile(mapFile.absolutePath, decodeOptions)
        }.getOrNull() ?: return null
        val snapshot = runCatching { readRoute(routeFile, bitmap) }.getOrNull()
        if (snapshot == null) bitmap.recycle()
        return snapshot
    }

    fun delete(mapFile: File) {
        runCatching { mapFile.delete() }
        runCatching { routeFile(mapFile).delete() }
    }

    private fun readRoute(routeFile: File, bitmap: Bitmap): StoryMapSnapshot {
        DataInputStream(routeFile.inputStream().buffered()).use { input ->
            check(input.readInt() == MAGIC) { "Manifesto de mapa inválido." }
            check(input.readInt() == VERSION) { "Versão de mapa não suportada." }
            check(input.readInt() == bitmap.width) { "Largura do mapa não confere." }
            check(input.readInt() == bitmap.height) { "Altura do mapa não confere." }
            val count = input.readInt()
            check(count in 0..MAX_POINTS) { "Rota do mapa inválida." }
            val points = ArrayList<PointF>(count)
            repeat(count) {
                val x = input.readFloat()
                val y = input.readFloat()
                check(x.isFinite() && y.isFinite()) { "Ponto do mapa inválido." }
                points += PointF(x, y)
            }
            check(input.available() == 0) { "Manifesto de mapa com dados extras." }
            return StoryMapSnapshot(bitmap = bitmap, routePixels = points)
        }
    }

    private val decodeOptions = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
}
