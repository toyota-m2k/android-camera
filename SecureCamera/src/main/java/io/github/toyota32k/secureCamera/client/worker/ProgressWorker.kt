package io.github.toyota32k.secureCamera.client.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.toyota32k.media.lib.converter.IAwaiter
import io.github.toyota32k.secureCamera.client.worker.ProgressWorker.Companion.PROGRESS_CURRENT_BYTES
import io.github.toyota32k.secureCamera.client.worker.ProgressWorker.Companion.PROGRESS_TOTAL_LENGTH
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import java.util.UUID

abstract class ProgressWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val PROGRESS_TOTAL_LENGTH = "total_length"
        const val PROGRESS_CURRENT_BYTES = "current_bytes"
    }
    protected suspend fun progress(current:Long, total:Long) {
        setProgress(
            workDataOf(
                PROGRESS_CURRENT_BYTES to current,
                PROGRESS_TOTAL_LENGTH to total
            ))
    }
}

abstract class ProgressWorkerProcessor {
    companion object {
        val logger = UtLog("PWP")
    }
    class ProgressAwaiter(scope:CoroutineScope, val workManager:WorkManager, val id:UUID, val progress:((current:Long,total:Long)->Unit)?): IAwaiter<Boolean> {
        val result:Flow<Boolean> = flow {
            workManager.getWorkInfoByIdFlow(id)
                .collect { workInfo ->
                    when (workInfo?.state) {
                        WorkInfo.State.RUNNING -> {
                            if(progress != null) {
                                workInfo.progress.apply {
                                    progress(
                                        getLong(PROGRESS_CURRENT_BYTES, 0L),
                                        getLong(PROGRESS_TOTAL_LENGTH, 0L)
                                    )
                                }
                            }
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            logger.info("work succeeded")
                            emit(true)
                        }
                        WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                            logger.info("work failed")
                            emit(false)
                        }
                        else -> {}
                    }
                }
            logger.info("Completed")
        }.shareIn(scope, started = SharingStarted.Eagerly, replay = 1)

        override suspend fun await(): Boolean {
            return result.first()
        }

        override fun cancel() {
            workManager.cancelWorkById(id)
        }
    }

    inline fun <reified T:ProgressWorker> process(context:Context, data:Data, noinline progress:((current:Long, total:Long)->Unit)?):IAwaiter<Boolean> {
        val request = OneTimeWorkRequestBuilder<T>()
            .setInputData(data)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        val workManager = WorkManager.getInstance(context)
        workManager.enqueue(request)
        return ProgressAwaiter(CoroutineScope(Dispatchers.IO), workManager, request.id, progress)
    }
}