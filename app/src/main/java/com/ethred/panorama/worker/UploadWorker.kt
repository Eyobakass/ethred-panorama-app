package com.ethred.panorama.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ethred.panorama.R
import com.ethred.panorama.data.local.db.CaptureSessionEntity
import com.ethred.panorama.data.repository.CaptureSessionRepository
import com.ethred.panorama.data.repository.UploadQueueRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class UploadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val uploadQueueRepository: UploadQueueRepository,
    private val sessionRepository: CaptureSessionRepository
) : CoroutineWorker(appContext, params) {

    private val notificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val pendingItems = uploadQueueRepository.getPendingItems()
        if (pendingItems.isEmpty()) {
            return@withContext Result.success()
        }

        createNotificationChannel()
        setForeground(createForegroundInfo(0, pendingItems.size))

        var hasFailure = false

        pendingItems.forEachIndexed { index, item ->
            setForeground(createForegroundInfo(index + 1, pendingItems.size))

            val result = uploadQueueRepository.processItem(item)
            if (result.isSuccess) {
                // Cleanup raw frames post successful upload to free device storage as per FR-SYNC-03
                sessionRepository.clearFrames(item.sessionId)
                sessionRepository.updateSessionStatus(item.sessionId, CaptureSessionEntity.STATUS_UPLOADED)
            } else {
                hasFailure = true
            }
        }

        if (hasFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun createForegroundInfo(current: Int, total: Int): ForegroundInfo {
        val title = "Uploading 360° Panorama ($current/$total)"
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(total, current, false)
            .build()

        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ethred 360 Upload Service",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "ethred_360_upload_channel"
        private const val NOTIFICATION_ID = 3601
    }
}
