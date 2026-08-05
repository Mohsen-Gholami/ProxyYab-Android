package ir.proxyyab.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try { Repository(applicationContext).refresh(); Result.success() } catch (_: Exception) { Result.retry() }
}
