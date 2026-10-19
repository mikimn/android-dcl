package com.mikimn.apkloader.dcl

import android.content.Context
import android.content.Intent
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Makes the routing extras of a proxy-slot launch unforgeable. A hosted app's `PendingIntent` is held and fired by
 * other parties, and a *mutable* one (`FLAG_MUTABLE`, the default below API 31) lets the holder `fillIn` extras, i.e.
 * supply routing extras the slot then acts on. `Intent.fillIn` only *adds* extras that are missing (it never
 * replaces one already set), but that is enough: a rewritten intent has no `apkAssetFileName`, which wins over
 * `loadedApkName`, so a holder could add one and have the slot load another APK; and a PendingIntent carrying no
 * class could be given one, reaching an internal, non-exported activity the fixed component kept out of reach. Every launch the loader itself routes is signed with an HMAC over those extras using a per-install
 * secret ([secret]); the proxy slot refuses anything whose extras do not match ([verify]).
 * [ApkPolicy] separately restricts *which* APKs may be named at all.
 */
object RoutingMac {
    const val KEY_MAC = "dclRoutingMac"
    private const val PREFS = "dcl_routing"
    private const val PREF_SECRET = "secret"

    /**
     * The text a signature covers: each routing extra on its own (empty when absent). Not "the effective value":
     * `ApkLoading.apkNameOf` prefers `apkAssetFileName` over `loadedApkName`, so a holder *adding* the former to a
     * signed intent must change the signature even though the latter is still there.
     */
    private fun payload(intent: Intent): ByteArray =
        listOf(DCLActivity.KEY_ACTIVITY_CLASS, DCLActivity.KEY_LOADED_APK_NAME, DCLActivity.KEY_APK_ASSET_FILE_NAME)
            .joinToString("\n") { intent.getStringExtra(it).orEmpty() }.toByteArray()

    fun mac(secret: ByteArray, intent: Intent): String =
        Base64.encodeToString(
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret, "HmacSHA256")) }.doFinal(payload(intent)),
            Base64.NO_WRAP
        )

    /** Signs [intent] over the routing extras it currently carries. Call after they are set. */
    fun sign(secret: ByteArray, intent: Intent) {
        intent.putExtra(KEY_MAC, mac(secret, intent))
    }

    fun verify(secret: ByteArray, intent: Intent?): Boolean {
        val given = intent?.getStringExtra(KEY_MAC) ?: return false
        return MessageDigest.isEqual(given.toByteArray(), mac(secret, intent).toByteArray())
    }

    /** The per-install secret, created on first use and kept in the host's private preferences. */
    @Synchronized
    fun secret(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(PREF_SECRET, null)?.let { return Base64.decode(it, Base64.NO_WRAP) }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        // commit(), not apply(): a signed PendingIntent must still verify after a process death right after this.
        prefs.edit().putString(PREF_SECRET, Base64.encodeToString(fresh, Base64.NO_WRAP)).commit()
        return fresh
    }

    /** [sign] with the process's secret; the intent is left unsigned (and so refused) if there is no Application yet. */
    fun sign(intent: Intent) {
        ApkLoading.currentApplication()?.let { sign(secret(it), intent) }
    }

    fun verify(intent: Intent?): Boolean =
        ApkLoading.currentApplication()?.let { verify(secret(it), intent) } == true
}
