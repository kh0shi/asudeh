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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import ir.asudehapp.sms.data.AppLanguage
import ir.asudehapp.sms.data.AsudehSettings
import ir.asudehapp.sms.data.DateStyle
import ir.asudehapp.sms.data.ThemeMode
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.SmsUri
import ir.asudehapp.sms.telephony.ActiveConversation
import ir.asudehapp.sms.ui.AsudehTheme
import ir.asudehapp.sms.ui.LocalUiIsPersian

class MainActivity : ComponentActivity() {

    private val model: AsudehViewModel by viewModels()

    private var defaultAppState by mutableStateOf(false)

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        defaultAppState = isDefaultSmsApp()
        // مرحلهٔ دوم اولین اجرا (D47)، چه کاربر قبول کرده باشد چه نه.
        model.finishOnboarding()
        requestPermissions()
        // با پیش‌فرض شدن، اپ به پیامک‌ها دسترسی پیدا می‌کند؛ همگام‌سازی از نو (D21).
        model.sync()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.READ_SMS] == true) model.sync()
        if (granted[Manifest.permission.READ_CONTACTS] == true) model.refreshContactNames()
    }

    /** زبانی که این Activity با آن ساخته شده؛ اگر تنظیمات فرق کند، دوباره ساخته می‌شود. */
    private var appliedLanguage = AppLanguage.SYSTEM

    override fun attachBaseContext(newBase: Context) {
        appliedLanguage = AsudehSettings.storedLanguage(newBase)
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // از اندروید ۱۵ با targetSdk 35 پنجره در هر حال تا لبه‌ها کشیده می‌شود و
        // دیگر با باز شدن کیبورد کوچک نمی‌شود. پس همه‌جا همین حالت را داریم و
        // خود Compose با `imePadding` جای کیبورد را باز می‌کند؛ وگرنه جعبهٔ
        // نوشتن زیر کیبورد می‌رفت.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        defaultAppState = isDefaultSmsApp()
        // کسی که آسوده را از پیش پیش‌فرض کرده، صفحهٔ خوش‌آمد را لازم ندارد.
        if (defaultAppState) model.finishOnboarding()
        // در اولین اجرا مجوزها بعد از صفحهٔ خوش‌آمد خواسته می‌شوند (D47).
        if (model.onboarded.value) requestPermissions()
        if (savedInstanceState == null) handle(intent)

        setContent {
            val settings by model.settings.collectAsState()
            val names by model.contactNames.collectAsState()
            val photos by model.contactPhotos.collectAsState()
            LaunchedEffect(settings.language) {
                if (settings.language != appliedLanguage) {
                    AppLocale.refresh(application, settings.language)
                    recreate()
                }
            }
            val dark = when (settings.theme) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // پوستهٔ Compose فقط محتوا را رنگ می‌کند و به پنجره نمی‌رسد. اگر
            // اینجا به سامانه نگوییم نوارها روشن‌اند یا تاریک، سامانه به
            // پیش‌فرضِ تمِ XML (که روشن است) برمی‌گردد و نوار ناوبری در پوستهٔ
            // تاریک سفید می‌ماند. همان `dark` بالا معیار است، پس هر سه حالتِ
            // ThemeMode درست کار می‌کند.
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            AsudehTheme(darkTheme = dark, dynamicColor = settings.dynamicColor) {
                val clipboard = LocalClipboardManager.current
                CompositionLocalProvider(
                    // ارقام فارسی فقط در رابط فارسی معنی دارند.
                    LocalPersianDigits provides (settings.persianDigits && LocalUiIsPersian.current),
                    LocalUseJalali provides when (settings.dateStyle) {
                        DateStyle.AUTO -> LocalUiIsPersian.current
                        DateStyle.JALALI -> true
                        DateStyle.GREGORIAN -> false
                    },
                    LocalContactNames provides names,
                    LocalContactPhotos provides photos,
                    // هرچه کپی شود، ارقامش لاتین است.
                    LocalClipboardManager provides remember(clipboard) {
                        LatinDigitsClipboard(clipboard)
                    },
                ) {
                    AsudehApp(
                        model = model,
                        isDefaultApp = defaultAppState,
                        onBecomeDefault = ::requestDefaultSmsRole,
                        onContinueReadOnly = {
                            model.finishOnboarding()
                            requestPermissions()
                        },
                        onExit = ::finish,
                    )
                }
            }
        }
    }

    /** اعلان یا `sms:` وقتی اپ باز است (`singleTop`) از اینجا می‌رسد. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** پیش‌نویس با رفتن اپ به پس‌زمینه از دست نمی‌رود (D53). */
    override fun onStop() {
        // اپ دیگر روی صفحه نیست، پس پیامک تازه باید اعلان بگیرد.
        ActiveConversation.clear()
        model.saveDraftNow()
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        ActiveConversation.set(model.openThreadId())
    }

    override fun onResume() {
        super.onResume()
        ActiveConversation.set(model.openThreadId())
        val wasDefault = defaultAppState
        defaultAppState = isDefaultSmsApp()
        // از تنظیمات گوشی پیش‌فرض شده، نه از دکمهٔ اپ.
        if (defaultAppState && !wasDefault) {
            model.finishOnboarding()
            requestPermissions()
            model.sync()
        }
    }

    /** `sms:` از اپ‌های دیگر، «اشتراک‌گذاری»، یا لمس اعلان خود اپ. */
    private fun handle(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val parsed = intent.dataString?.let(SmsUri::parse) ?: return
                if (parsed.recipients.isEmpty()) return
                val body = parsed.body ?: intent.getStringExtra(EXTRA_SMS_BODY)
                model.composeTo(parsed.recipients, body)
                return
            }
            // اشتراک‌گذاری متن یا تصویر: گیرنده را کاربر در «گفتگوی تازه» انتخاب می‌کند.
            Intent.ACTION_SEND -> {
                model.shareIntoNewConversation(
                    Share(text = intent.getStringExtra(Intent.EXTRA_TEXT), image = intent.streamUri()),
                )
                return
            }
        }
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, 0L)
        if (threadId != 0L) {
            model.openFromNotification(threadId)
            return
        }
        intent.getStringExtra(EXTRA_FOLDER)
            ?.let { runCatching { Folder.valueOf(it) }.getOrNull() }
            ?.let(model::openFolderFromNotification)
    }

    @Suppress("DEPRECATION")
    private fun Intent.streamUri(): android.net.Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    /**
     * از اندروید ۱۰ نقش پیامک با `RoleManager` نگه داشته می‌شود. روی بعضی
     * گوشی‌ها `getDefaultSmsPackage` بعد از گرفتن نقش هنوز null برمی‌گرداند،
     * پس اول خود نقش بررسی می‌شود.
     */
    private fun isDefaultSmsApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
                return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
            }
        }
        return Telephony.Sms.getDefaultSmsPackage(this) == packageName
    }

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
        private const val EXTRA_FOLDER = "ir.asudehapp.sms.FOLDER"
        private const val EXTRA_SMS_BODY = "sms_body"

        fun intentFor(context: Context, threadId: Long): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_THREAD_ID, threadId)

        /** لمس اعلان `Digest`: پوشهٔ پیامک‌های پنهان باز می‌شود (D40). */
        fun folderIntent(context: Context, folder: Folder): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_FOLDER, folder.name)
    }
}

/** اشتراک‌گذاری از اپ دیگر: متن و/یا تصویر. */
data class Share(val text: String?, val image: android.net.Uri?)
