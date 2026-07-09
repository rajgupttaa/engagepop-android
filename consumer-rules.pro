# EngagePop SDK consumer ProGuard rules.
# The public API + the FCM service must survive minification.
-keep class com.engagepop.EngagePop { *; }
-keep class com.engagepop.EngagePopConfig { *; }
-keep class com.engagepop.EngagePopMessagingService { *; }
