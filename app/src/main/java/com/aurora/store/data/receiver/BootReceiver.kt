package com.aurora.store.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.aurora.store.data.helper.UpdateHelper
import com.aurora.store.data.model.UpdateMode
import com.aurora.store.data.work.UpdateWorker
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val BOOT_UPDATE_WORKER = "BOOT_UPDATE_WORKER"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i(TAG, "onReceive called with action: ${intent?.action}")

        val bootActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )
        if (context != null && intent?.action in bootActions) {
            Log.i(TAG, "Boot completed, scheduling update check in 20 seconds")

            val inputData = Data.Builder()
                .putInt(UpdateHelper.UPDATE_MODE, UpdateMode.CHECK_AND_NOTIFY.ordinal)
                .build()

            val work = OneTimeWorkRequestBuilder<UpdateWorker>()
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setInputData(inputData)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(BOOT_UPDATE_WORKER, ExistingWorkPolicy.REPLACE, work)

            Log.i(TAG, "Update check scheduled")
        }
    }
}
