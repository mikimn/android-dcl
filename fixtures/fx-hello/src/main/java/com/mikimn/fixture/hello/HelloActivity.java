package com.mikimn.fixture.hello;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import com.mikimn.fixture.common.Probe;

/** Tier 0/1: one activity, no custom resources. Records its lifecycle and what it can see. */
public class HelloActivity extends Activity {
    static final String CHANNEL = "fx-hello";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Probe.log(this, CHANNEL, "onCreate");
        Probe.value(this, CHANNEL, "packageName", getPackageName());
        Probe.value(this, CHANNEL, "applicationClass", getApplication().getClass().getName());
        Probe.value(this, CHANNEL, "classLoader", getClass().getClassLoader().getClass().getName());
        // What the launcher passed us (getIntent() of a hosted activity: see DCLActivity.HOST_ONLY_EXTRAS).
        android.content.Intent launch = getIntent();
        Probe.value(this, CHANNEL, "intent.action", launch.getAction());
        Probe.value(this, CHANNEL, "intent.data", launch.getDataString());
        Probe.value(this, CHANNEL, "intent.extra", launch.getStringExtra("fx.extra"));
        Probe.value(this, CHANNEL, "intent.component", launch.getComponent() == null ? null : launch.getComponent().getClassName());
        Probe.value(this, CHANNEL, "intent.package", launch.getComponent() == null ? null : launch.getComponent().getPackageName());
        Probe.value(this, CHANNEL, "intent.hostExtras",
            launch.hasExtra("activityClassName") || launch.hasExtra("apkAssetFileName") || launch.hasExtra("loadedApkName"));
        // What this app sees when it checks its own identity (anti-tamper / installer checks).
        try {
            android.content.pm.PackageManager pm = getPackageManager();
            android.content.pm.PackageInfo pi =
                pm.getPackageInfo("com.mikimn.fixture.hello", android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
            android.content.pm.Signature[] signers =
                pi.signingInfo == null ? null : pi.signingInfo.getApkContentsSigners();
            Probe.value(this, CHANNEL, "sig.count", signers == null ? 0 : signers.length);
            if (signers != null && signers.length > 0) {
                StringBuilder hex = new StringBuilder();
                for (byte b : java.security.MessageDigest.getInstance("SHA-256").digest(signers[0].toByteArray())) {
                    hex.append(String.format("%02x", b));
                }
                Probe.value(this, CHANNEL, "sig.sha256", hex);
            }
            Probe.value(this, CHANNEL, "installer", pm.getInstallSourceInfo("com.mikimn.fixture.hello").getInstallingPackageName());
            // The older identity checks many apps still use.
            Probe.value(this, CHANNEL, "installer.legacy", pm.getInstallerPackageName("com.mikimn.fixture.hello"));
            Probe.value(this, CHANNEL, "uid.isOurs", pm.getPackageUid("com.mikimn.fixture.hello", 0) == android.os.Process.myUid());
            Probe.value(this, CHANNEL, "sig.checkSelf",
                pm.checkSignatures("com.mikimn.fixture.hello", "com.mikimn.fixture.hello") == android.content.pm.PackageManager.SIGNATURE_MATCH);
            if (signers != null && signers.length > 0) {
                Probe.value(this, CHANNEL, "sig.hasOwnCert", pm.hasSigningCertificate("com.mikimn.fixture.hello",
                    signers[0].toByteArray(), android.content.pm.PackageManager.CERT_INPUT_RAW_X509));
            }
        } catch (Exception e) {
            Probe.value(this, CHANNEL, "identity.error", e.toString());
        }
        // Where this app thinks its code lives (libraries reopen their own APK by these paths).
        Probe.value(this, CHANNEL, "path.code", getPackageCodePath());
        Probe.value(this, CHANNEL, "path.resource", getPackageResourcePath());
        Probe.value(this, CHANNEL, "path.sourceDir", getApplicationInfo().sourceDir);
        Probe.value(this, CHANNEL, "path.publicSourceDir", getApplicationInfo().publicSourceDir);
        Probe.value(this, CHANNEL, "path.dataDir", getApplicationInfo().dataDir);
        if (getIntent().getBooleanExtra("fx.webview", false)) {
            // Platform code (WebView) is handed this activity's Context: it must keep working when the
            // Context reports the loaded app's own code paths instead of the host's.
            android.webkit.WebView web = new android.webkit.WebView(this);
            web.setWebViewClient(new android.webkit.WebViewClient() {
                @Override public void onPageFinished(android.webkit.WebView view, String url) {
                    Probe.value(HelloActivity.this, CHANNEL, "webview.finished", view.getTitle());
                }
            });
            setContentView(web);
            web.loadDataWithBaseURL(null, "<html><head><title>fx-webview</title></head><body>hi</body></html>", "text/html", "UTF-8", null);
            return;
        }
        TextView text = new TextView(this);
        text.setText("hello from fx-hello");
        setContentView(text);
    }

    @Override protected void onStart() { super.onStart(); Probe.log(this, CHANNEL, "onStart"); }
    @Override protected void onResume() { super.onResume(); Probe.log(this, CHANNEL, "onResume"); }
    @Override protected void onPause() { super.onPause(); Probe.log(this, CHANNEL, "onPause"); }
    @Override protected void onStop() { super.onStop(); Probe.log(this, CHANNEL, "onStop"); }
    @Override protected void onDestroy() { super.onDestroy(); Probe.log(this, CHANNEL, "onDestroy"); }
}
