package com.oppo.checkin

import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.oppo.checkin.automation.CheckinStateMachine
import com.oppo.checkin.automation.RepositoryOutcomeListener
import com.oppo.checkin.automation.TimeoutScheduler
import com.oppo.checkin.data.CheckinRepository
import com.oppo.checkin.notification.NotificationHelper
import com.oppo.checkin.scheduler.AlarmScheduler

class HandlerTimeoutScheduler : TimeoutScheduler {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null

    override fun schedule(delayMs: Long, onTimeout: () -> Unit) {
        cancel()
        val runnable = Runnable { onTimeout() }
        pendingRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    override fun cancel() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }
}

class AppContainer(context: Context) {
    val repository = CheckinRepository(context.applicationContext)
    val notificationHelper = NotificationHelper(context.applicationContext)
    val alarmScheduler = AlarmScheduler(context.applicationContext, repository)
    val stateMachine = CheckinStateMachine(
        outcomeListener = RepositoryOutcomeListener(repository, notificationHelper),
        timeoutScheduler = HandlerTimeoutScheduler()
    )
}

class CheckinApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        synchronized(Companion) {
            appContainer = container
        }
    }

    companion object {
        @Volatile
        private var appContainer: AppContainer? = null

        fun getContainer(context: Context): AppContainer {
            val existing = appContainer
            if (existing != null) return existing
            return synchronized(this) {
                appContainer ?: AppContainer(context.applicationContext).also {
                    appContainer = it
                }
            }
        }
    }
}
