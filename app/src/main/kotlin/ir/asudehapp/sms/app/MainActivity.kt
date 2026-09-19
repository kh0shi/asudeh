package ir.asudehapp.sms.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import ir.asudehapp.sms.model.SmsUri
import ir.asudehapp.sms.ui.AsudehTheme

class MainActivity : ComponentActivity() {

    private val model: AsudehViewModel by viewModels()

    private var defaultAppState by mutableStateOf(false)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        defaultAppState = isDefaultSmsApp()
        // با پیش‌فرض شدن، اپ به پیامک‌ها دسترسی پیدا می‌کند؛ همگام‌سازی از نو (D21).
        model.sync()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.READ_SMS] == true) model.sync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        defaultAppState = isDefaultSmsApp()
        requestPermissions()
        if (savedInstanceState == null) handle(intent)

        setContent {
            AsudehTheme {
                AsudehApp(
                    model = model,
                    isDefaultApp = defaultAppState,
                    onBecomeDefault = ::requestDefaultSmsRole,
                )
            }
        }
    }

    /** اعلان یا `sms:` وقتی اپ باز است (`singleTop`) از اینجا می‌رسد. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        defaultAppState = isDefaultSmsApp()
    }

    /** `sms:` از اپ‌های دیگر، یا لمس اعلان خود اپ. */
    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.action == Intent.ACTION_SENDTO || intent.action == Intent.ACTION_VIEW) {
            val parsed = intent.dataString?.let(SmsUri::parse) ?: return
            val recipient = parsed.recipients.firstOrNull() ?: return
            val body = parsed.body ?: intent.getStringExtra(EXTRA_SMS_BODY)
            model.composeTo(recipient, body)
            return
        }
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
        if (threadId != 0L) {
            model.openFromNotification(threadId)
        }
    }

    private fun isDefaultSmsApp(): Boolean =
        Telephony.Sms.getDefaultSmsPackage(this) == packageName

    /**
     * اولین مرحلهٔ اولین اجرا (D47): یک دکمه که درخواست `RoleManager` را باز
     * می‌کند. اگر کاربر قبول نکند، اپ از کار نمی‌افتد و فقط خواندنی می‌ماند.
     */
    private fun requestDefaultSmsRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS))
                return
            }
        }
        @Suppress("DEPRECATION")
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).putExtra(
            Telephony.Sms.Intents.EXTRA_PACKAGE_NAME,
            packageName,
        )
        roleLauncher.launch(intent)
    }

    /**
     * خواندن پیامک‌ها برای حالت «فقط-خواندنی» پیش از پیش‌فرض شدن لازم است (D47).
     * مخاطب‌ها اختیاری است، ولی قفل ایمنی D31 با آن بهتر کار می‌کند.
     */
    private fun requestPermissions() {
        val wanted = buildList {
            add(Manifest.permission.READ_SMS)
            add(Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permissionLauncher.launch(wanted.toTypedArray())
    }

    companion object {
        private const val EXTRA_THREAD_ID = "ir.asudehapp.sms.THREAD_ID"
        private const val EXTRA_SMS_BODY = "sms_body"

        fun intentFor(context: Context, threadId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_THREAD_ID, threadId)
    }
}
