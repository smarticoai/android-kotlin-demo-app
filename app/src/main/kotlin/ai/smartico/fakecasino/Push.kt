package ai.smartico.fakecasino

import ai.smartico.publicapi.Smartico
import ai.smartico.publicapi.transport.PushEngagementEventType
import ai.smartico.publicapi.transport.PushEngagementRef
import ai.smartico.publicapi.transport.PushPlatform
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Campaign push notifications.
 *
 * Two halves that are easy to confuse: FCM delivers the message to the device,
 * and Smartico wants to know what happened to it. The SDK owns the Smartico
 * half — token registration (cid 1003) and the lifecycle reports — so this file
 * is only the Android plumbing around it.
 *
 * Delivery depends on the message shape. A data-only push wakes
 * [SmarticoMessagingService] and we build the notification ourselves, which is
 * the only case where we can report an impression at display time. A push that
 * carries an FCM `notification` block while the app is backgrounded is rendered
 * by the system without our code running at all — there the tap is the first
 * thing we see, which is why [onTap] retro-reports the earlier events.
 */
object Push {

    const val CHANNEL_ID = "smartico_campaigns"
    private const val TAG = "SmarticoDemo"

    /** Extras carried from a notification tap into MainActivity. */
    const val EXTRA_DP = "dp"
    private const val EXTRA_UID = "engagement_uid"
    private const val EXTRA_MSG_ID = "message_id"

    /**
     * One report per (engagement, event type). A tap retro-reports delivered
     * and impression, which the foreground path may already have sent.
     */
    private val reported = mutableSetOf<String>()

    /** Last (user, token) pair sent, so repeated identifies don't re-send it. */
    private var lastRegistered: String? = null

    /**
     * A tap's deep link, held until the app can actually act on it. A tap that
     * cold-starts the app arrives long before login and navigation exist.
     */
    @Volatile
    private var pendingDp: String? = null

    /**
     * Fetch the FCM token and hand it to Smartico. Safe to call after every
     * identify: the SDK queues the registration until the user is identified,
     * and an unchanged (user, token) pair is skipped here.
     *
     * Best-effort by design — no Play services on the device, a user who
     * declined notifications, or a build without google-services.json all end
     * up here and must not break the app.
     */
    fun register(ctx: Context, extUserId: String) {
        if (extUserId.isEmpty()) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isNullOrEmpty()) return@addOnSuccessListener
                val key = "$extUserId:$token"
                if (key == lastRegistered) return@addOnSuccessListener
                lastRegistered = key
                Smartico.registerPushToken(token, PushPlatform.NATIVE_ANDROID, ctx.packageName)
                android.util.Log.i(TAG, "push token registered for $extUserId — ${token.take(16)}…")
            }
            .addOnFailureListener { e ->
                android.util.Log.i(TAG, "push token unavailable: ${e.message}")
            }
    }

    /** Campaign payload → the reference Smartico identifies the engagement by. */
    fun refFrom(data: Map<String, String>): PushEngagementRef? {
        val uid = data["engagement_uid"] ?: data["engagementUid"] ?: return null
        if (uid.isEmpty()) return null
        return PushEngagementRef(
            engagementUid = uid,
            messageId = data["message_id"] ?: data["messageId"],
            action = data["action"],
        )
    }

    fun report(type: PushEngagementEventType, ref: PushEngagementRef) {
        val key = "${ref.engagementUid}:${type.wire}"
        if (!reported.add(key)) return
        // Queued by the SDK until identify, so a cold-start tap still reports.
        Smartico.reportPushEngagement(type, ref)
    }

    /**
     * The channel must exist before the first notification, and creating it
     * twice is a no-op — so this runs both at startup and before display.
     */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Campaigns",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Missions, tournaments and rewards" }
        ctx.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** True once the user has allowed notifications (always true below API 33). */
    fun allowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Build and post the notification for a data-only campaign push. */
    fun show(ctx: Context, title: String?, body: String?, ref: PushEngagementRef) {
        if (!allowed(ctx)) return
        ensureChannel(ctx)

        val intent = Intent(ctx, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            ref.action?.let { putExtra(EXTRA_DP, it) }
            putExtra(EXTRA_UID, ref.engagementUid)
            ref.messageId?.let { putExtra(EXTRA_MSG_ID, it) }
        }
        // A distinct request code per engagement: with a shared one the system
        // would reuse the first PendingIntent and every tap would carry the
        // first campaign's deep link.
        val requestCode = ref.engagementUid.hashCode()
        val pending = PendingIntent.getActivity(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_push)
            .setContentTitle(title ?: ctx.getString(R.string.app_name))
            .setContentText(body.orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.orEmpty()))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(ctx).notify(requestCode, notification) }
    }

    /**
     * A notification was tapped. The tap proves the whole chain, so it
     * retro-reports what display time could not, and parks the deep link for
     * [flushPendingDp] to run once the app is identified.
     */
    fun onTap(intent: Intent?) {
        val uid = intent?.getStringExtra(EXTRA_UID) ?: return
        val dp = intent.getStringExtra(EXTRA_DP)
        val ref = PushEngagementRef(
            engagementUid = uid,
            messageId = intent.getStringExtra(EXTRA_MSG_ID),
            action = dp,
        )
        report(PushEngagementEventType.DELIVERED, ref)
        report(PushEngagementEventType.IMPRESSION, ref)
        report(PushEngagementEventType.ACTION, ref)
        if (!dp.isNullOrEmpty()) pendingDp = dp

        // Consume the extras: onNewIntent hands the same Intent back on every
        // configuration change, and without this the deep link would re-run.
        intent.removeExtra(EXTRA_UID)
        intent.removeExtra(EXTRA_DP)
        intent.removeExtra(EXTRA_MSG_ID)
    }

    /** Run a parked deep link. Call once the SDK reports the user identified. */
    fun flushPendingDp() {
        val dp = pendingDp ?: return
        pendingDp = null
        android.util.Log.i(TAG, "push deep link → $dp")
        Smartico.dp(dp)
    }
}

/**
 * FCM entry point. Only reached for data-only messages, or for any message
 * while the app is in the foreground — a `notification` message arriving in the
 * background is drawn by the system without waking this service.
 */
class SmarticoMessagingService : FirebaseMessagingService() {

    /**
     * Fires when FCM rotates the token, which can happen without a login. The
     * SDK queues the registration until a user is identified, so sending it
     * straight away is safe.
     */
    override fun onNewToken(token: String) {
        val ext = Sdk.extUserId
        if (ext.isEmpty()) return
        Smartico.registerPushToken(token, PushPlatform.NATIVE_ANDROID, packageName)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val ref = Push.refFrom(message.data) ?: return
        val title = message.notification?.title ?: message.data["title"]
        val body = message.notification?.body ?: message.data["body"]

        Push.show(this, title, body, ref)
        Push.report(PushEngagementEventType.DELIVERED, ref)
        // Display and delivery coincide here: we just drew it ourselves.
        if (Push.allowed(this)) Push.report(PushEngagementEventType.IMPRESSION, ref)
    }
}
