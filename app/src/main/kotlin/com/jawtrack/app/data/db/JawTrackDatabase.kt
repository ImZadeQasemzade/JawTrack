package com.jawtrack.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.jawtrack.app.data.db.dao.AudioClipDao
import com.jawtrack.app.data.db.dao.DailyFactorsDao
import com.jawtrack.app.data.db.dao.EpisodeDao
import com.jawtrack.app.data.db.dao.GapDao
import com.jawtrack.app.data.db.dao.HeartRateSampleDao
import com.jawtrack.app.data.db.dao.HrvSampleDao
import com.jawtrack.app.data.db.dao.LabelDao
import com.jawtrack.app.data.db.dao.MorningCheckinDao
import com.jawtrack.app.data.db.dao.NightMetricsDao
import com.jawtrack.app.data.db.dao.RoomProfileDao
import com.jawtrack.app.data.db.dao.SessionDao
import com.jawtrack.app.data.db.dao.SleepStageDao
import com.jawtrack.app.data.db.entities.AudioClip
import com.jawtrack.app.data.db.entities.DailyFactors
import com.jawtrack.app.data.db.entities.Episode
import com.jawtrack.app.data.db.entities.Gap
import com.jawtrack.app.data.db.entities.HeartRateSample
import com.jawtrack.app.data.db.entities.HrvSample
import com.jawtrack.app.data.db.entities.Label
import com.jawtrack.app.data.db.entities.MorningCheckin
import com.jawtrack.app.data.db.entities.NightMetrics
import com.jawtrack.app.data.db.entities.RoomProfile
import com.jawtrack.app.data.db.entities.Session
import com.jawtrack.app.data.db.entities.SleepStage

@Database(
    entities = [
        Session::class,
        Episode::class,
        AudioClip::class,
        HeartRateSample::class,
        HrvSample::class,
        SleepStage::class,
        NightMetrics::class,
        DailyFactors::class,
        MorningCheckin::class,
        Label::class,
        RoomProfile::class,
        Gap::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class JawTrackDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao
    abstract fun gapDao(): GapDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun audioClipDao(): AudioClipDao
    abstract fun heartRateSampleDao(): HeartRateSampleDao
    abstract fun hrvSampleDao(): HrvSampleDao
    abstract fun sleepStageDao(): SleepStageDao
    abstract fun nightMetricsDao(): NightMetricsDao
    abstract fun dailyFactorsDao(): DailyFactorsDao
    abstract fun morningCheckinDao(): MorningCheckinDao
    abstract fun labelDao(): LabelDao
    abstract fun roomProfileDao(): RoomProfileDao

    companion object {
        @Volatile private var instance: JawTrackDatabase? = null

        fun getInstance(context: Context): JawTrackDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JawTrackDatabase::class.java,
                    "jawtrack.db"
                ).build().also { instance = it }
            }
    }
}
