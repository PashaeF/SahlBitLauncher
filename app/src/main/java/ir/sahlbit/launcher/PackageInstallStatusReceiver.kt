package ir.sahlbit.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

class PackageInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmation = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT) as? Intent
                }
                confirmation?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                    runCatching { context.startActivity(it) }.onFailure {
                        Toast.makeText(context, "برای تکمیل نصب، پنجرهٔ نصب‌کنندهٔ اندروید را باز کنید.", Toast.LENGTH_LONG).show()
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Toast.makeText(context, "به‌روزرسانی سهل‌بیت با موفقیت نصب شد.", Toast.LENGTH_LONG).show()
            else -> {
                val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
                Toast.makeText(context, "نصب به‌روزرسانی انجام نشد. ${detail}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
