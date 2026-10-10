package com.example.kapsama20

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Instant

/** Tamamlandı bildirimi, video açılmasından ve öğretmen onayından ayrıdır. */
class HomeworkSubmitWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val homeworkId = inputData.getLong(KEY_HOMEWORK_ID, -1L)
        val studentId = inputData.getString(KEY_STUDENT_ID).orEmpty()
        if (homeworkId < 0 || studentId.isBlank()) return Result.failure()
        val body = OdevDosyalari.durum(studentId, homeworkId)
            .put("yapildi", true)
            .put("yapildi_zaman", Instant.ofEpochMilli(inputData.getLong(KEY_TIME, System.currentTimeMillis())).toString())
        return when (val code = SupabaseYaz.upsert(applicationContext, "odev_durumu", body)) {
            in 200..299 -> {
                HomeworkRepository.markSubmitted(applicationContext, studentId, homeworkId)
                Result.success()
            }
            0, 401, 408, 409, 429, in 500..599 -> Result.retry()
            else -> Result.failure(workDataOf("error" to "Ödev gönderilemedi (HTTP $code)."))
        }
    }

    companion object {
        const val KEY_HOMEWORK_ID = "homework_id"
        const val KEY_STUDENT_ID = "student_id"
        const val KEY_TIME = "completed_at"
    }
}
