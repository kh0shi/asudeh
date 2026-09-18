package ir.asudehapp.sms.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.ui.AsudehTheme

class MainActivity : ComponentActivity() {

    private var defaultAppState by mutableStateOf(false)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { defaultAppState = isDefaultSmsApp() }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        defaultAppState = isDefaultSmsApp()
        requestOptionalPermissions()

        val openThreadId = intent?.getLongExtra(EXTRA_THREAD_ID, 0L) ?: 0L

        setContent {
            AsudehTheme {
                val model: AsudehViewModel = viewModel()
                AsudehApp(
                    model = model,
                    isDefaultApp = defaultAppState,
                    onBecomeDefault = ::requestDefaultSmsRole,
                    initialThreadId = openThreadId,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        defaultAppState = isDefaultSmsApp()
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

    /** مخاطب‌ها اختیاری است، ولی قفل ایمنی D31 با آن بهتر کار می‌کند. */
    private fun requestOptionalPermissions() {
        val wanted = buildList {
            add(android.Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }

    companion object {
        private const val EXTRA_THREAD_ID = "ir.asudehapp.sms.THREAD_ID"

        fun intentFor(context: Context, threadId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_THREAD_ID, threadId)
    }
}

/** ناوبری در این نسخه فقط سه مقصد دارد، پس یک `sealed` ساده کافی است (D56). */
sealed interface Destination {
    data object Home : Destination
    data class FolderView(val folder: Folder) : Destination
    data class Conversation(val threadId: Long, val folder: Folder) : Destination
}
