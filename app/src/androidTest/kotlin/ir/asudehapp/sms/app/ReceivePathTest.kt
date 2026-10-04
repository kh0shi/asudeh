package ir.asudehapp.sms.app

import android.app.role.RoleManager
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.ReasonCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * آزمون ابزاری مسیر دریافت (D64، ADR-0003): پیامکی که واقعاً از مودم شبیه‌ساز
 * می‌رسد، در Telephony Provider و در `index.db` هست، **حتی وقتی طبقه‌بند استثنا
 * پرتاب می‌کند**. اگر کسی ترتیب «اول ذخیره، بعد طبقه‌بندی» را برعکس کند، حالت
 * کرش قرمز می‌شود چون پیامک دیگر در provider نیست.
 *
 * پیامک را خود آزمون نمی‌تواند بفرستد (فقط سیستم `SMS_DELIVER` می‌فرستد)، پس
 * `scripts/ci/receive-test.sh` هر آزمون را جدا اجرا می‌کند، منتظر خط
 * `READY <متن>` در logcat می‌ماند و همان متن را با `adb emu sms send` می‌فرستد.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // RoleManager
class ReceivePathTest {

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val app: TestAsudehApplication get() = context.applicationContext as TestAsudehApplication

    @Before
    fun requireDefaultSmsApp() {
        val roles = context.getSystemService(RoleManager::class.java)
        assertTrue(
            "آسوده باید اپ پیش‌فرض پیامک باشد (cmd role add-role-holder android.app.role.SMS ...)",
            roles.isRoleHeld(RoleManager.ROLE_SMS),
        )
    }

    @After
    fun restoreClassifier() {
        app.classifyOverride = null
    }

    @Test
    fun receivedSmsIsStoredAndClassified() {
        val message = receive(awaitBody("normal"))
        assertEquals(Folder.INBOX, message.folder)
        assertFalse("طبقه‌بندی عادی نباید معلق بماند", message.pendingClassify)
        assertNotEquals(ReasonCode.CLASSIFIER_FAILED, message.reasonCode)
    }

    @Test
    fun receivedSmsSurvivesClassifierCrash() {
        app.classifyOverride = { throw IllegalStateException("طبقه‌بند عمداً خراب است (آزمون D64)") }
        val message = receive(awaitBody("crash"))
        // ADR-0003: پیامک گم نمی‌شود؛ در Inbox می‌ماند و بعداً دوباره طبقه‌بندی می‌شود.
        assertEquals(Folder.INBOX, message.folder)
        assertTrue("پیامک با طبقه‌بند خراب باید برای طبقه‌بندی دوباره علامت بخورد", message.pendingClassify)
        assertEquals(ReasonCode.CLASSIFIER_FAILED, message.reasonCode)
    }

    /** متن یکتای این آزمون را اعلام می‌کند تا اسکریپت CI همان را بفرستد. */
    private fun awaitBody(mode: String): String {
        val body = "asudeh-d64-$mode-${UUID.randomUUID().toString().take(8)}"
        Log.i(TAG, "READY $body")
        return body
    }

    /** منتظر می‌ماند تا پیامک هم در provider و هم در ایندکس دیده شود. */
    private fun receive(body: String): MessageEntity {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        var provider: Pair<Long, Long>? = null
        while (System.currentTimeMillis() < deadline) {
            provider = provider ?: findInProvider(body)
            val indexed = provider?.let { (id, thread) -> findInIndex(id, thread) }
            if (indexed != null) return indexed
            Thread.sleep(POLL_MILLIS)
        }
        assertNotNull("پیامک «$body» در Telephony Provider نوشته نشد", provider)
        throw AssertionError("پیامک «$body» در provider هست ولی در index.db نیست")
    }

    private fun findInProvider(body: String): Pair<Long, Long>? =
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.THREAD_ID),
            "${Telephony.Sms.BODY} = ?",
            arrayOf(body),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null
        }

    private fun findInIndex(providerId: Long, threadId: Long): MessageEntity? = runBlocking {
        app.repository.conversationNow(threadId)
            .firstOrNull { it.kind == MessageEntity.KIND_SMS && it.providerId == providerId }
    }

    private companion object {
        const val TAG = "AsudehReceiveTest"
        const val TIMEOUT_MILLIS = 90_000L
        const val POLL_MILLIS = 500L
    }
}
