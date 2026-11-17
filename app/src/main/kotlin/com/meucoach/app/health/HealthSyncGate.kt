package com.meucoach.app.health

import java.time.Instant

/**
 * Data-gate outcome for plan generation (ADR 0048 §2.1, FR-409).
 *
 * It answers one question: has the Health Connect sync settled, so the week
 * may be generated? `Idle`/`Syncing` mean "wait"; `Ready` means "generate";
 * `Failed` means "generate with the available data and say so explicitly"
 * (`dataIncomplete`), because the sync may still arrive later.
 *
 * Named `Gate` (not `State`) because [HealthSyncState] already describes the
 * detailed sync UI state; this is the single boolean-like signal the plan
 * generation waits for.
 */
sealed interface HealthSyncGate {
    data object Idle : HealthSyncGate

    data object Syncing : HealthSyncGate

    data class Ready(val at: Instant) : HealthSyncGate

    data class Failed(val message: String) : HealthSyncGate
}
