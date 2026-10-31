package com.nvllz.stepsy.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.nvllz.stepsy.ui.MainActivity
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal data class StepSnapshot(val steps: Int, val paused: Boolean)

internal object StepUpdates {
    fun flow(context: Context): Flow<StepSnapshot> = callbackFlow {
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                if (resultCode == 0 && resultData != null) {
                    trySend(StepSnapshot(
                        resultData.getInt(MotionService.KEY_STEPS),
                        resultData.getBoolean(MotionService.KEY_IS_PAUSED, false),
                    ))
                }
            }
        }
        context.startService(
            Intent(context, MotionService::class.java)
                .setAction(MotionService.ACTION_SUBSCRIBE)
                .putExtra(MainActivity.RECEIVER_TAG, receiver)
        )
        context.startService(Intent(context, MotionService::class.java).putExtra("FORCE_UPDATE", true))
        awaitClose()
    }
}