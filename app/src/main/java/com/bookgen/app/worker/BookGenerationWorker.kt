package com.bookgen.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.bookgen.app.BookGenApplication
import com.bookgen.app.MainActivity
import com.bookgen.app.domain.model.MarkupFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val bookId = inputData.getString(KEY_BOOK_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.failure(workDataOf(KEY_ERROR to "書籍IDが指定されていません"))
        val targetSections = inputData.getNullableStringArray(KEY_TARGET_SECTION_IDS)
            ?.filterNotNull()
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
        val customInstruction = inputData.getString(KEY_CUSTOM_INSTRUCTION)
        val formatOverride = inputData.getString(KEY_MARKUP_FORMAT)
            ?.let { raw -> runCatching { MarkupFormat.valueOf(raw) }.getOrNull() }

        val container = (applicationContext as BookGenApplication).container
        val bookTitle = runCatching { container.bookRepository.getBook(bookId).summary.title }
            .getOrDefault("書籍執筆")

        ensureNotificationChannels(applicationContext)
        val notifId = bookId.hashCode() and 0x7FFFFFFF

        runCatching {
            setForeground(buildForegroundInfo(notifId, bookId, bookTitle, 0, 1, "執筆準備中…"))
        }

        return try {
            val summary = container.generationPipeline.executeBookGeneration(
                bookId = bookId,
                targetSectionIds = targetSections,
                customInstruction = customInstruction,
                markupFormatOverride = formatOverride,
                onNotificationUpdate = { completed, total, label ->
                    setProgressAsync(
                        workDataOf(
                            KEY_COMPLETED to completed,
                            KEY_TOTAL to total,
                            KEY_LABEL to label,
                        ),
                    )
                    runCatching {
                        setForeground(
                            buildForegroundInfo(notifId, bookId, bookTitle, completed, total, label),
                        )
                    }
                },
            )

            postCompletionNotification(
                bookId = bookId,
                bookTitle = bookTitle,
                succeeded = summary.succeeded,
                failed = summary.failed,
                errorMessage = summary.firstErrorMessage,
            )

            if (summary.failed > 0 && summary.succeeded == 0) {
                Result.failure(workDataOf(KEY_ERROR to (summary.firstErrorMessage ?: "生成に失敗しました")))
            } else {
                Result.success(
                    workDataOf(
                        KEY_COMPLETED to summary.succeeded,
                        KEY_TOTAL to summary.totalTargets,
                    ),
                )
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            postCompletionNotification(
                bookId = bookId,
                bookTitle = bookTitle,
                succeeded = 0,
                failed = 1,
                errorMessage = e.message,
            )
            Result.failure(workDataOf(KEY_ERROR to (e.message ?: "執筆ワーカーエラー")))
        }
    }

    private fun buildForegroundInfo(
        notificationId: Int,
        bookId: String,
        bookTitle: String,
        completed: Int,
        total: Int,
        statusLabel: String,
    ): ForegroundInfo {
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val openIntent = createWorkspacePendingIntent(applicationContext, bookId)
        val maxSteps = total.coerceAtLeast(1)
        val progress = completed.coerceIn(0, maxSteps)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("執筆中: $bookTitle ($progress/$maxSteps)")
            .setContentText(statusLabel)
            .setProgress(maxSteps, progress, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "一時停止",
                cancelIntent,
            )
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun postCompletionNotification(
        bookId: String,
        bookTitle: String,
        succeeded: Int,
        failed: Int,
        errorMessage: String?,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val (title, body) = when {
            failed > 0 && succeeded == 0 ->
                "執筆が中断しました: $bookTitle" to (errorMessage ?: "失敗した節から再開できます")
            failed > 0 ->
                "一部完了・要再開: $bookTitle" to "${succeeded}節を保存、${failed}節が失敗しました"
            succeeded > 0 ->
                "執筆が完了しました: $bookTitle" to "${succeeded}節を生成・保存しました。タップして確認できます"
            else ->
                "執筆を完了しました: $bookTitle" to "すべての節は既に最新です"
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(createWorkspacePendingIntent(applicationContext, bookId))
            .build()

        val doneNotifId = ("done_$bookId").hashCode() and 0x7FFFFFFF
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(doneNotifId, notification)
        }
    }

    companion object {
        const val KEY_BOOK_ID = "book_id"
        const val KEY_TARGET_SECTION_IDS = "target_section_ids"
        const val KEY_CUSTOM_INSTRUCTION = "custom_instruction"
        const val KEY_MARKUP_FORMAT = "markup_format"
        const val KEY_COMPLETED = "completed"
        const val KEY_TOTAL = "total"
        const val KEY_LABEL = "label"
        const val KEY_ERROR = "error"

        private const val CHANNEL_PROGRESS = "bookgen_generation_progress"
        private const val CHANNEL_ALERTS = "bookgen_generation_alerts"

        fun uniqueWorkName(bookId: String): String = "bookgen_write_$bookId"

        fun enqueueBookGeneration(
            context: Context,
            bookId: String,
            targetSectionIds: List<String>? = null,
            customInstruction: String? = null,
            markupFormatOverride: MarkupFormat? = null,
        ) {
            val dataBuilder = Data.Builder()
                .putString(KEY_BOOK_ID, bookId)
            if (!targetSectionIds.isNullOrEmpty()) {
                dataBuilder.putStringArray(KEY_TARGET_SECTION_IDS, targetSectionIds.toTypedArray())
            }
            if (!customInstruction.isNullOrBlank()) {
                dataBuilder.putString(KEY_CUSTOM_INSTRUCTION, customInstruction.trim())
            }
            if (markupFormatOverride != null) {
                dataBuilder.putString(KEY_MARKUP_FORMAT, markupFormatOverride.name)
            }

            val request = OneTimeWorkRequestBuilder<BookGenerationWorker>()
                .setInputData(dataBuilder.build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(bookId),
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancelBookGeneration(context: Context, bookId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(bookId))
        }

        fun observeIsRunning(context: Context, bookId: String): Flow<Boolean> {
            return WorkManager.getInstance(context)
                .getWorkInfosForUniqueWorkFlow(uniqueWorkName(bookId))
                .map { infos ->
                    infos.any { info ->
                        info.state == WorkInfo.State.RUNNING ||
                            info.state == WorkInfo.State.ENQUEUED ||
                            info.state == WorkInfo.State.BLOCKED
                    }
                }
        }

        private fun ensureNotificationChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_PROGRESS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_PROGRESS,
                        "書籍執筆の進行状況",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "バックグラウンドでAI執筆を実行中の進捗を表示します"
                    },
                )
            }
            if (nm.getNotificationChannel(CHANNEL_ALERTS) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERTS,
                        "執筆完了・エラー通知",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "書籍の全節完了またはエラー中断時に通知します"
                    },
                )
            }
        }

        private fun createWorkspacePendingIntent(context: Context, bookId: String): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_BOOK_ID, bookId)
            }
            return PendingIntent.getActivity(
                context,
                bookId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
