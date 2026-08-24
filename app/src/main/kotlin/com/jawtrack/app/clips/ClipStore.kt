package com.jawtrack.app.clips

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.jawtrack.app.recording.AudioConfig
import com.jawtrack.corelogic.clips.ClipCapping
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ClipWriteResult(val path: String, val keyAlias: String, val durationMs: Long, val sampleRate: Int)

/**
 * Encrypts and stores short evidence clips in app-private internal storage (JawTrackSpec §6.3,
 * §6.5, §6.6) — never external storage, never `MediaStore`. One AES-256-GCM key lives in the
 * Android Keystore for the whole app; every clip gets its own random IV (mandatory for GCM —
 * reusing an IV with the same key breaks its confidentiality guarantee), stored as a
 * length-prefix + IV + ciphertext in the clip file itself.
 *
 * A hand-rolled wrapper rather than `androidx.security:security-crypto` — §6.5 says that's in
 * maintenance mode, don't build new work on it — and rather than Tink, to avoid a dependency
 * this sandbox has no way to verify resolves or behaves correctly (same concern flagged on
 * `YamnetGate2Classifier`; unlike that one, everything here is plain `javax.crypto`/
 * `java.security.KeyStore`, standard JDK/Android framework APIs, not a third-party library).
 */
class ClipStore(
    context: Context,
    private val maxClipSamples: Int = DEFAULT_MAX_CLIP_SAMPLES
) {
    private val clipsDir: File by lazy { File(context.filesDir, CLIPS_DIR_NAME).apply { mkdirs() } }

    init {
        ensureKeyExists()
    }

    /**
     * Encrypts [pcm16Bit] (16-bit mono PCM) and writes it to a new file, truncating to the most
     * recent [maxClipSamples] samples if longer (§6.3's 12s cap enforced here too, in code, not
     * just by callers respecting it). Returns null for empty input rather than writing a
     * zero-length clip.
     */
    fun writeClip(pcm16Bit: ShortArray, sampleRateHz: Int, fileName: String = "${System.nanoTime()}.clip"): ClipWriteResult? {
        if (pcm16Bit.isEmpty()) return null
        val capped = ClipCapping.cap(pcm16Bit, maxClipSamples)

        val plaintext = ByteBuffer.allocate(capped.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            capped.forEach { putShort(it) }
        }.array()

        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getKey()) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)

        val file = File(clipsDir, fileName)
        file.outputStream().use { out ->
            out.write(byteArrayOf(iv.size.toByte()))
            out.write(iv)
            out.write(ciphertext)
        }

        val durationMs = (capped.size.toLong() * 1000) / sampleRateHz
        return ClipWriteResult(path = file.absolutePath, keyAlias = KEY_ALIAS, durationMs = durationMs, sampleRate = sampleRateHz)
    }

    /** Decrypts a clip straight to memory — never to a temp file (§8 Screen 3: decrypt to memory only). */
    fun readClip(path: String): ShortArray {
        val bytes = File(path).readBytes()
        val ivLength = bytes[0].toInt() and 0xFF
        val iv = bytes.copyOfRange(1, 1 + ivLength)
        val ciphertext = bytes.copyOfRange(1 + ivLength, bytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
        val plaintext = cipher.doFinal(ciphertext)

        val shortBuffer = ByteBuffer.wrap(plaintext).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return ShortArray(shortBuffer.remaining()).also { shortBuffer.get(it) }
    }

    /** @return true if a file existed at [path] and was deleted. */
    fun deleteClip(path: String): Boolean = File(path).delete()

    /** Deletes every clip file on disk immediately, no undo (§6.7 "delete all audio" button). */
    fun deleteAllClips() {
        clipsDir.listFiles()?.forEach { it.delete() }
    }

    private fun ensureKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) return

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
        try {
            keyGenerator.init(buildKeySpec(strongBox = true))
            keyGenerator.generateKey()
        } catch (e: Exception) {
            // No StrongBox on this device (or the API rejected it) -- a regular Keystore-backed
            // key, still non-exportable and often still hardware-backed, is the correct fallback.
            keyGenerator.init(buildKeySpec(strongBox = false))
            keyGenerator.generateKey()
        }
    }

    private fun buildKeySpec(strongBox: Boolean): KeyGenParameterSpec {
        val builder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false) // must be usable from a background service with no user present
        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }
        return builder.build()
    }

    private fun getKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "jawtrack_clip_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val CLIPS_DIR_NAME = "clips"

        /** 12s at 16kHz (§6.3). */
        val DEFAULT_MAX_CLIP_SAMPLES = ClipCapping.maxClipSamples(AudioConfig.SAMPLE_RATE_HZ)
    }
}
