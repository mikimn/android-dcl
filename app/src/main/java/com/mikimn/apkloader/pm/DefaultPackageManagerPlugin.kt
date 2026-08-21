package com.mikimn.apkloader.pm

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import androidx.core.os.bundleOf


class DefaultPackageManagerPlugin(private val base: PackageManager) : PackageManagerPlugin {
    override fun getActivityInfo(component: ComponentName, flags: Int): ActivityInfo {
        return base.getActivityInfo(component, flags)
    }

    override fun getReceiverInfo(component: ComponentName, flags: Int): ActivityInfo {
        return base.getReceiverInfo(component, flags)
    }

    override fun getServiceInfo(component: ComponentName, flags: Int): ServiceInfo {
        // Quickfix Firebase ComponentDiscoveryService
        if (component.className == "com.google.firebase.components.ComponentDiscoveryService") {
            return ServiceInfo().apply {
                name = component.className
                metaData = bundleOf(
                    "com.google.firebase.components:com.google.firebase.perf.FirebasePerfKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.perf.FirebasePerfRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.auth.FirebaseAuthRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.dynamiclinks.FirebaseDynamicLinksKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.dynamiclinks.internal.FirebaseDynamicLinkRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.firestore.FirebaseFirestoreKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.firestore.FirestoreRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.functions.FirebaseFunctionsKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.functions.FunctionsRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.messaging.FirebaseMessagingKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.messaging.FirebaseMessagingRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.crashlytics.ndk.CrashlyticsNdkRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.crashlytics.FirebaseCrashlyticsKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.crashlytics.CrashlyticsRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.remoteconfig.FirebaseRemoteConfigKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.remoteconfig.RemoteConfigRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.sessions.FirebaseSessionsRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.analytics.connector.internal.AnalyticsConnectorRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.iid.Registrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.installations.FirebaseInstallationsKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.installations.FirebaseInstallationsRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.ktx.FirebaseCommonLegacyRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.FirebaseCommonKtxRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.abt.component.AbtRegistrar" to "com.google.firebase.components.ComponentRegistrar",
                    "com.google.firebase.components:com.google.firebase.datatransport.TransportRegistrar" to "com.google.firebase.components.ComponentRegistrar"
                )
            }
        }

        return base.getServiceInfo(component, flags)
    }

    override fun getProviderInfo(component: ComponentName, flags: Int): ProviderInfo {
        return base.getProviderInfo(component, flags)
    }

    override fun getApplicationInfo(packageName: String, flags: Int): ApplicationInfo {
        return base.getApplicationInfo(packageName, flags).apply {
//            if (metaData == null) {
//                metaData = bundleOf()
//            }
//            if (metaData != null) {
//                metaData.putInt("com.google.android.gms.version", 11910000)
//                metaData.putLong("com.google.android.gms.games.APP_ID", 799978229476L)
//            }
        }
    }

    override fun getPackageInfo(packageName: String, flags: Int): PackageInfo {
        return base.getPackageInfo(packageName, flags)
    }

    override fun resolveActivity(intent: Intent, flags: Int): ResolveInfo? {
        return base.resolveActivity(intent, flags)
    }
}