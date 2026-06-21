package com.prplegryn.bd.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.prplegryn.bd.BdApplication
import com.prplegryn.bd.data.DownloadTask
import com.prplegryn.bd.data.TaskStatus

object DownloadScheduler {
    fun enqueue(context: Context, task: DownloadTask) {
        val app = context.applicationContext as BdApplication
        app.tasks.update(task.id) {
            it.copy(status = TaskStatus.QUEUED, error = "", progress = 0)
        }
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_TASK_ID, task.id).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(task.id)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(task.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun pause(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(id))
        (context.applicationContext as BdApplication).tasks.update(id) {
            it.copy(status = TaskStatus.PAUSED, speedBytesPerSecond = 0)
        }
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(id))
        (context.applicationContext as BdApplication).tasks.update(id) {
            it.copy(status = TaskStatus.CANCELLED, speedBytesPerSecond = 0)
        }
    }

    private fun uniqueName(id: String) = "download-$id"
}

