package com.jawtrack.app.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jawtrack.app.JawTrackApp
import com.jawtrack.corelogic.enrichment.EpisodeSummary
import com.jawtrack.corelogic.report.IndexTrend
import com.jawtrack.corelogic.report.NightSummaryText
import com.jawtrack.corelogic.report.TimelineLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class StageBand(val startFraction: Double, val endFraction: Double, val stage: String)
data class HeartRatePoint(val fraction: Double, val bpm: Int)
data class EpisodeTick(val episodeId: Long, val fraction: Double, val peakScoreFraction: Double)
data class GapBand(val startFraction: Double, val endFraction: Double)

data class ReportUiState(
    val isLoading: Boolean = true,
    val hasSession: Boolean = false,
    val sessionId: Long? = null,
    /** Leads Screen 1 instead of the index when true (§8: "not with a misleadingly low number"). */
    val serviceDiedEarly: Boolean = false,
    val jawTrackIndex: Double = 0.0,
    val confidenceGrade: String = "C",
    val indexDelta: Double? = null,
    val summarySentence: String = "",
    val nightStartMillis: Long = 0,
    val nightEndMillis: Long = 0,
    val stageBands: List<StageBand> = emptyList(),
    val heartRatePoints: List<HeartRatePoint> = emptyList(),
    val episodeTicks: List<EpisodeTick> = emptyList(),
    val gapBands: List<GapBand> = emptyList()
)

class ReportViewModel(application: Application) : AndroidViewModel(application) {

    private val app: JawTrackApp get() = getApplication()

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ReportUiState(isLoading = true)

            val session = app.reportRepository.getMostRecentReportableSession()
            if (session == null) {
                _uiState.value = ReportUiState(isLoading = false, hasSession = false)
                return@launch
            }

            val nightMetrics = app.reportRepository.getNightMetrics(session.id)
            val episodes = app.reportRepository.getEpisodes(session.id)
            val stages = app.reportRepository.getSleepStages(session.id)
            val heartRate = app.reportRepository.getHeartRateSamples(session.id)
            val gaps = app.reportRepository.getGaps(session.id)
            val history = app.reportRepository.getRecentIndexHistory(excludingSessionId = session.id)

            val nightStart = session.startedAt
            val nightEnd = session.endedAt ?: session.startedAt

            val summary = NightSummaryText.summarize(episodes.map { EpisodeSummary(it.onsetAt, it.offsetAt, it.sleepStage) })
            val sentence = NightSummaryText.formatSentence(
                summary,
                formatTime = ::formatClockTime,
                stageDisplayName = ::stageDisplayName
            )

            val index = nightMetrics?.episodesPerHour ?: 0.0
            val delta = IndexTrend.delta(index, IndexTrend.rollingAverage(history))

            val peakScoreCeiling = episodes.maxOfOrNull { it.peakScore }?.coerceAtLeast(0.01f) ?: 1f

            _uiState.value = ReportUiState(
                isLoading = false,
                hasSession = true,
                sessionId = session.id,
                serviceDiedEarly = !session.cleanShutdown,
                jawTrackIndex = index,
                confidenceGrade = nightMetrics?.confidenceGrade ?: "C",
                indexDelta = delta,
                summarySentence = sentence,
                nightStartMillis = nightStart,
                nightEndMillis = nightEnd,
                stageBands = stages.map {
                    val range = TimelineLayout.fractionRangeOf(it.startAt, it.endAt, nightStart, nightEnd)
                    StageBand(range.startFraction, range.endFraction, it.stage)
                },
                heartRatePoints = heartRate.map {
                    HeartRatePoint(TimelineLayout.fractionOf(it.t, nightStart, nightEnd), it.bpm)
                },
                episodeTicks = episodes.map {
                    EpisodeTick(
                        episodeId = it.id,
                        fraction = TimelineLayout.fractionOf(it.onsetAt, nightStart, nightEnd),
                        peakScoreFraction = (it.peakScore / peakScoreCeiling).coerceIn(0f, 1f).toDouble()
                    )
                },
                gapBands = gaps.map {
                    val range = TimelineLayout.fractionRangeOf(it.startAt, it.endAt, nightStart, nightEnd)
                    GapBand(range.startFraction, range.endFraction)
                }
            )
        }
    }

    companion object {
        fun formatClockTime(millis: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

        fun stageDisplayName(stage: String): String = when (stage) {
            "LIGHT" -> "light sleep"
            "DEEP" -> "deep sleep"
            "REM" -> "REM sleep"
            "AWAKE" -> "time awake"
            "OUT_OF_BED" -> "time out of bed"
            else -> "sleep"
        }
    }
}
