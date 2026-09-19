package ir.asudehapp.sms.classifier.receive

import ir.asudehapp.sms.classifier.Classifier
import ir.asudehapp.sms.model.Category
import ir.asudehapp.sms.model.Folder
import ir.asudehapp.sms.model.MessageInput
import ir.asudehapp.sms.model.NotificationBehavior
import ir.asudehapp.sms.model.Origin
import ir.asudehapp.sms.model.UserRules
import ir.asudehapp.sms.model.Verdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `SaveFirst` و «هیچ پیامکی گم نمی‌شود» (ADR-0003، D23).
 *
 * این آزمون‌ها همان تعهد فنی مانیفست را نگه می‌دارند: هر خطایی بعد از ذخیره رخ
 * بدهد، پیامک هنوز در سیستم هست و کاربر آن را در `Inbox` می‌بیند.
 */
class ReceivePipelineTest {

    private val steps = mutableListOf<String>()
    private val notified = mutableListOf<ClassifiedMessage>()
    private val indexed = mutableListOf<ClassifiedMessage>()
    private val errors = mutableListOf<String>()

    private val store = object : TelephonyStore {
        override suspend fun saveIncoming(raw: RawSms): StoredMessage {
            steps += "store"
            return StoredMessage(providerId = 42, threadId = 7)
        }
    }

    private val index = object : MessageIndex {
        override suspend fun upsert(message: ClassifiedMessage) {
            steps += "index"
            indexed += message
        }
    }

    private val notifier = object : Notifier {
        override suspend fun notify(message: ClassifiedMessage) {
            steps += "notify"
            notified += message
        }
    }

    private fun pipeline(
        classify: suspend (MessageInput) -> Verdict = { Classifier().classify(it) },
        store: TelephonyStore = this.store,
        index: MessageIndex = this.index,
        rules: UserRules = UserRules.EMPTY,
    ) = ReceivePipeline(
        store = store,
        index = index,
        notifier = notifier,
        classify = { steps += "classify"; classify(it) },
        userRules = { rules },
        onError = { stage, _ -> errors += stage },
        // طبقه‌بندی هم‌زمان در همین نخ اجرا می‌شود تا زمان مجازی `runTest` درست بماند.
        classifyScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private val promo = RawSms(
        address = "30001234",
        body = "جشنواره فروش ویژه! تا ۵۰٪ تخفیف روی همه محصولات. لغو۱۱",
        sentAt = 1_000,
        receivedAt = 1_100,
    )

    @Test
    fun `the message is written to the system before anything classifies it`() = runTest {
        pipeline().onReceive(promo)
        assertEquals("store", steps.first())
        assertEquals(listOf("store", "classify", "index", "notify"), steps)
    }

    @Test
    fun `confident live advertising is hidden and not notified`() = runTest {
        val message = pipeline().onReceive(promo)
        assertEquals(Category.PROMO, message.verdict.category)
        assertEquals(Folder.PROMO, message.placement.folder)
        assertEquals(NotificationBehavior.NONE, message.placement.notification)
        assertTrue(message.stored)
        assertTrue(message.indexed)
        assertFalse(message.pendingClassify)
    }

    @Test
    fun `a message the app did not receive live is never moved on its own`() = runTest {
        val message = pipeline().onReceive(promo, origin = Origin.EXTERNAL)
        assertEquals(Folder.INBOX, message.placement.folder)
        assertTrue(message.placement.suggestMove)
    }

    @Test
    fun `a crashing classifier leaves the message in the inbox`() = runTest {
        val message = pipeline(classify = { error("طبقه‌بند خراب شد") }).onReceive(promo)

        assertTrue("پیامک باید ذخیره شده باشد", message.stored)
        assertEquals(Folder.INBOX, message.placement.folder)
        assertTrue(message.pendingClassify)
        assertEquals(Category.UNKNOWN, message.verdict.category)
        assertEquals(1, notified.size)
        assertTrue("classify" in errors)
    }

    @Test
    fun `a classifier that runs past the time limit leaves the message in the inbox`() = runTest {
        val message = pipeline(
            classify = { delay(10_000); Verdict.unclassified() },
        ).onReceive(promo)

        assertTrue(message.stored)
        assertEquals(Folder.INBOX, message.placement.folder)
        assertTrue(message.pendingClassify)
        assertEquals(1, notified.size)
        assertTrue("classify-timeout" in errors)
    }

    @Test
    fun `a failing local index never loses the message`() = runTest {
        val brokenIndex = object : MessageIndex {
            override suspend fun upsert(message: ClassifiedMessage) {
                steps += "index"
                error("Room باز نشد")
            }
        }
        val message = pipeline(index = brokenIndex).onReceive(promo)

        assertTrue(message.stored)
        assertFalse(message.indexed)
        assertEquals("کاربر باید همچنان پیامک را ببیند", 1, notified.size)
        assertTrue("index" in errors)
    }

    @Test
    fun `a failing provider write still notifies the user instead of dropping the message`() =
        runTest {
            val brokenStore = object : TelephonyStore {
                override suspend fun saveIncoming(raw: RawSms): StoredMessage {
                    steps += "store"
                    error("provider در دسترس نیست")
                }
            }
            val message = pipeline(store = brokenStore).onReceive(promo)

            assertFalse(message.stored)
            assertEquals(1, notified.size)
            assertTrue("saveIncoming" in errors)
        }

    @Test
    fun `a classifier that ignores cancellation cannot hold the receiver past the limit`() =
        runBlocking {
            val receiver = ReceivePipeline(
                store = store,
                index = index,
                notifier = notifier,
                // کار CPU که به لغو واکنش نشان نمی‌دهد، مثل یک regex کند.
                classify = { Thread.sleep(3_000); Verdict.unclassified() },
                classifyTimeoutMillis = 100,
            )
            val started = System.nanoTime()
            val message = receiver.onReceive(promo)
            val elapsedMillis = (System.nanoTime() - started) / 1_000_000

            assertTrue("مسیر دریافت $elapsedMillis میلی‌ثانیه منتظر ماند", elapsedMillis < 1_500)
            assertTrue(message.pendingClassify)
            assertEquals(Folder.INBOX, message.placement.folder)
        }

    @Test
    fun `two messages that could not be written to the provider keep separate identities`() =
        runTest {
            val brokenStore = object : TelephonyStore {
                override suspend fun saveIncoming(raw: RawSms): StoredMessage = error("پر است")
            }
            val first = pipeline(store = brokenStore).onReceive(promo)
            val second = pipeline(store = brokenStore).onReceive(promo)

            assertTrue(first.providerId < 0)
            assertTrue(second.providerId < 0)
            assertNotEquals(first.providerId, second.providerId)
            assertNotEquals(first.threadId, second.threadId)
        }

    @Test
    fun `user rules reach the router`() = runTest {
        val rules = UserRules(allowlist = setOf("30001234"))
        val message = pipeline(rules = rules).onReceive(promo)
        assertEquals(Folder.INBOX, message.placement.folder)
    }
}
