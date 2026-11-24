package com.meucoach.app.story

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Startup orphan sweep for the private art photos (ADR 0061 §2.2, F7).
 *
 * The sweep itself lives in [ArtPhotoStore.sweepOrphans]: it deletes `.tmp`
 * and unreferenced `.jpg` files older than 24 h and never touches a referenced
 * photo (including a waiting photo under `TRANSPARENT`). This helper only
 * makes the startup call small, non-blocking and unit-testable: the
 * Application launches it in `applicationScope` with `runCatching`, so a
 * failure never blocks the main thread and never crashes the app.
 */
@Singleton
class ArtPhotoStartupSweeper @Inject constructor(private val store: ArtPhotoStore) {
    suspend fun sweep(nowMillis: Long = System.currentTimeMillis()): Int = runCatching {
        store.sweepOrphans(nowMillis)
    }.getOrElse { error ->
        Log.w(TAG, "Art photo startup sweep failed", error)
        0
    }

    companion object {
        private const val TAG = "ArtPhotoSweep"
    }
}
