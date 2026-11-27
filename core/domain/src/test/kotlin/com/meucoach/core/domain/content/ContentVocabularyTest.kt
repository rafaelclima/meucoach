package com.meucoach.core.domain.content

import com.meucoach.core.domain.briefing.SessionBriefingEngine
import com.meucoach.core.domain.calibration.CalibrationCalculator
import com.meucoach.core.domain.load.LoadCoverage
import com.meucoach.core.domain.load.LoadDataState
import com.meucoach.core.domain.load.LoadSnapshot
import com.meucoach.core.domain.prescription.BlockKind
import com.meucoach.core.domain.prescription.ColdStartAllowance
import com.meucoach.core.domain.prescription.CompletedRace
import com.meucoach.core.domain.prescription.DailyPrescription
import com.meucoach.core.domain.prescription.ExplanationCatalog
import com.meucoach.core.domain.prescription.GoalAlignment
import com.meucoach.core.domain.prescription.HeartRateTarget
import com.meucoach.core.domain.prescription.MesoPhase
import com.meucoach.core.domain.prescription.PaceTarget
import com.meucoach.core.domain.prescription.PlanDecision
import com.meucoach.core.domain.prescription.PlanDecisionReason
import com.meucoach.core.domain.prescription.PlannedSession
import com.meucoach.core.domain.prescription.PrescriptionAdjustment
import com.meucoach.core.domain.prescription.PrescriptionInput
import com.meucoach.core.domain.prescription.PrescriptionTargets
import com.meucoach.core.domain.prescription.QualityGate
import com.meucoach.core.domain.prescription.QualityKind
import com.meucoach.core.domain.prescription.RacePaceResolver
import com.meucoach.core.domain.prescription.RacePlan
import com.meucoach.core.domain.prescription.RecoveryWindow
import com.meucoach.core.domain.prescription.SessionLibrary
import com.meucoach.core.domain.prescription.TargetKind
import com.meucoach.core.domain.prescription.WorkoutBlock
import com.meucoach.core.domain.prescription.WorkoutRecommender
import com.meucoach.core.domain.prescription.WorkoutStructure
import com.meucoach.core.domain.prescription.WorkoutType
import com.meucoach.core.domain.race.RacePredictionResult
import com.meucoach.core.domain.race.RaceTimePredictor
import com.meucoach.core.domain.readiness.ReadinessComponent
import com.meucoach.core.domain.readiness.ReadinessFlag
import com.meucoach.core.domain.readiness.ReadinessResult
import com.meucoach.core.domain.story.StoryVerdictCalculator
import com.meucoach.core.domain.story.StoryVerdictTone
import com.meucoach.core.domain.zones.HrZoneDistribution
import com.meucoach.core.domain.zones.HrZoneDuration
import com.meucoach.core.model.AthleteProfile
import com.meucoach.core.model.DataConfidence
import com.meucoach.core.model.Experience
import com.meucoach.core.model.HeartRateZone
import com.meucoach.core.model.HeatLevel
import com.meucoach.core.model.Pace
import com.meucoach.core.model.RaceDistance
import com.meucoach.core.model.ReadinessLevel
import com.meucoach.core.model.Sex
import com.meucoach.core.model.VdotSource
import com.meucoach.core.model.WeeklyAvailability
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Vocabulary gate over the user texts the plan goldens never reach
 * (ADR 0071 F1).
 *
 * Golden fixtures freeze one generated week; the texts below come from the
 * catalogs and the day-level engines directly, so every branch that emits
 * user text is enumerated here. Tones `calculate` can never return
 * (QUALITY too hard, LONG too easy, anything on REST) are dead text fixed
 * wholesale in F5; everything reachable is scanned. The assertions pin stable facts only (enums,
 * rule IDs, non-emptiness) — never the copy itself, which fatias F2–F5 will
 * rewrite. [VocabularyGate] fails on any forbidden term missing from the
 * baseline and on any baseline entry that no longer matches.
 */
class ContentVocabularyTest {
    @Test
    fun `explanation catalog titles stay clean`() {
        val texts = mutableListOf<CheckedText>()
        assertThat(ExplanationCatalog.hasRule("R-CAL-01")).isTrue()
        assertThat(ExplanationCatalog.hasEvidence("EV-SEILER-2010")).isTrue()
        ExplanationCatalog.allRules().forEach { rule ->
            texts += CheckedText("$CATALOG [rule ${rule.id} title]", rule.title)
        }
        ExplanationCatalog.allEvidence().forEach { evidence ->
            texts += CheckedText("$CATALOG [evidence ${evidence.id} title]", evidence.title)
        }
        VocabularyGate.assertClean(texts, CATALOG)
    }

    @Test
    fun `quality gate details and unlock hints stay clean`() {
        VocabularyGate.assertClean(qualityGateTexts(), QUALITY_GATE)
    }

    @Test
    fun `session library titles and blurbs stay clean`() {
        val seen = mutableSetOf<Pair<String, String>>()
        val texts = mutableListOf<CheckedText>()
        for (phase in MesoPhase.entries) {
            for (level in Experience.entries) {
                val easy = SessionLibrary.easyTemplates(phase, level, includeIntroFartlek = true) +
                    SessionLibrary.easyTemplates(phase, level)
                val qualities = RaceDistance.entries.flatMap { distance ->
                    SessionLibrary.qualityTemplates(phase, level, distance)
                } + SessionLibrary.qualityTemplates(phase, level, null)
                val rotation = RaceDistance.entries.flatMap { distance ->
                    (0..3).flatMap { week ->
                        SessionLibrary.qualityRotation(phase, level, distance, week, 1, 5) +
                            SessionLibrary.qualityRotation(phase, level, distance, week, 2, 5)
                    }
                }
                (easy + qualities + rotation).forEach { template ->
                    if (seen.add(template.title to template.blurb)) {
                        val origin = "$SESSION_LIBRARY [template \"${template.title}\"]"
                        texts += CheckedText(origin, template.title)
                        texts += CheckedText(origin, template.blurb)
                    }
                }
            }
        }
        assertThat(texts).isNotEmpty()
        VocabularyGate.assertClean(texts, SESSION_LIBRARY)
    }

    @Test
    fun `briefing guidance and steps stay clean`() {
        VocabularyGate.assertClean(briefingTexts(), BRIEFING)
    }

    @Test
    fun `story verdict lines stay clean`() {
        VocabularyGate.assertClean(verdictTexts(), VERDICT)
    }

    @Test
    fun `race pace notes stay clean`() {
        VocabularyGate.assertClean(racePaceTexts(), RACE_PACE)
    }

    @Test
    fun `workout recommender titles and rationales stay clean`() {
        VocabularyGate.assertClean(recommenderTexts(), RECOMMENDER)
    }

    private fun qualityGateTexts(): List<CheckedText> {
        val texts = mutableListOf<CheckedText>()
        fun collect(label: String, evaluate: () -> com.meucoach.core.domain.prescription.QualityEligibility) {
            val eligibility = evaluate()
            assertThat(eligibility.reason).describedAs(label).isNotNull()
            eligibility.detail?.let { texts += CheckedText("$QUALITY_GATE [$label detail]", it) }
            eligibility.unlockHint?.let { texts += CheckedText("$QUALITY_GATE [$label unlockHint]", it) }
        }
        collect("illness") { gate(readiness = readiness(flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY))) }
        collect("red") { gate(readiness = readiness(level = ReadinessLevel.RED, score = 20)) }
        collect("beginner") {
            gate(profile = profile(experience = Experience.BEGINNER), readiness = readiness(flags = emptySet()))
        }
        collect("subjectiveMissing") { gate(readiness = readiness(components = emptyMap())) }
        collect("shortSleep") { gate(readiness = readiness(flags = setOf(ReadinessFlag.SHORT_SLEEP))) }
        collect("lowReadiness") { gate(readiness = readiness(level = ReadinessLevel.AMBER, score = 55)) }
        collect("postRace") { gate(recovery = recovery()) }
        collect("raceWeek") { gate(phase = MesoPhase.TAPER, raceWeek = true) }
        collect("recoveryWeek") { gate(phase = MesoPhase.RECOVERY) }
        collect("taper") { gate(phase = MesoPhase.TAPER) }
        collect("loadMissing") { gate(load = null) }
        collect("loadStale") { gate(load = load(sourceFresh = false)) }
        collect("loadInsufficient") { gate(load = load(daysWithLoad = 7)) }
        collect("monotonyHigh") { gate(load = load(monotony = 2.1)) }
        collect("monotonyUnknown") { gate(load = load(monotony = null)) }
        collect("strainSpike") { gate(load = load(strain = 1_500.0, chronicAverage28d = 100.0)) }
        collect("acwrHigh") { gate(load = load(acwr = 1.6)) }
        val coldStart = gate(
            plannedSession = session(durationMin = 25),
            readiness = readiness(),
            load = null,
            profile = profile(),
            coldStart = ColdStartAllowance(
                vdot = FIVE_K_VDOT,
                vdotSource = VdotSource.TRAINING_ESTIMATE,
                calibratedAt = MONDAY.minusDays(2),
                today = MONDAY,
            ),
        )
        assertThat(coldStart.eligible).isTrue()
        coldStart.detail?.let { texts += CheckedText("$QUALITY_GATE [coldStart detail]", it) }
        return texts
    }

    private fun briefingTexts(): List<CheckedText> {
        val engine = SessionBriefingEngine()
        val texts = mutableListOf<CheckedText>()
        fun collect(label: String, prescription: DailyPrescription) {
            val briefing = engine.brief(prescription)
            assertThat(briefing).describedAs(label).isNotNull()
            val shown = requireNotNull(briefing) { "Briefing must exist for $label" }
            texts += CheckedText("$BRIEFING [$label howToRun]", shown.howToRun)
            texts += CheckedText("$BRIEFING [$label ifHard]", shown.ifHard)
            shown.steps.forEachIndexed { index, step ->
                texts += CheckedText("$BRIEFING [$label step$index]", step)
            }
        }
        collect("restStop", prescription(type = WorkoutType.REST, ruleIds = listOf("R-SAFE-01")))
        collect("rest", prescription(type = WorkoutType.REST))
        collect(
            "easyHeartRate",
            prescription(
                type = WorkoutType.EASY,
                targets = PrescriptionTargets(
                    heartRate = HeartRateTarget(minBpm = 140, maxBpm = 155),
                    primary = TargetKind.HEART_RATE,
                ),
            ),
        )
        collect(
            "easyHeartRatePaceRef",
            prescription(
                type = WorkoutType.EASY,
                targets = PrescriptionTargets(
                    pace = PaceTarget(Pace.fromSecondsPerKm(360), Pace.fromSecondsPerKm(390)),
                    heartRate = HeartRateTarget(minBpm = 140, maxBpm = 155),
                    primary = TargetKind.HEART_RATE,
                ),
            ),
        )
        collect(
            "qualityPace",
            prescription(
                type = WorkoutType.QUALITY,
                structure = workStructure(),
                targets = PrescriptionTargets(
                    pace = PaceTarget(Pace.fromSecondsPerKm(300), Pace.fromSecondsPerKm(320)),
                    heartRate = HeartRateTarget(minBpm = 150, maxBpm = 172),
                    primary = TargetKind.PACE,
                ),
            ),
        )
        collect(
            "qualityPaceNoCeil",
            prescription(
                type = WorkoutType.QUALITY,
                structure = workStructure(),
                targets = PrescriptionTargets(
                    pace = PaceTarget(Pace.fromSecondsPerKm(300), Pace.fromSecondsPerKm(320)),
                    heartRate = HeartRateTarget(minBpm = 150, maxBpm = null),
                    primary = TargetKind.PACE,
                ),
            ),
        )
        collect(
            "longMixedNoMax",
            prescription(
                type = WorkoutType.LONG,
                targets = PrescriptionTargets(
                    heartRate = HeartRateTarget(minBpm = 135, maxBpm = null),
                    primary = TargetKind.MIXED,
                ),
            ),
        )
        collect(
            "easyRpePaceRef",
            prescription(
                type = WorkoutType.EASY,
                targets = PrescriptionTargets(
                    pace = PaceTarget(Pace.fromSecondsPerKm(360), Pace.fromSecondsPerKm(390)),
                    rpe = 6,
                    primary = TargetKind.RPE,
                ),
            ),
        )
        collect(
            "easyRpe",
            prescription(
                type = WorkoutType.EASY,
                targets = PrescriptionTargets(rpe = 6, primary = TargetKind.RPE),
            ),
        )
        collect(
            "stridesSteps",
            prescription(type = WorkoutType.EASY, structure = stridesStructure()),
        )
        collect(
            "racePaceSteps",
            prescription(type = WorkoutType.QUALITY, structure = racePaceStructure()),
        )
        collect(
            "recoverySteps",
            prescription(type = WorkoutType.EASY, structure = recoveryStructure()),
        )
        return texts
    }

    private fun verdictTexts(): List<CheckedText> {
        val texts = mutableListOf<CheckedText>()
        fun collect(
            label: String,
            tone: StoryVerdictTone?,
            calculate: () -> com.meucoach.core.domain.story.StoryVerdict,
        ) {
            val verdict = calculate()
            assertThat(verdict.tone).describedAs(label).isEqualTo(tone)
            texts += CheckedText("$VERDICT [$label headline]", verdict.headline)
            texts += CheckedText("$VERDICT [$label coachLine]", verdict.coachLine)
            verdict.proof?.let { texts += CheckedText("$VERDICT [$label proof]", it) }
        }
        collect("easyOnTarget", StoryVerdictTone.ON_TARGET) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.EASY),
                distribution = distribution(z1 = 100, z2 = 900, z3 = 0, z4 = 0, z5 = 0),
                lthrBpm = LTHR,
                nextSession = verdictSession(WorkoutType.EASY, date = MONDAY.plusDays(1)),
            )
        }
        collect("easyTooHard", StoryVerdictTone.TOO_HARD) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.EASY),
                distribution = distribution(z1 = 60, z2 = 140, z3 = 300, z4 = 400, z5 = 100),
                lthrBpm = LTHR,
            )
        }
        collect("easyTooEasy", StoryVerdictTone.TOO_EASY) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.EASY),
                distribution = distribution(z1 = 800, z2 = 200, z3 = 0, z4 = 0, z5 = 0),
                lthrBpm = LTHR,
            )
        }
        collect("longOnTarget", StoryVerdictTone.ON_TARGET) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.LONG),
                distribution = distribution(z1 = 400, z2 = 600, z3 = 0, z4 = 0, z5 = 0),
                lthrBpm = LTHR,
            )
        }
        collect("longTooHard", StoryVerdictTone.TOO_HARD) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.LONG),
                distribution = distribution(z1 = 0, z2 = 100, z3 = 100, z4 = 400, z5 = 400),
                lthrBpm = LTHR,
            )
        }
        collect("qualityOnTarget", StoryVerdictTone.ON_TARGET) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.QUALITY),
                distribution = distribution(z1 = 0, z2 = 100, z3 = 100, z4 = 700, z5 = 100),
                lthrBpm = LTHR,
            )
        }
        collect("qualityTooEasy", StoryVerdictTone.TOO_EASY) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.QUALITY),
                distribution = distribution(z1 = 700, z2 = 100, z3 = 100, z4 = 50, z5 = 50),
                lthrBpm = LTHR,
            )
        }
        collect("noTarget", null) {
            StoryVerdictCalculator.calculate(session = null, distribution = null, lthrBpm = null)
        }
        collect("noHeartRate", null) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.EASY),
                distribution = null,
                lthrBpm = LTHR,
            )
        }
        collect("nextLong", StoryVerdictTone.ON_TARGET) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.EASY),
                distribution = distribution(z1 = 0, z2 = 1000, z3 = 0, z4 = 0, z5 = 0),
                lthrBpm = LTHR,
                nextSession = verdictSession(WorkoutType.LONG, date = MONDAY.plusDays(3), durationMin = 60),
            )
        }
        collect("nextQuality", StoryVerdictTone.ON_TARGET) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.EASY),
                distribution = distribution(z1 = 0, z2 = 1000, z3 = 0, z4 = 0, z5 = 0),
                lthrBpm = LTHR,
                nextSession = verdictSession(WorkoutType.QUALITY, date = MONDAY.plusDays(1)),
            )
        }
        collect("nextRest", StoryVerdictTone.ON_TARGET) {
            StoryVerdictCalculator.calculate(
                session = verdictSession(WorkoutType.EASY),
                distribution = distribution(z1 = 0, z2 = 1000, z3 = 0, z4 = 0, z5 = 0),
                lthrBpm = LTHR,
                nextSession = verdictSession(WorkoutType.REST, date = MONDAY.plusDays(1), durationMin = null),
            )
        }
        return texts
    }

    private fun racePaceTexts(): List<CheckedText> {
        val texts = mutableListOf<CheckedText>()
        val race = RacePlan("race-1", RaceDistance.FIVE_K, MONDAY.plusDays(14))
        fun collect(label: String, goalSec: Int?, profile: AthleteProfile, alignment: GoalAlignment?) {
            val plan = RacePaceResolver.resolve(profile, race, goalSec, MONDAY)
            assertThat(plan.goalGap?.alignment).describedAs(label).isEqualTo(alignment)
            texts += CheckedText("$RACE_PACE [$label note]", plan.note)
            texts += CheckedText("$RACE_PACE [$label blockTarget]", plan.blockTarget)
        }
        val available = RaceTimePredictor.predict(raceProfile(), race.distance, MONDAY)
            as RacePredictionResult.Available
        val predictedSec = available.prediction.predictedSec
        assertThat(RacePaceResolver.pointPace(available.prediction).secondsPerKm).isPositive()
        collect("noGoal", null, raceProfile(), null)
        collect("ambitious", (predictedSec * 0.90).roundToInt(), raceProfile(), GoalAlignment.AMBITIOUS)
        collect("alignedFast", (predictedSec * 0.98).roundToInt(), raceProfile(), GoalAlignment.ALIGNED)
        collect("alignedSlow", (predictedSec * 1.03).roundToInt(), raceProfile(), GoalAlignment.ALIGNED)
        collect("conservative", (predictedSec * 1.10).roundToInt(), raceProfile(), GoalAlignment.CONSERVATIVE)
        val noVdot = RacePaceResolver.resolve(raceProfile(vdot = null), race, null, MONDAY)
        assertThat(noVdot.rpe).isNotNull()
        texts += CheckedText("$RACE_PACE [unavailableNoVdot note]", noVdot.note)
        val stale = RacePaceResolver.resolve(raceProfile(calibratedAt = MONDAY.minusDays(100)), race, null, MONDAY)
        assertThat(stale.rpe).isNotNull()
        texts += CheckedText("$RACE_PACE [unavailableStale note]", stale.note)
        val lowConfidence = RacePaceResolver.resolve(
            raceProfile(vdotSource = null, calibratedAt = MONDAY.minusDays(2)),
            race,
            null,
            MONDAY,
        )
        assertThat(lowConfidence.rpe).isNotNull()
        texts += CheckedText("$RACE_PACE [unavailableLowConfidence note]", lowConfidence.note)
        return texts
    }

    private fun recommenderTexts(): List<CheckedText> {
        val recommender = WorkoutRecommender()
        val texts = mutableListOf<CheckedText>()
        fun collect(label: String, input: PrescriptionInput) {
            val recommendation = recommender.recommend(input)
            texts += CheckedText("$RECOMMENDER [$label title]", recommendation.title)
            texts += CheckedText("$RECOMMENDER [$label rationale]", recommendation.rationale)
        }
        val baseProfile = recommenderProfile()
        val green = readiness()
        collect(
            "illness",
            PrescriptionInput(baseProfile, readiness(flags = setOf(ReadinessFlag.ILLNESS_OR_INJURY)), null, MONDAY),
        )
        collect(
            "red",
            PrescriptionInput(baseProfile, readiness(level = ReadinessLevel.RED, score = 20), null, MONDAY),
        )
        collect(
            "noAvailability",
            PrescriptionInput(
                baseProfile.copy(weeklyAvailability = WeeklyAvailability(emptyMap())),
                green,
                null,
                MONDAY,
            ),
        )
        collect(
            "shortDay",
            PrescriptionInput(
                baseProfile.copy(
                    weeklyAvailability = WeeklyAvailability(mapOf(DayOfWeek.MONDAY to 10)),
                ),
                green,
                null,
                MONDAY,
            ),
        )
        val subjectiveMissing = recommender.recommend(
            PrescriptionInput(baseProfile, readiness(components = emptyMap()), trustedLoad(), MONDAY),
        )
        assertThat(subjectiveMissing.ruleIds).contains("R-READY-06")
        texts += CheckedText("$RECOMMENDER [subjectiveMissing title]", subjectiveMissing.title)
        texts += CheckedText("$RECOMMENDER [subjectiveMissing rationale]", subjectiveMissing.rationale)
        val shortSleep = recommender.recommend(
            PrescriptionInput(
                baseProfile,
                readiness(flags = setOf(ReadinessFlag.SHORT_SLEEP)),
                trustedLoad(),
                MONDAY,
            ),
        )
        assertThat(shortSleep.ruleIds).contains("R-READY-04")
        texts += CheckedText("$RECOMMENDER [shortSleep title]", shortSleep.title)
        texts += CheckedText("$RECOMMENDER [shortSleep rationale]", shortSleep.rationale)
        val acwrHigh = recommender.recommend(
            PrescriptionInput(baseProfile, green, load(acwr = 1.6), MONDAY),
        )
        assertThat(acwrHigh.ruleIds).contains("R-LOAD-03")
        texts += CheckedText("$RECOMMENDER [acwrHigh title]", acwrHigh.title)
        texts += CheckedText("$RECOMMENDER [acwrHigh rationale]", acwrHigh.rationale)
        val amber = recommender.recommend(
            PrescriptionInput(baseProfile, readiness(level = ReadinessLevel.AMBER, score = 55), trustedLoad(), MONDAY),
        )
        assertThat(amber.ruleIds).contains("R-READY-03")
        texts += CheckedText("$RECOMMENDER [amber title]", amber.title)
        texts += CheckedText("$RECOMMENDER [amber rationale]", amber.rationale)
        val staleLoad = load(sourceFresh = false)
        assertThat(staleLoad.dataState).isEqualTo(LoadDataState.STALE)
        collect("stale", PrescriptionInput(baseProfile, green, staleLoad, MONDAY))
        val thinLoad = load(daysWithLoad = 7)
        assertThat(thinLoad.dataState).isEqualTo(LoadDataState.INSUFFICIENT)
        collect("insufficient", PrescriptionInput(baseProfile, green, thinLoad, MONDAY))
        collect("base", PrescriptionInput(baseProfile, green, trustedLoad(), MONDAY))
        collect(
            "heat",
            PrescriptionInput(baseProfile, green, trustedLoad(), MONDAY, heatLevel = HeatLevel.HOT),
        )
        collect(
            "heatTreadmill",
            PrescriptionInput(
                baseProfile.copy(treadmillAccess = true),
                green,
                trustedLoad(),
                MONDAY,
                heatLevel = HeatLevel.HOT,
            ),
        )
        collect(
            "noCalibration",
            PrescriptionInput(
                baseProfile.copy(thresholdPace = null, lactateThresholdHeartRate = null),
                green,
                trustedLoad(),
                MONDAY,
            ),
        )
        return texts
    }

    private fun gate(
        plannedSession: PlannedSession = session(),
        readiness: ReadinessResult = readiness(),
        load: LoadSnapshot? = load(),
        recovery: RecoveryWindow? = null,
        profile: AthleteProfile = profile(),
        phase: MesoPhase? = null,
        raceWeek: Boolean = false,
        coldStart: ColdStartAllowance? = null,
    ) = QualityGate.evaluate(
        plannedSession = plannedSession,
        readiness = readiness,
        load = load,
        recovery = recovery,
        profile = profile,
        date = MONDAY,
        phase = phase,
        raceWeek = raceWeek,
        coldStart = coldStart,
    )

    private fun session(
        type: WorkoutType = WorkoutType.QUALITY,
        kind: QualityKind? = QualityKind.CONTINUOUS,
        durationMin: Int? = 40,
    ) = PlannedSession(
        date = MONDAY,
        type = type,
        title = "Ritmo forte",
        durationMin = durationMin,
        rationale = "Ritmo forte com blocos.",
        ruleIds = listOf("R-PER-03"),
        evidenceIds = listOf("EV-SEILER-2010"),
        qualityKind = kind,
        planDecision = PlanDecision(PlanDecisionReason.QUALITY_OPPORTUNITY),
    )

    private fun readiness(
        level: ReadinessLevel = ReadinessLevel.GREEN,
        components: Map<ReadinessComponent, Int> = mapOf(ReadinessComponent.SUBJECTIVE to 80),
        flags: Set<ReadinessFlag> = emptySet(),
        score: Int = when (level) {
            ReadinessLevel.GREEN -> 80
            ReadinessLevel.AMBER -> 55
            ReadinessLevel.RED -> 20
        },
    ) = ReadinessResult(
        score = score,
        level = level,
        confidence = DataConfidence.HIGH,
        components = components,
        missing = ReadinessComponent.entries.toSet() - components.keys,
        flags = flags,
    )

    private fun profile(experience: Experience = Experience.INTERMEDIATE) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        experience = experience,
        timezone = "America/Sao_Paulo",
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
                DayOfWeek.SATURDAY to 90,
            ),
        ),
    )

    private fun recommenderProfile() = profile().copy(
        weeklyAvailability = WeeklyAvailability(
            mapOf(
                DayOfWeek.MONDAY to 40,
                DayOfWeek.TUESDAY to 40,
                DayOfWeek.WEDNESDAY to 40,
                DayOfWeek.FRIDAY to 40,
            ),
        ),
    )

    private fun recovery() = RecoveryWindow(
        CompletedRace(
            eventId = "race-1",
            distance = RaceDistance.FIVE_K,
            raceDate = MONDAY.minusDays(2),
        ),
    )

    private fun load(
        acwr: Double? = 1.0,
        daysWithLoad: Int = 14,
        sourceFresh: Boolean = true,
        subjectiveDaysInWindow: Int = 0,
        monotony: Double? = 1.2,
        strain: Double? = 400.0,
        chronicAverage28d: Double = 0.0,
        daysWithoutData: Int = 0,
        daysBeforeHistory: Int = 0,
    ): LoadSnapshot {
        val withoutData = if (sourceFresh) daysWithoutData + daysBeforeHistory else 28 - daysWithLoad
        val withoutActivity = if (sourceFresh) 28 - daysWithLoad - withoutData else 0
        return LoadSnapshot(
            daily = emptyList(),
            ctlSeries = emptyList(),
            atlSeries = emptyList(),
            ctl = 0.0,
            atl = 0.0,
            tsb = 0.0,
            acuteAverage7d = 0.0,
            chronicAverage28d = chronicAverage28d,
            acwr = acwr,
            monotony = monotony,
            missingDays = withoutData + withoutActivity,
            coverage = LoadCoverage(
                daysInWindow = 28,
                daysWithLoad = daysWithLoad,
                daysWithoutActivity = withoutActivity,
                daysWithoutData = withoutData,
                lastActivityAt = null,
                sourceFresh = sourceFresh,
                daysBeforeHistory = daysBeforeHistory,
            ),
            subjectiveDaysInWindow = subjectiveDaysInWindow,
            strain = strain,
        )
    }

    private fun trustedLoad(): LoadSnapshot {
        val snapshot = load()
        assertThat(snapshot.dataState).isEqualTo(LoadDataState.KNOWN)
        return snapshot
    }

    private fun prescription(
        type: WorkoutType,
        targets: PrescriptionTargets = PrescriptionTargets(rpe = 6, primary = TargetKind.RPE),
        ruleIds: List<String> = listOf("R-ZONE-01"),
        structure: WorkoutStructure? = null,
    ): DailyPrescription {
        val duration = structure?.totalMin ?: 40
        return DailyPrescription(
            date = MONDAY,
            plannedSessionId = null,
            plannedType = type,
            plannedTitle = "Planejado",
            plannedDurationMin = duration,
            prescribedType = type,
            title = "Prescrito",
            prescribedDurationMin = if (type == WorkoutType.REST) null else duration,
            targets = targets,
            adjustment = PrescriptionAdjustment.KEEP,
            rationale = "Decisão do dia.",
            ruleIds = ruleIds,
            evidenceIds = listOf("EV-SAW-2016"),
            confidence = DataConfidence.HIGH,
            engineVersion = "test-v1",
            inputsHash = "hash",
            structure = structure,
        )
    }

    private fun stridesStructure(): WorkoutStructure = WorkoutStructure(
        warmupMin = 8,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.STRIDES,
                repetitions = 6,
                workSec = 20,
                recoverySec = 60,
                target = "RPE 8",
                note = "Rápido e relaxado; recuperação completa.",
            ),
        ),
        cooldownMin = 9,
        totalMin = 25,
    )

    private fun workStructure(): WorkoutStructure = WorkoutStructure(
        warmupMin = 10,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.WORK,
                repetitions = 5,
                workSec = 180,
                recoverySec = 120,
                target = "I",
                note = "Repetições fortes; recuperação leve.",
            ),
        ),
        cooldownMin = 5,
        totalMin = 40,
    )

    private fun racePaceStructure(): WorkoutStructure = WorkoutStructure(
        warmupMin = 10,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.RACE_PACE,
                repetitions = 5,
                workSec = 240,
                recoverySec = 120,
                target = "GOAL_5K",
                note = "Ritmo da prova atual.",
            ),
        ),
        cooldownMin = 5,
        totalMin = 45,
    )

    private fun recoveryStructure(): WorkoutStructure = WorkoutStructure(
        warmupMin = 10,
        blocks = listOf(
            WorkoutBlock(
                kind = BlockKind.WORK,
                workSec = 1_200,
                target = "T",
                note = "Ritmo forte sustentado.",
            ),
            WorkoutBlock(kind = BlockKind.RECOVERY, workSec = 300),
        ),
        cooldownMin = 5,
        totalMin = 40,
    )

    private fun verdictSession(type: WorkoutType, date: LocalDate = MONDAY, durationMin: Int? = 40) = PlannedSession(
        date = date,
        type = type,
        title = "Sessão de teste",
        durationMin = durationMin,
        rationale = "Teste determinístico.",
        ruleIds = listOf("R-ZONE-01"),
        evidenceIds = listOf("EV-FRIEL-2018"),
    )

    private fun distribution(z1: Int, z2: Int, z3: Int, z4: Int, z5: Int): HrZoneDistribution = HrZoneDistribution(
        zones = listOf(
            HrZoneDuration(HeartRateZone.Z1, z1),
            HrZoneDuration(HeartRateZone.Z2, z2),
            HrZoneDuration(HeartRateZone.Z3, z3),
            HrZoneDuration(HeartRateZone.Z4, z4),
            HrZoneDuration(HeartRateZone.Z5, z5),
        ),
        totalSec = z1 + z2 + z3 + z4 + z5,
    )

    private fun raceProfile(
        vdot: Double? = FIVE_K_VDOT,
        vdotSource: VdotSource? = VdotSource.TRAINING_ESTIMATE,
        calibratedAt: LocalDate? = MONDAY.minusDays(2),
    ) = AthleteProfile(
        id = "athlete-1",
        birthDate = LocalDate.of(1988, 1, 1),
        sex = Sex.MALE,
        restingHeartRate = 58,
        maxHeartRateObserved = 187,
        lactateThresholdHeartRate = 180,
        thresholdPace = Pace.fromSecondsPerKm(360),
        vdot = vdot,
        vdotSource = vdotSource,
        vdotCalibratedAt = calibratedAt,
        experience = Experience.INTERMEDIATE,
        timezone = "America/Sao_Paulo",
    )

    private companion object {
        const val DOMAIN = "core/domain/src/main/kotlin/com/meucoach/core/domain/"
        const val CATALOG = DOMAIN + "prescription/ExplanationCatalog.kt"
        const val QUALITY_GATE = DOMAIN + "prescription/QualityGate.kt"
        const val SESSION_LIBRARY = DOMAIN + "prescription/SessionLibrary.kt"
        const val BRIEFING = DOMAIN + "briefing/SessionBriefingEngine.kt"
        const val VERDICT = DOMAIN + "story/StoryVerdictCalculator.kt"
        const val RACE_PACE = DOMAIN + "prescription/RacePace.kt"
        const val RECOMMENDER = DOMAIN + "prescription/WorkoutRecommender.kt"
        val MONDAY: LocalDate = LocalDate.of(2026, 11, 2)
        const val LTHR = 180
        val FIVE_K_VDOT: Double = requireNotNull(CalibrationCalculator.vdot(5_000.0, 1_859)) {
            "Reference VDOT must compute"
        }
    }
}
