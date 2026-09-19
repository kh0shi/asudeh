package ir.asudehapp.sms.telephony

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import ir.asudehapp.sms.classifier.receive.RawSms
import ir.asudehapp.sms.classifier.receive.ReceivePipeline
import ir.asudehapp.sms.classifier.receive.StoredMessage
import ir.asudehapp.sms.classifier.receive.TelephonyStore
import ir.asudehapp.sms.data.AsudehRepository
import ir.asudehapp.sms.data.MessageEntity
import ir.asudehapp.sms.data.SendStatus
import ir.asudehapp.sms.mms.MmsCodec
import ir.asudehapp.sms.mms.MmsPart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * دریافت MMS از MMSC با `SmsManager.downloadMultimediaMessage` (D26). خود
 * سیستم به شبکه وصل می‌شود و پیام را در [MmsFileProvider] می‌نویسد؛ نتیجه به
 * [MmsResultReceiver] می‌رسد.
 */
object MmsDownloader {

    fun download(context: Context, providerId: Long, contentLocation: String, subscriptionId: Int) {
        val name = "dl-$providerId.pdu"
        MmsFileProvider.file(context, name).delete()
        val uri = MmsFileProvider.uri(context, name)
        val intent = Intent(context, MmsResultReceiver::class.java)
            .setAction(MmsResultReceiver.ACTION_DOWNLOADED)
            .setData(Uri.parse("asudeh-mms://download/$providerId"))
            .putExtra(MmsResultReceiver.EXTRA_PROVIDER_ID, providerId)
        grantToPhone(context, uri)
        SmsSender.smsManagerFor(context, subscriptionId)
            .downloadMultimediaMessage(context, contentLocation, uri, null, resultIntent(context, intent))
    }

    /** «دوباره دریافت کن» از داخل گفتگو. */
    suspend fun retry(context: Context, repository: AsudehRepository, providerId: Long): Boolean {
        val info = repository.mms.downloadInfo(providerId) ?: return false
        return runCatching { download(context, providerId, info.contentLocation, info.subscriptionId) }
            .onFailure { Log.w(TAG, "درخواست دریافت MMS ممکن نشد", it) }
            .isSuccess
    }

    /**
     * `M-NotifyResp.ind`: به MMSC می‌گوید پیام رسید، تا دوباره فرستاده نشود.
     * اگر نرسد، بدترین حالت یک اعلان تکراری است که [MmsProviderStore] آن را
     * نادیده می‌گیرد.
     */
    fun acknowledge(context: Context, providerId: Long, transactionId: String, mmsVersion: Int, subscriptionId: Int) {
        if (transactionId.isBlank()) return
        runCatching {
            val name = "ack-$providerId.pdu"
            MmsFileProvider.file(context, name).writeBytes(MmsCodec.composeNotifyResp(transactionId, mmsVersion))
            val uri = MmsFileProvider.uri(context, name)
            grantToPhone(context, uri)
            SmsSender.smsManagerFor(context, subscriptionId).sendMultimediaMessage(context, uri, null, null, null)
        }.onFailure { Log.w(TAG, "تأیید دریافت MMS فرستاده نشد", it) }
    }

    private const val TAG = "AsudehMms"
}

/**
 * ارسال MMS، برای پیوست تصویر و گفتگوی گروهی (D26). `SaveFirst` اینجا هم
 * برقرار است: پیام پیش از ارسال در `OUTBOX` و ایندکس نوشته می‌شود و ارسال
 * ناموفق هرگز بی‌صدا نیست.
 */
class MmsSender(
    private val context: Context,
    private val repository: AsudehRepository,
) {

    suspend fun send(
        recipients: List<String>,
        text: String?,
        attachments: List<MmsPart>,
        subscriptionId: Int = -1,
    ): MessageEntity {
        require(recipients.isNotEmpty()) { "گیرنده‌ای نیست" }
        val message = repository.recordOutgoingMms(recipients, text, attachments, subscriptionId)
        transmit(message, recipients, text, attachments)
        return message
    }

    /** «دوباره بفرست» برای MMS ناموفق؛ PDU از روی partهای ذخیره‌شده دوباره ساخته می‌شود. */
    suspend fun resend(message: MessageEntity) {
        repository.setMmsSendStatus(message.providerId, SendStatus.PENDING)
        val attachments = repository.mms.attachmentData(message.providerId)
        transmit(message, message.participants, message.body.takeIf { it.isNotEmpty() }, attachments)
    }

    private suspend fun transmit(
        message: MessageEntity,
        recipients: List<String>,
        text: String?,
        attachments: List<MmsPart>,
    ) = withContext(Dispatchers.IO) {
        try {
            val pdu = MmsCodec.composeSendReq(
                recipients = recipients,
                text = text,
                attachments = attachments,
                deliveryReport = context.telephonyHost.settings.deliveryReports,
            )
            val name = "out-${message.providerId}.pdu"
            MmsFileProvider.file(context, name).writeBytes(pdu)
            val uri = MmsFileProvider.uri(context, name)
            val intent = Intent(context, MmsResultReceiver::class.java)
                .setAction(MmsResultReceiver.ACTION_SENT)
                .setData(Uri.parse("asudeh-mms://sent/${message.providerId}"))
                .putExtra(MmsResultReceiver.EXTRA_PROVIDER_ID, message.providerId)
            grantToPhone(context, uri)
            SmsSender.smsManagerFor(context, message.subId)
                .sendMultimediaMessage(context, uri, null, null, resultIntent(context, intent))
        } catch (failure: Exception) {
            Log.w(TAG, "ارسال MMS ممکن نشد", failure)
            repository.setMmsSendStatus(message.providerId, SendStatus.FAILED)
            context.telephonyHost.notifier.notifySendFailed(message)
        }
    }

    private companion object {
        const val TAG = "AsudehMmsSend"
    }
}

/**
 * نتیجهٔ دریافت و ارسال MMS از سرویس سیستم. export نشده است؛ فقط
 * PendingIntent خود اپ به آن می‌رسد.
 */
class MmsResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val providerId = intent.getLongExtra(EXTRA_PROVIDER_ID, 0L)
        if (providerId <= 0) return
        val ok = resultCode == Activity.RESULT_OK
        val sendConf = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
        val appContext = context.applicationContext
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_DOWNLOADED -> onDownloaded(appContext, providerId, ok)
                    ACTION_SENT -> onSent(appContext, providerId, ok, sendConf)
                }
            } catch (failure: Throwable) {
                Log.e(TAG, "ثبت نتیجهٔ MMS ناموفق بود", failure)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onDownloaded(context: Context, providerId: Long, ok: Boolean) {
        val host = context.telephonyHost
        val repository = host.repository
        val file = MmsFileProvider.file(context, "dl-$providerId.pdu")
        val pendingEntry = repository.find(MessageEntity.KIND_MMS, providerId)
        val incoming = if (ok && file.exists()) MmsCodec.parseRetrieveConf(file.readBytes()) else null
        file.delete()
        if (incoming == null) {
            // اعلان MMS در provider و ایندکس می‌ماند و از گفتگو دوباره دریافت می‌شود.
            host.notifier.notifyMmsProblem(pendingEntry?.address, pendingEntry?.threadId ?: 0L, saved = true)
            return
        }

        val info = repository.mms.downloadInfo(providerId)
        val stored = repository.mms.completeDownload(providerId, incoming, SimCards.ownNumbers(context))
        val now = System.currentTimeMillis()
        val raw = RawSms(
            address = incoming.from.orEmpty(),
            body = incoming.text.ifEmpty { incoming.subject.orEmpty() },
            sentAt = if (incoming.dateSeconds > 0) incoming.dateSeconds * 1_000 else now,
            receivedAt = pendingEntry?.dateReceived ?: now,
            subscriptionId = info?.subscriptionId ?: pendingEntry?.subId ?: -1,
            isMms = true,
            attachments = incoming.attachments.size,
            hasImage = incoming.attachments.any { it.isImage },
            recipients = if (stored.participants.size > 1) stored.participants else emptyList(),
        )
        // پیام پیش‌تر در provider نوشته شده؛ این `TelephonyStore` فقط شناسه‌اش را برمی‌گرداند.
        val alreadyStored = object : TelephonyStore {
            override suspend fun saveIncoming(raw: RawSms) = StoredMessage(stored.providerId, stored.threadId)
        }
        ReceivePipeline(
            store = alreadyStored,
            index = repository.index,
            notifier = host.notifier,
            classify = { input -> repository.classifier.classify(input) },
            isKnownContact = { address -> Contacts.isKnown(context, address) },
            userRules = { repository.currentUserRules() },
            onError = { stage, error -> Log.w(TAG, "مرحلهٔ $stage شکست خورد", error) },
        ).onReceive(raw)

        MmsDownloader.acknowledge(
            context,
            providerId,
            info?.transactionId ?: incoming.transactionId.orEmpty(),
            incoming.mmsVersion,
            raw.subscriptionId,
        )
    }

    private suspend fun onSent(context: Context, providerId: Long, ok: Boolean, sendConf: ByteArray?) {
        val host = context.telephonyHost
        MmsFileProvider.file(context, "out-$providerId.pdu").delete()
        val response = sendConf?.let(MmsCodec::parseSendConf)
        val sent = ok && (response == null || response.ok)
        host.repository.setMmsSendStatus(
            providerId,
            if (sent) SendStatus.SENT else SendStatus.FAILED,
            response?.messageId,
        )
        if (!sent) {
            host.repository.find(MessageEntity.KIND_MMS, providerId)?.let(host.notifier::notifySendFailed)
        }
    }

    companion object {
        const val ACTION_DOWNLOADED = "ir.asudehapp.sms.MMS_DOWNLOADED"
        const val ACTION_SENT = "ir.asudehapp.sms.MMS_SENT"
        const val EXTRA_PROVIDER_ID = "ir.asudehapp.sms.PROVIDER_ID"
        private const val TAG = "AsudehMmsResult"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/**
 * سرویس MMS سیستم باید بتواند در فایل ما بنویسد یا از آن بخواند. `SmsManager`
 * این دسترسی را خودش می‌دهد؛ این فقط برای گوشی‌هایی است که نمی‌دهند.
 */
private fun grantToPhone(context: Context, uri: Uri) {
    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    runCatching { context.grantUriPermission(PHONE_PACKAGE, uri, flags) }
}

/** نتیجه در extraها می‌آید (مثلاً `M-Send.conf`)، پس PendingIntent باید mutable باشد. */
private fun resultIntent(context: Context, intent: Intent): PendingIntent {
    val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    return PendingIntent.getBroadcast(context, 0, intent, mutable or PendingIntent.FLAG_UPDATE_CURRENT)
}

private const val PHONE_PACKAGE = "com.android.phone"
