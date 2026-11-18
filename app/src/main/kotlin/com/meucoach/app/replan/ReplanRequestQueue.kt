package com.meucoach.app.replan

import com.meucoach.core.domain.replan.ReplanIntent

/**
 * Serializes replanning evaluations for one screen.
 *
 * While an evaluation runs, only an explicit request is remembered — and it
 * keeps its intent, so tapping "Tenho menos tempo" or "Perdi um treino" during
 * an automatic refresh never degrades to a plain request. A later explicit
 * request replaces an earlier queued one; automatic refreshes are dropped.
 */
internal class ReplanRequestQueue {
    var isEvaluating: Boolean = false
        private set

    private var queued: ReplanIntent? = null

    /** Returns true when the caller must start the evaluation. */
    fun begin(): Boolean {
        if (isEvaluating) return false
        isEvaluating = true
        return true
    }

    /** Remembers an explicit request that arrived while another evaluation ran. */
    fun enqueue(intent: ReplanIntent) {
        queued = intent
    }

    /** Marks the evaluation finished and returns the pending explicit intent, if any. */
    fun finish(): ReplanIntent? {
        isEvaluating = false
        val next = queued
        queued = null
        return next
    }
}
