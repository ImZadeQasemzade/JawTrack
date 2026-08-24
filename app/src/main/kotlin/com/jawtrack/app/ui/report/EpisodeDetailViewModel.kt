package com.jawtrack.app.ui.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jawtrack.app.JawTrackApp
import com.jawtrack.app.clips.AudioPlayer
import com.jawtrack.app.data.db.entities.UserLabel
import com.jawtrack.app.recording.RecordingService
import com.jawtrack.corelogic.report.SpectrogramGenerator
import com.jawtrack.corelogic.report.WaveformDownsampler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EpisodeDetailUiState(
    val isLoading: Boolean = true,
    /** null once the queue is exhausted — Screen 3 shows a "all caught up" state instead. */
    val episodeId: Long? = null,
    val onsetAt: Long = 0,
    val offsetAt: Long = 0,
    val peakScore: Float = 0f,
    val rejectedClasses: List<String> = emptyList(),
    val currentLabel: UserLabel? = null,
    val waveform: FloatArray = FloatArray(0),
    val spectrogram: List<com.jawtrack.corelogic.report.SpectrogramFrame> = emptyList(),
    /** Episodes left after this one, for the "N left" progress text (§8: "ten clips should take under a minute"). */
    val remainingUnlabeledCount: Int = 0
)

/**
 * Screen 3's labeling loop (§8): loads one episode's clip, decrypts it to memory only (never a
 * temp file — enforced in [com.jawtrack.app.clips.ClipStore.readClip]), renders waveform +
 * spectrogram, plays it back, and on a label tap advances to the next unlabeled episode in the
 * session so a whole night's clips can be swiped through without leaving the screen.
 */
class EpisodeDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val app: JawTrackApp get() = getApplication()
    private val audioPlayer = AudioPlayer()

    private val queue = ArrayDeque<Long>()
    private var currentPcm: ShortArray = ShortArray(0)
    private var started = false

    private val _uiState = MutableStateFlow(EpisodeDetailUiState())
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    /** Call once, when the screen is first shown (e.g. tapped in from the timeline). Re-entrant calls are ignored. */
    fun start(sessionId: Long, initialEpisodeId: Long) {
        if (started) return
        started = true

        viewModelScope.launch {
            val unlabeled = app.labelRepository.getUnlabeledForSession(sessionId).map { it.id }
            queue.addAll(unlabeled)
            // The tapped episode leads the queue even if it's already labeled (re-review from the timeline).
            queue.remove(initialEpisodeId)
            queue.addFirst(initialEpisodeId)

            loadNext()
        }
    }

    fun play() {
        audioPlayer.play(currentPcm)
    }

    fun stopPlayback() {
        audioPlayer.stop()
    }

    fun label(label: UserLabel) {
        val episodeId = _uiState.value.episodeId ?: return
        viewModelScope.launch {
            app.labelRepository.applyLabel(episodeId, label, RecordingService.CLASSIFIER_VERSION)
            loadNext()
        }
    }

    /** Skip without labeling — leaves the episode unlabeled and moves it to the back of the queue. */
    fun skip() {
        val episodeId = _uiState.value.episodeId ?: return
        queue.addLast(episodeId)
        viewModelScope.launch { loadNext() }
    }

    private suspend fun loadNext() {
        audioPlayer.stop()
        _uiState.value = _uiState.value.copy(isLoading = true)

        val nextId = queue.removeFirstOrNull()
        if (nextId == null) {
            _uiState.value = EpisodeDetailUiState(isLoading = false, episodeId = null, remainingUnlabeledCount = 0)
            return
        }

        val episode = app.labelRepository.getEpisode(nextId)
        if (episode == null) {
            loadNext() // deleted out from under us; just move on
            return
        }

        val clip = app.labelRepository.getClipsForEpisode(nextId).firstOrNull()
        currentPcm = clip?.let { app.clipRepository.readClip(it.path) } ?: ShortArray(0)

        val waveform = WaveformDownsampler.downsample(currentPcm, WAVEFORM_COLUMNS)
        val spectrogram = if (clip != null) {
            SpectrogramGenerator.generate(currentPcm, clip.sampleRate)
        } else {
            emptyList()
        }

        _uiState.value = EpisodeDetailUiState(
            isLoading = false,
            episodeId = episode.id,
            onsetAt = episode.onsetAt,
            offsetAt = episode.offsetAt,
            peakScore = episode.peakScore,
            rejectedClasses = episode.rejectedClasses,
            currentLabel = episode.userLabel,
            waveform = waveform,
            spectrogram = spectrogram,
            remainingUnlabeledCount = queue.size
        )
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.stop()
    }

    companion object {
        private const val WAVEFORM_COLUMNS = 120
    }
}
