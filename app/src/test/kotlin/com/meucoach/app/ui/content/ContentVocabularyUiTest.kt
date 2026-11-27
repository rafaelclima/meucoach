package com.meucoach.app.ui.content

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.meucoach.app.coach.CoachNextContext
import com.meucoach.app.ui.coach.CoachCheckInLabels
import com.meucoach.app.ui.coach.CoachDoneLabels
import com.meucoach.app.ui.coach.CoachFallbackLabels
import com.meucoach.app.ui.coach.CoachImportLabels
import com.meucoach.app.ui.coach.CoachNextLabels
import com.meucoach.app.ui.coach.CoachOpeningLabels
import com.meucoach.app.ui.coach.CoachProgressLabels
import com.meucoach.app.ui.common.RacePredictionBlock
import com.meucoach.app.ui.common.readinessLabel
import com.meucoach.app.ui.common.readinessLabelLowercase
import com.meucoach.app.ui.profile.ageLabel
import com.meucoach.app.ui.profile.availabilityDayLabel
import com.meucoach.app.ui.profile.availabilityLabel
import com.meucoach.app.ui.profile.experienceLabel
import com.meucoach.app.ui.profile.goalLabel
import com.meucoach.app.ui.profile.preferredTrainingTimeLabel
import com.meucoach.app.ui.profile.sexLabel
import com.meucoach.app.ui.profile.targetDateLabel
import com.meucoach.app.ui.profile.targetTimeLabel
import com.meucoach.app.ui.review.WeeklyReviewFormat
import com.meucoach.core.designsystem.theme.McTheme
import com.meucoach.core.domain.coach.CoachNextSubject
import com.meucoach.core.domain.coach.CoachProgressSummary
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityBlockReason
import com.meucoach.core.domain.prescription.SessionStatus
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.race.RacePrediction
import com.meucoach.core.domain.race.RacePredictionResult
import com.meucoach.core.domain.race.RacePredictionUnavailable
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.review.WeeklyAdherence
import com.meucoach.core.domain.review.WeeklyQuality
import com.meucoach.core.domain.review.WeeklyReadiness
import com.meucoach.core.domain.review.WeeklyReview
import com.meucoach.core.domain.review.WeeklyReviewObservation
import com.meucoach.core.domain.review.WeeklyReviewObservationKind
import com.meucoach.core.domain.review.WeeklyReviewStatus
import com.meucoach.core.domain.review.WeeklySleep
import com.meucoach.core.domain.review.WeeklyVolume
import com.meucoach.core.domain.trends.WeeklyIntensity
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.CompletionEvidence
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.GoalType
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.RaceEvent
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.TrainingGoal
import com.meucoach.core.model.VdotSource
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate

/**
 * Vocabulary gate over the user texts owned by the UI layer (ADR 0071 F1).
 *
 * The domain gate owns every text the engine produces; this test owns the
 * literals the app adds around them: `strings.xml`, the label formatters and
 * the prediction/review blocks the goldens never render. The assertions pin
 * stable facts only (counts, non-emptiness) — never the copy itself, which
 * fatias F6–F8 will rewrite. [UiVocabularyGate] fails on any forbidden term
 * missing from the baseline and on any stale entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ContentVocabularyUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `strings xml stays clean`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val texts = com.meucoach.app.R.string::class.java.declaredFields
            .filter { it.type == Int::class.javaPrimitiveType }
            .map { field ->
                val id = field.getInt(null)
                val name = context.resources.getResourceEntryName(id)
                CheckedText("$STRINGS [string $name]", context.getString(id))
            }
        assertThat(texts).isNotEmpty()
        UiVocabularyGate.assertClean(texts, STRINGS)
    }

    @Test
    fun `readiness labels stay clean`() {
        val texts = ReadinessLevel.entries.flatMap { level ->
            listOf(
                CheckedText("$READINESS_LABELS [readinessLabel $level]", readinessLabel(level)),
                CheckedText("$READINESS_LABELS [readinessLabelLowercase $level]", readinessLabelLowercase(level)),
            )
        }
        assertThat(texts).hasSize(ReadinessLevel.entries.size * 2)
        UiVocabularyGate.assertClean(texts, READINESS_LABELS)
    }

    @Test
    fun `profile labels stay clean`() {
        val texts = mutableListOf<CheckedText>()
        Sex.entries.forEach { texts += CheckedText("$PROFILE_LABELS [sexLabel $it]", sexLabel(it)) }
        Experience.entries.forEach {
            texts += CheckedText("$PROFILE_LABELS [experienceLabel $it]", experienceLabel(it))
        }
        GoalType.entries.forEach { texts += CheckedText("$PROFILE_LABELS [goalLabel $it]", goalLabel(it)) }
        texts += CheckedText("$PROFILE_LABELS [ageLabel]", ageLabel(profile(), TODAY))
        texts += CheckedText("$PROFILE_LABELS [targetDateLabel dated]", targetDateLabel(datedProfile()))
        texts += CheckedText("$PROFILE_LABELS [targetDateLabel dateless]", targetDateLabel(profile()))
        texts += CheckedText("$PROFILE_LABELS [targetTimeLabel null]", targetTimeLabel(null))
        texts += CheckedText("$PROFILE_LABELS [targetTimeLabel short]", targetTimeLabel(3720))
        texts += CheckedText("$PROFILE_LABELS [targetTimeLabel long]", targetTimeLabel(360))
        texts += CheckedText("$PROFILE_LABELS [preferredTrainingTimeLabel null]", preferredTrainingTimeLabel(profile()))
        texts += CheckedText(
            "$PROFILE_LABELS [preferredTrainingTimeLabel set]",
            preferredTrainingTimeLabel(profile().copy(preferredTrainingTimeMinutes = 300)),
        )
        texts += CheckedText("$PROFILE_LABELS [availabilityLabel empty]", availabilityLabel(profile()))
        texts += CheckedText(
            "$PROFILE_LABELS [availabilityLabel set]",
            availabilityLabel(
                profile().copy(
                    weeklyAvailability = WeeklyAvailability(
                        mapOf(DayOfWeek.MONDAY to 40, DayOfWeek.WEDNESDAY to 40),
                    ),
                ),
            ),
        )
        texts += CheckedText("$PROFILE_LABELS [goalLabel profile]", goalLabel(datedProfile()))
        texts += CheckedText("$PROFILE_LABELS [availabilityDayLabel]", availabilityDayLabel(DayOfWeek.MONDAY))
        UiVocabularyGate.assertClean(texts, PROFILE_LABELS)
    }

    @Test
    fun `coach next labels stay clean`() {
        val texts = mutableListOf<CheckedText>()
        fun add(label: String, value: String?) {
            if (value != null) texts += CheckedText("$COACH_NEXT_LABELS [$label]", value)
        }
        add("headline null", CoachNextLabels.headline(context(subject = null)))
        add("statusLine null", CoachNextLabels.statusLine(context(subject = null)))
        add("headline raceToday", CoachNextLabels.headline(context(subject = race(TODAY))))
        add("statusLine raceToday", CoachNextLabels.statusLine(context(subject = race(TODAY))))
        add("headline raceTomorrow", CoachNextLabels.headline(context(subject = race(TODAY.plusDays(1)))))
        add("statusLine raceLater", CoachNextLabels.statusLine(context(subject = race(TODAY.plusDays(10)))))
        add("headline trainingFuture", CoachNextLabels.headline(context(subject = training(TODAY.plusDays(2)))))
        add("statusLine trainingFuture", CoachNextLabels.statusLine(context(subject = training(TODAY.plusDays(2)))))
        add(
            "statusLine trainingFutureSoon",
            CoachNextLabels.statusLine(context(subject = training(TODAY.plusDays(1)))),
        )
        add("headline planned", CoachNextLabels.headline(context(subject = training(TODAY), prescription = null)))
        add("plannedLine kept", CoachNextLabels.plannedLine(context(subject = training(TODAY), prescription = kept())))
        add("statusLine kept", CoachNextLabels.statusLine(context(subject = training(TODAY), prescription = kept())))
        PrescriptionAdjustment.entries.forEach { adjustment ->
            val prescription = kept().copy(adjustment = adjustment)
            add(
                "statusLine $adjustment",
                CoachNextLabels.statusLine(context(subject = training(TODAY), prescription = prescription)),
            )
        }
        QualityBlockReason.entries.forEach { reason ->
            add("blockCopy $reason", CoachNextLabels.blockCopy(reason))
            add("unlockCopy $reason", CoachNextLabels.unlockCopy(reason))
            val prescription = kept().copy(
                adjustment = PrescriptionAdjustment.DOWNGRADE,
                prescribedType = WorkoutType.EASY,
                qualityBlockReason = reason,
                decisionDetail = "detalhe",
                unlockHint = "dica",
            )
            add(
                "actionLine $reason",
                CoachNextLabels.actionLine(context(subject = training(TODAY), prescription = prescription)),
            )
            add(
                "statusLine $reason",
                CoachNextLabels.statusLine(context(subject = training(TODAY), prescription = prescription)),
            )
        }
        add("readinessLine absent", CoachNextLabels.readinessLine(context(subject = training(TODAY))))
        add(
            "readinessLine unmeasured",
            CoachNextLabels.readinessLine(
                context(
                    subject = training(TODAY),
                    readiness = readiness(flags = setOf(ReadinessFlag.INSUFFICIENT_DATA)),
                ),
            ),
        )
        add(
            "readinessLine full",
            CoachNextLabels.readinessLine(
                context(
                    subject = training(TODAY),
                    readiness = readiness(flags = setOf(ReadinessFlag.SHORT_SLEEP)),
                    hasCheckIn = false,
                    sleepMinutes = 300,
                ),
            ),
        )
        add(
            "readinessLine future",
            CoachNextLabels.readinessLine(
                context(subject = training(TODAY.plusDays(1)), readiness = readiness()),
            ),
        )
        assertThat(CoachNextLabels.readinessLevel(context(subject = training(TODAY)))).isNull()
        UiVocabularyGate.assertClean(texts, COACH_NEXT_LABELS)
    }

    @Test
    fun `race prediction block stays clean`() {
        val origin = "$RACE_PREDICTION_BLOCK"
        val high = RacePrediction(
            distance = RaceDistance.FIVE_K,
            predictedSec = 1_859,
            rangeSec = 1_766..1_952,
            confidence = DataConfidence.HIGH,
            usedRiegel = true,
        )
        val medium = high.copy(confidence = DataConfidence.MEDIUM, usedRiegel = false)
        val cases = listOf(
            "$origin [available ambitious]" to Pair(RacePredictionResult.Available(high), 1_600),
            "$origin [available aligned]" to Pair(RacePredictionResult.Available(high), 1_859),
            "$origin [available conservative]" to Pair(RacePredictionResult.Available(medium), 2_000),
            "$origin [available noGoal]" to Pair(RacePredictionResult.Available(medium), null),
        ) + RacePredictionUnavailable.entries.map { reason ->
            "$origin [unavailable $reason]" to Pair(RacePredictionResult.Unavailable(reason), null)
        }
        var current by mutableStateOf(0)
        compose.setContent {
            McTheme {
                val (result, goalSec) = cases[current].second
                RacePredictionBlock(result = result, goalSec = goalSec)
            }
        }
        cases.forEachIndexed { index, (from, _) ->
            compose.runOnIdle { current = index }
            compose.waitForIdle()
            collectRendered(from)
        }
        UiVocabularyGate.assertClean(
            renderedTexts.map { (from, text) ->
                CheckedText(from, text)
            },
            RACE_PREDICTION_BLOCK,
        )
        renderedTexts.clear()
    }

    @Test
    fun `weekly review format stays clean`() {
        val texts = mutableListOf<CheckedText>()
        val review = review()
        texts += CheckedText("$WEEKLY_REVIEW_FORMAT [dateRange]", WeeklyReviewFormat.dateRange(review))
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [adherenceHeadline]",
            WeeklyReviewFormat.adherenceHeadline(review.adherence!!),
        )
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [adherencePercent]",
            WeeklyReviewFormat.adherencePercent(review.adherence!!),
        )
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [adherenceDetail]",
            WeeklyReviewFormat.adherenceDetail(review.adherence!!),
        )
        texts += CheckedText("$WEEKLY_REVIEW_FORMAT [volume]", WeeklyReviewFormat.volume(100))
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [volumePercent]",
            WeeklyReviewFormat.volumePercent(review.volume!!),
        )
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [qualityLine kept]",
            WeeklyReviewFormat.qualityLine(
                WeeklyQuality(planned = 1, kept = 1, downgraded = 0, firstDowngradeDetail = null),
            ),
        )
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [qualityLine downgraded]",
            WeeklyReviewFormat.qualityLine(
                WeeklyQuality(planned = 2, kept = 1, downgraded = 1, firstDowngradeDetail = "check-in de hoje ausente"),
            ),
        )
        texts += CheckedText("$WEEKLY_REVIEW_FORMAT [volumeExtra]", WeeklyReviewFormat.volumeExtra(28))
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [readinessValue]",
            WeeklyReviewFormat.readinessValue(review.readiness!!),
        )
        texts += CheckedText(
            "$WEEKLY_REVIEW_FORMAT [readinessHint]",
            WeeklyReviewFormat.readinessHint(review.readiness!!),
        )
        texts += CheckedText("$WEEKLY_REVIEW_FORMAT [sleepValue]", WeeklyReviewFormat.sleepValue(review.sleep!!))
        texts += CheckedText("$WEEKLY_REVIEW_FORMAT [sleepHint]", WeeklyReviewFormat.sleepHint(review.sleep!!))
        texts += CheckedText("$WEEKLY_REVIEW_FORMAT [minutes]", WeeklyReviewFormat.minutes(91))
        texts += CheckedText("$WEEKLY_REVIEW_FORMAT [emptyDescription]", WeeklyReviewFormat.emptyDescription())
        WeeklyReviewObservationKind.entries.forEach { kind ->
            texts += CheckedText(
                "$WEEKLY_REVIEW_FORMAT [observation $kind]",
                WeeklyReviewFormat.observationText(reviewFor(kind), observation(kind)),
            )
        }
        UiVocabularyGate.assertClean(texts, WEEKLY_REVIEW_FORMAT)
    }

    @Test
    fun `coach voice labels stay clean`() {
        val texts = mutableListOf<CheckedText>()
        listOf(
            "codigo" to "código interno",
            "metrica" to "métrica proibida",
            "pace" to "quis falar de pace aqui",
            "fc" to "quis falar de fc aqui",
            "duracao" to "duração não planejada aqui",
            "porcentagem" to "porcentagem não calculada aqui",
            "numero" to "número fora do contexto aqui",
            "distancia" to "distância fora do plano aqui",
            "escopo" to "escopo do coach aqui",
            "longa" to "longa demais aqui",
            "desconhecido" to "algo nunca visto",
            "offline" to "Sem conexão aqui — te mostro o plano direto do app.",
            "direto" to "Me enrolei aqui — te mostro o plano direto.",
        ).forEach { (slug, reason) ->
            texts +=
                CheckedText(
                    "$COACH_FALLBACK_LABELS [freeAnswerReasonCopy $slug]",
                    CoachFallbackLabels.freeAnswerReasonCopy(reason),
                )
            texts += CheckedText("$COACH_FALLBACK_LABELS [reasonCopy $slug]", CoachFallbackLabels.reasonCopy(reason))
        }
        listOf(6, 13, 20).forEach { hour ->
            texts += CheckedText("$COACH_OPENING_LABELS [greeting $hour]", CoachOpeningLabels.greeting(hour))
        }
        texts += CheckedText(
            "$COACH_OPENING_LABELS [opening raceToday]",
            CoachOpeningLabels.opening(context(subject = race(TODAY)), 9),
        )
        texts += CheckedText(
            "$COACH_OPENING_LABELS [opening raceTomorrow]",
            CoachOpeningLabels.opening(context(subject = race(TODAY.plusDays(1))), 9),
        )
        texts += CheckedText(
            "$COACH_OPENING_LABELS [opening raceLater]",
            CoachOpeningLabels.opening(context(subject = race(TODAY.plusDays(10))), 9),
        )
        texts += CheckedText(
            "$COACH_OPENING_LABELS [opening trainingFuture]",
            CoachOpeningLabels.opening(context(subject = training(TODAY.plusDays(3))), 9),
        )
        texts += CheckedText(
            "$COACH_OPENING_LABELS [opening missingCheckIn]",
            CoachOpeningLabels.opening(context(subject = training(TODAY)), 9),
        )
        texts += CheckedText(
            "$COACH_OPENING_LABELS [opening null]",
            CoachOpeningLabels.opening(context(subject = null), 9),
        )
        texts += CheckedText("$COACH_DONE_LABELS [systemLine]", CoachDoneLabels.SYSTEM_LINE)
        texts += CheckedText(
            "$COACH_DONE_LABELS [cheer]",
            CoachDoneLabels.cheer(context(subject = training(TODAY), todaySession = doneSession()))!!,
        )
        texts += CheckedText("$COACH_CHECKIN_LABELS [systemLine]", CoachCheckInLabels.SYSTEM_LINE)
        texts += CheckedText(
            "$COACH_CHECKIN_LABELS [ack kept]",
            CoachCheckInLabels.ack(
                context(subject = training(TODAY), hasCheckIn = true, prescription = kept()),
            )!!,
        )
        texts += CheckedText(
            "$COACH_CHECKIN_LABELS [ack downgraded]",
            CoachCheckInLabels.ack(
                context(
                    subject = training(TODAY),
                    hasCheckIn = true,
                    prescription = kept().copy(
                        adjustment = PrescriptionAdjustment.DOWNGRADE,
                        prescribedType = WorkoutType.EASY,
                        qualityBlockReason = QualityBlockReason.SHORT_SLEEP,
                        decisionDetail = "detalhe",
                        unlockHint = "dica",
                    ),
                ),
            )!!,
        )
        texts += CheckedText(
            "$COACH_CHECKIN_LABELS [ack noPrescription]",
            CoachCheckInLabels.ack(context(subject = training(TODAY), hasCheckIn = true))!!,
        )
        texts += CheckedText("$COACH_IMPORT_LABELS [systemLine]", CoachImportLabels.SYSTEM_LINE)
        texts += CheckedText(
            "$COACH_IMPORT_LABELS [ack]",
            CoachImportLabels.ack(context(subject = training(TODAY), importedToday = true, hasPlan = true))!!,
        )
        val summary = CoachProgressSummary(
            availableWeeks = 4,
            plannedSessions = 12,
            doneSessions = 9,
            adherencePercent = 75,
            plannedMinutes = 400,
            executedMinutes = 350,
            extraMinutes = 28,
            volumePercent = 88,
            avgReadiness = 72,
            redDays = 2,
            avgSleepMinutes = 430,
            shortNights = 1,
            easyPercent28d = 82,
        )
        texts += CheckedText("$COACH_PROGRESS_LABELS [coverageNote]", CoachProgressLabels.coverageNote(3, 4))
        texts += CheckedText("$COACH_PROGRESS_LABELS [adherenceLine]", CoachProgressLabels.adherenceLine(summary))
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [adherenceLine empty]",
            CoachProgressLabels.adherenceLine(summary.copy(adherencePercent = null)),
        )
        texts += CheckedText("$COACH_PROGRESS_LABELS [volumeLine]", CoachProgressLabels.volumeLine(summary))
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [volumeLine empty]",
            CoachProgressLabels.volumeLine(summary.copy(volumePercent = null)),
        )
        texts += CheckedText("$COACH_PROGRESS_LABELS [extraLine]", CoachProgressLabels.extraLine(summary))
        texts += CheckedText("$COACH_PROGRESS_LABELS [intensityLine]", CoachProgressLabels.intensityLine(summary))
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [intensityLine empty]",
            CoachProgressLabels.intensityLine(summary.copy(easyPercent28d = null)),
        )
        texts += CheckedText("$COACH_PROGRESS_LABELS [readinessLine]", CoachProgressLabels.readinessLine(summary))
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [readinessLine empty]",
            CoachProgressLabels.readinessLine(summary.copy(avgReadiness = null)),
        )
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [readinessLine clear]",
            CoachProgressLabels.readinessLine(summary.copy(redDays = 0)),
        )
        texts += CheckedText("$COACH_PROGRESS_LABELS [sleepLine]", CoachProgressLabels.sleepLine(summary))
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [sleepLine empty]",
            CoachProgressLabels.sleepLine(summary.copy(avgSleepMinutes = null)),
        )
        texts += CheckedText("$COACH_PROGRESS_LABELS [vdotLine absent]", CoachProgressLabels.vdotLine(null, TODAY))
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [vdotLine stale]",
            CoachProgressLabels.vdotLine(vdotProfile(calibratedAt = TODAY.minusDays(100)), TODAY),
        )
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [vdotLine proof]",
            CoachProgressLabels.vdotLine(vdotProfile(source = VdotSource.PERFORMANCE), TODAY),
        )
        texts += CheckedText(
            "$COACH_PROGRESS_LABELS [vdotLine estimate]",
            CoachProgressLabels.vdotLine(vdotProfile(source = VdotSource.TRAINING_ESTIMATE), TODAY),
        )
        fun scoped(scope: String) = texts.filter { it.origin.startsWith(scope) }
        UiVocabularyGate.assertClean(scoped(COACH_FALLBACK_LABELS), COACH_FALLBACK_LABELS)
        UiVocabularyGate.assertClean(scoped(COACH_OPENING_LABELS), COACH_OPENING_LABELS)
        UiVocabularyGate.assertClean(scoped(COACH_DONE_LABELS), COACH_DONE_LABELS)
        UiVocabularyGate.assertClean(scoped(COACH_CHECKIN_LABELS), COACH_CHECKIN_LABELS)
        UiVocabularyGate.assertClean(scoped(COACH_IMPORT_LABELS), COACH_IMPORT_LABELS)
        UiVocabularyGate.assertClean(scoped(COACH_PROGRESS_LABELS), COACH_PROGRESS_LABELS)
    }

    private val renderedTexts = mutableListOf<Pair<String, String>>()

    private fun renderForGate(origin: String, result: RacePredictionResult, goalSec: Int?) {
        compose.setContent {
            McTheme {
                RacePredictionBlock(result = result, goalSec = goalSec)
            }
        }
        collectRendered(origin)
    }

    private fun collectRendered(origin: String) {
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .forEach { node ->
                node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.forEach { text ->
                    renderedTexts += origin to text.text
                }
            }
    }

    private fun context(
        subject: CoachNextSubject?,
        prescription: DailyPrescription? = null,
        readiness: ReadinessResult? = null,
        hasCheckIn: Boolean = false,
        sleepMinutes: Int? = null,
        todaySession: PlannedSession? = null,
        importedToday: Boolean = false,
        hasPlan: Boolean = true,
    ) = CoachNextContext(
        subject = subject,
        today = TODAY,
        hasPlan = hasPlan,
        hasProfile = true,
        prescription = prescription,
        readiness = readiness,
        hasCheckIn = hasCheckIn,
        sleepMinutes = sleepMinutes,
        todaySession = todaySession,
        importedToday = importedToday,
    )

    private fun training(date: LocalDate) = CoachNextSubject.Training(
        PlannedSession(
            date = date,
            type = WorkoutType.EASY,
            title = "Rodagem",
            durationMin = 40,
            rationale = "Base aeróbica.",
            ruleIds = listOf("R-ZONE-01"),
            evidenceIds = listOf("EV-SEILER-2010"),
        ),
    )

    private fun race(date: LocalDate) = CoachNextSubject.Race(
        RaceEvent(
            id = "race-1",
            distance = RaceDistance.FIVE_K,
            targetDate = date,
            createdAt = Instant.parse("2026-10-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-10-01T00:00:00Z"),
        ),
    )

    private fun kept() = DailyPrescription(
        date = TODAY,
        plannedSessionId = "plan-1",
        plannedType = WorkoutType.QUALITY,
        plannedTitle = "Intervalado",
        plannedDurationMin = 40,
        prescribedType = WorkoutType.QUALITY,
        title = "Intervalado",
        prescribedDurationMin = 40,
        targets = PrescriptionTargets(primary = TargetKind.RPE, rpe = 7),
        adjustment = PrescriptionAdjustment.KEEP,
        rationale = "Mantido como planejado.",
        ruleIds = listOf("R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        confidence = DataConfidence.HIGH,
        engineVersion = "test-v1",
        inputsHash = "hash",
    )

    private fun doneSession() = PlannedSession(
        date = TODAY,
        type = WorkoutType.EASY,
        title = "Rodagem",
        durationMin = 40,
        rationale = "Base aeróbica.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-SEILER-2010"),
        status = SessionStatus.DONE,
        completionEvidence = CompletionEvidence.MANUAL,
    )

    private fun readiness(flags: Set<ReadinessFlag> = emptySet()) = ReadinessResult(
        score = 80,
        level = ReadinessLevel.GREEN,
        confidence = DataConfidence.HIGH,
        components = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        missing = emptySet(),
        flags = flags,
    )

    private fun profile() = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(emptyMap()),
    )

    private fun datedProfile() = profile().copy(
        goal = TrainingGoal(
            type = GoalType.FIVE_K,
            targetDate = LocalDate.of(2026, 12, 20),
            targetTimeSec = 1_860,
        ),
    )

    private fun vdotProfile(
        source: VdotSource? = VdotSource.TRAINING_ESTIMATE,
        calibratedAt: LocalDate? = TODAY.minusDays(2),
    ) = profile().copy(
        vdot = 30.4,
        vdotSource = source,
        vdotCalibratedAt = calibratedAt,
        vdotReferenceDistanceM = if (source == VdotSource.PERFORMANCE) 5_000.0 else null,
        vdotReferenceDurationSec = if (source == VdotSource.PERFORMANCE) 1_859 else null,
    )

    private fun review() = WeeklyReview(
        weekStart = TODAY.minusDays(7),
        weekEnd = TODAY.minusDays(1),
        status = WeeklyReviewStatus.AVAILABLE,
        adherence = WeeklyAdherence(planned = 4, done = 2, verified = 1, manual = 1, legacy = 0, skipped = 2),
        volume = WeeklyVolume(plannedMin = 150, executedMin = 100, percentOfPlan = 67),
        intensity = WeeklyIntensity(
            weekStart = TODAY.minusDays(7),
            easySec = 3_600,
            moderateSec = 1_800,
            hardSec = 1_800,
        ),
        intensitySessions = 4,
        readiness = WeeklyReadiness(measuredDays = 5, averageScore = 48, redDays = 3),
        sleep = WeeklySleep(measuredNights = 6, averageMinutes = 451, shortNights = 3),
        observations = emptyList(),
        ruleIds = listOf("R-REVIEW-01"),
        evidenceIds = emptyList(),
        confidence = DataConfidence.MEDIUM,
    )

    private fun reviewFor(kind: WeeklyReviewObservationKind) = when (kind) {
        WeeklyReviewObservationKind.VOLUME_ABOVE_PLAN ->
            review().copy(volume = WeeklyVolume(plannedMin = 150, executedMin = 180, percentOfPlan = 120))

        WeeklyReviewObservationKind.ADHERENCE_PARTIAL ->
            review().copy(
                adherence = WeeklyAdherence(planned = 4, done = 2, verified = 2, manual = 0, legacy = 0, skipped = 2),
            )

        WeeklyReviewObservationKind.ADHERENCE_NONE ->
            review().copy(
                adherence = WeeklyAdherence(planned = 4, done = 0, verified = 0, manual = 0, legacy = 0, skipped = 4),
            )

        else -> review()
    }

    private fun observation(kind: WeeklyReviewObservationKind) = WeeklyReviewObservation(
        kind = kind,
        ruleId = "R-REVIEW-01",
        evidenceIds = emptyList(),
        confidence = DataConfidence.MEDIUM,
    )

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 11, 2)
        const val STRINGS = "app/src/main/res/values/strings.xml"
        const val READINESS_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/common/ReadinessLabels.kt"
        const val PROFILE_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/profile/ProfileLabels.kt"
        const val COACH_NEXT_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/coach/CoachNextLabels.kt"
        const val RACE_PREDICTION_BLOCK = "app/src/main/kotlin/com/meucoach/app/ui/common/RacePredictionBlock.kt"
        const val WEEKLY_REVIEW_FORMAT = "app/src/main/kotlin/com/meucoach/app/ui/review/WeeklyReviewFormat.kt"
        const val COACH_FALLBACK_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/coach/CoachFallbackLabels.kt"
        const val COACH_OPENING_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/coach/CoachOpeningLabels.kt"
        const val COACH_DONE_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/coach/CoachDoneLabels.kt"
        const val COACH_CHECKIN_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/coach/CoachCheckInLabels.kt"
        const val COACH_IMPORT_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/coach/CoachImportLabels.kt"
        const val COACH_PROGRESS_LABELS = "app/src/main/kotlin/com/meucoach/app/ui/coach/CoachProgressLabels.kt"
    }
}
