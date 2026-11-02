package ir.sahlbit.launcher

import android.app.Application
import android.util.Log

/**
 * Records an uncaught app crash so the next launch can offer a safe route
 * to Android's Home-app settings. It never changes the default launcher.
 */
class SahlBitApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_CRASH_RECOVERY_PENDING, true)
                    .putLong(KEY_CRASH_TIME, System.currentTimeMillis())
                    .commit()
            }.onFailure {
                Log.e(TAG, "Could not save crash recovery marker", it)
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                Log.e(TAG, "Uncaught exception", throwable)
            }
        }
    }

    companion object {
        const val PREFS_NAME = "sahlbit_recovery"
        const val KEY_CRASH_RECOVERY_PENDING = "crash_recovery_pending"
        const val KEY_CRASH_TIME = "crash_time"
        private const val TAG = "SahlBitRecovery"
    }
}
