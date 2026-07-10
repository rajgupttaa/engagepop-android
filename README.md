# EngagePop Android SDK

Native push notifications and in-app messages for your Android app, powered by
[EngagePop](https://engagepop.com).

- Register the device with EngagePop (FCM), receive and display notifications
- Track opens and route deep links
- Know your users: `identify` / `track` / `convert`
- Native in-app popups rendered from your EngagePop campaigns

Requires Android 5.0+ (API 21). Kotlin.

## Before you start

1. Add **Firebase** to your app (Firebase console → add Android app → drop in
   `google-services.json`, apply the `com.google.gms.google-services` plugin).
2. In the EngagePop dashboard, open **Mobile Apps → Register app**, pick
   **Android**, and provide your package name + a Firebase **service-account
   JSON** (Project settings → Service accounts → Generate new private key).
   You'll get a **site key** (`ep_…`) and an **app key** (`epm_…`).

## Install

### Gradle

```kotlin
dependencies {
    implementation("com.engagepop:engagepop-android:0.1.0")
}
```

## Setup

```kotlin
import com.engagepop.EngagePop
import com.engagepop.EngagePopConfig

// e.g. in Application.onCreate()
EngagePop.configure(this, EngagePopConfig(siteKey = "ep_…", appKey = "epm_…"))

// Android 13+ notification permission (call from an Activity):
EngagePop.requestNotificationPermission(this)

// Route taps that carry a deep link:
EngagePop.deepLinkHandler = { url -> /* navigate to url */ }
```

Forward notification taps from your launcher Activity so opens are tracked:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    EngagePop.handleNotificationOpen(intent)
}
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    EngagePop.handleNotificationOpen(intent)
}
```

The SDK registers its own `FirebaseMessagingService`. If your app already has
one, remove ours from the merged manifest and forward the callbacks:

```kotlin
override fun onNewToken(token: String) { EngagePop.handleNewToken(token) }
override fun onMessageReceived(msg: RemoteMessage) { EngagePop.handleRemoteMessage(this, msg) }
```

## Know your users

```kotlin
EngagePop.identify(mapOf("name" to "Sarah", "plan" to "Pro"))
EngagePop.track("purchase", mapOf("product" to "Blue Sneakers"))
EngagePop.convert(value = 49.99, order = "1234", campaignId = 12)
EngagePop.reset() // on logout
```

## In-app popups

Once configured, the SDK fetches your published **popup** campaigns and shows the
first eligible one (A/B variants, audience conditions, and frequency rules
apply). `{{merge tags}}` fill from your identify attributes. Re-check after a
screen change with:

```kotlin
EngagePop.refreshInAppMessages()
```

The native renderer covers the common blocks (heading, text, image, button,
email capture, divider, spacer); richer blocks render on web today.

To control where/when a popup appears, turn off auto-show and trigger it
yourself:

```kotlin
EngagePop.configure(this, EngagePopConfig(siteKey = "ep_…", appKey = "epm_…",
                                          autoShowInAppMessages = false))
// …then, on a screen where a popup is OK:
EngagePop.refreshInAppMessages()
```

## Notification inbox / bell

The SDK keeps a local history so you can build an in-app inbox/bell:

```kotlin
val messages = EngagePop.inbox?.messages() ?: emptyList()   // newest first
val unread   = EngagePop.inbox?.unreadCount() ?: 0

EngagePop.inbox?.markRead(message.id)
EngagePop.inbox?.markAllRead()
EngagePop.inbox?.clear()

// Refresh your badge when it changes:
EngagePop.inbox?.onChanged = { /* reload your bell */ }
```

It captures foreground + data-message notifications automatically (the FCM
service runs in your app process). Notifications delivered purely in the
background as `notification` messages are captured when tapped.

## Publishing (maintainers)

Developed inside the EngagePop monorepo under `sdks/android`, released to Maven
Central as `com.engagepop:engagepop-android` (mirror the directory to the public
`engagepop-android` repo + publish).

## Notes

- Pure logic (merge tags, A/B bucketing, targeting) has JVM unit tests
  (`./gradlew test`). The bucketing hash matches the web loader and iOS SDK, so a
  device sees the same variant across platforms.
