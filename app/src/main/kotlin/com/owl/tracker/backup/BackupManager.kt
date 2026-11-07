package com.owl.tracker.backup

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.owl.tracker.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.time.Clock
import java.time.LocalDate

/**
 * Makes and reads backup files. Files go where the person chooses, through Android's own file picker
 * and share sheet: no storage permission, and Owl Tracker itself still never touches the internet.
 */
class BackupManager(private val context: Context, private val repo: Repository, private val clock: Clock) {

    fun appVersion(): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""

    fun fileName(): String = "owl-tracker-backup-${LocalDate.now(clock)}.json"

    /** The backup's bytes; encrypted when [password] is given. */
    suspend fun build(password: CharArray?): ByteArray {
        val snapshot = repo.snapshot()
        return withContext(Dispatchers.Default) {
            BackupCodec.encode(BackupCodec.fromSnapshot(snapshot, clock.millis(), appVersion()), password, clock.zone)
        }
    }

    /** Writes to a file the person picked ("Save to a file"). */
    suspend fun write(uri: Uri, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri)
            ?: error("Couldn't open the file for writing")
        out.use { it.write(bytes) }
    }

    /**
     * A private copy for the share sheet, readable by the app the person shares to through a
     * one-time grant (FileProvider, see res/xml/backup_paths.xml). Older copies are removed first.
     */
    suspend fun writeForSharing(bytes: ByteArray): File = withContext(Dispatchers.IO) {
        val dir = shareDir().apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, fileName()).apply { writeBytes(bytes) }
    }

    /** Removes shared copies older than a day (they only need to live while the share happens). */
    suspend fun cleanUpShared() = withContext(Dispatchers.IO) {
        val cutoff = clock.millis() - 24 * 60 * 60 * 1000L
        shareDir().listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.delete() }
    }

    suspend fun read(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        (context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")).use { readLimited(it) }
    }

    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun shareDir() = File(context.cacheDir, "backups")

    companion object {
        /** Reads at most [BackupCodec.MAX_BYTES]; a bigger file is refused rather than read into memory. */
        fun readLimited(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > BackupCodec.MAX_BYTES) throw BackupException(BackupException.Problem.TOO_LARGE)
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
    }
}
