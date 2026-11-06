package com.meucoach.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.meucoach.core.data.db.MeuCoachDatabase
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Pace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DailyPrescriptionRepositoryTest {
    private lateinit var database: MeuCoachDatabase
    private lateinit var repository: DailyPrescriptionRepository
    private var nextId = 0
    private val today = LocalDate.of(2026, 11, 5)
    private val clock = Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeuCoachDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        repository = DailyPrescriptionRepository(database, clock) { "prescription-${++nextId}" }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `appends the first version and exposes it as the latest`(): Unit = runBlocking {
        val first = repository.append(prescription())

        assertThat(first.id).isEqualTo("prescription-1")
        assertThat(first.supersedesId).isNull()
        assertThat(repository.getLatest(today)).isEqualTo(first)
        assertThat(repository.observeLatest(today).first()).isEqualTo(first)
    }

    @Test
    fun `a changed decision appends a version that supersedes the previous one`(): Unit = runBlocking {
        val first = repository.append(prescription(hash = "hash-1"))
        val second = repository.append(prescription(hash = "hash-2", adjustment = PrescriptionAdjustment.REDUCE))

        assertThat(second.supersedesId).isEqualTo(first.id)
        assertThat(repository.getLatest(today)).isEqualTo(second)
        assertThat(repository.getVersions(today)).containsExactly(first, second)
    }

    @Test
    fun `the same inputs hash is not duplicated`(): Unit = runBlocking {
        val first = repository.append(prescription(hash = "hash-1"))
        val duplicate = repository.append(prescription(hash = "hash-1"))

        assertThat(duplicate.id).isEqualTo(first.id)
        assertThat(repository.getVersions(today)).hasSize(1)
    }

    @Test
    fun `round-trips targets and audit fields`(): Unit = runBlocking {
        val targets = PrescriptionTargets(
            pace = PaceTarget(
                fast = Pace.fromSecondsPerKm(360),
                slow = Pace.fromSecondsPerKm(370),
            ),
            heartRate = HeartRateTarget(146, 161),
            zone = "Z2",
            distanceKm = 6.4,
        )
        val stored = repository.append(prescription(targets = targets))

        val loaded = repository.getLatest(today)
        assertThat(loaded).isEqualTo(stored)
        assertThat(loaded?.targets).isEqualTo(targets)
        assertThat(loaded?.ruleIds).containsExactly("R-ZONE-01", "R-PRESC-01")
        assertThat(loaded?.evidenceIds).containsExactly("EV-SEILER-2010")
        assertThat(loaded?.engineVersion).isEqualTo("daily-prescription-v1")
        assertThat(loaded?.inputsHash).isEqualTo("hash-1")
    }

    @Test
    fun `versions are scoped by date`(): Unit = runBlocking {
        val yesterday = today.minusDays(1)
        repository.append(prescription(date = yesterday, hash = "old"))
        val current = repository.append(prescription(hash = "new"))

        assertThat(repository.getLatest(today)).isEqualTo(current)
        assertThat(repository.getLatest(yesterday)?.inputsHash).isEqualTo("old")
        assertThat(repository.getVersions(today)).hasSize(1)
    }

    @Test
    fun `reverting to a previous input appends a new version instead of reusing the old row`(): Unit = runBlocking {
        repository.append(prescription(hash = "hash-1"))
        repository.append(prescription(hash = "hash-2"))
        val reverted = repository.append(prescription(hash = "hash-1"))

        assertThat(reverted.inputsHash).isEqualTo("hash-1")
        assertThat(repository.getLatest(today)).isEqualTo(reverted)
        assertThat(repository.getVersions(today)).hasSize(3)
    }

    private fun prescription(
        date: LocalDate = today,
        hash: String = "hash-1",
        adjustment: PrescriptionAdjustment = PrescriptionAdjustment.KEEP,
        targets: PrescriptionTargets = PrescriptionTargets(zone = "Z2", rpe = 4),
    ) = DailyPrescription(
        date = date,
        plannedSessionId = "2026-11-02-$date",
        plannedType = WorkoutType.EASY,
        plannedTitle = "Fácil e conversável",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.EASY,
        title = "Fácil e conversável",
        prescribedDurationMin = 40,
        targets = targets,
        adjustment = adjustment,
        rationale = "Plano mantido.",
        ruleIds = listOf("R-ZONE-01", "R-PRESC-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.MEDIUM,
        engineVersion = "daily-prescription-v1",
        inputsHash = hash,
    )
}
