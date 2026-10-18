package com.mikimn.fixture.hello;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.mikimn.fixture.common.Probe;

/** Tier 0/1: one activity, no custom resources. Records its lifecycle and what it can see. */
public class HelloActivity extends Activity {
    static final String CHANNEL = "fx-hello";

    private android.content.ServiceConnection serviceConnection;

    // The implicit broadcast below is the point (it exercises delivery by action to a non-exported
    // receiver of a loaded app), so the lint check against it does not apply.
    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Probe.log(this, CHANNEL, "onCreate");
        Probe.value(this, CHANNEL, "packageName", getPackageName());
        Probe.value(this, CHANNEL, "applicationClass", getApplication().getClass().getName());
        Probe.value(this, CHANNEL, "classLoader", getClass().getClassLoader().getClass().getName());
        Probe.value(this, CHANNEL, "shared.direct", com.mikimn.fixture.shared.SharedLib.owner());
        Probe.value(this, CHANNEL, "shared.pkg", com.mikimn.fixture.shared.SharedLib.packageOf(this));
        Probe.value(this, CHANNEL, "applicationContextClass", getApplicationContext().getClass().getName());
        try {
            // by name through the process class loader, the way the framework resolves classes
            Probe.value(this, CHANNEL, "shared.byName", Class.forName("com.mikimn.fixture.shared.SharedLib", true, getClassLoader())
                .getMethod("owner").invoke(null));
        } catch (Exception e) {
            Probe.value(this, CHANNEL, "shared.byName", e.toString());
        }
        // What the launcher passed us (getIntent() of a hosted activity: see DCLActivity.HOST_ONLY_EXTRAS).
        android.content.Intent launch = getIntent();
        Probe.value(this, CHANNEL, "intent.action", launch.getAction());
        Probe.value(this, CHANNEL, "intent.data", launch.getDataString());
        Probe.value(this, CHANNEL, "intent.extra", launch.getStringExtra("fx.extra"));
        Probe.value(this, CHANNEL, "intent.component", launch.getComponent() == null ? null : launch.getComponent().getClassName());
        Probe.value(this, CHANNEL, "intent.package", launch.getComponent() == null ? null : launch.getComponent().getPackageName());
        Probe.value(this, CHANNEL, "intent.hostExtras",
            launch.hasExtra("activityClassName") || launch.hasExtra("apkAssetFileName") || launch.hasExtra("loadedApkName"));
        // Broadcasts to our own receiver: explicit (by class), implicit (by action) and restricted to
        // our own package. The system can't resolve any of them for a loaded app without help.
        sendBroadcast(new Intent(this, HelloReceiver.class).putExtra("via", "explicit"));
        sendBroadcast(new Intent("fx.hello.PING").putExtra("via", "implicit"));
        sendBroadcast(new Intent("fx.hello.PING").setPackage("com.mikimn.fixture.hello").putExtra("via", "package"));
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
        if (getIntent().getBooleanExtra("fx.job", false)) {
            // JobScheduler, the way WorkManager uses it: a job that runs now, and one far in the future that we
            // only list (it must come back naming our own service class) and cancel.
            try {
                android.app.job.JobScheduler scheduler =
                    (android.app.job.JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                android.content.ComponentName service = new android.content.ComponentName(this, HelloJobService.class);
                android.os.PersistableBundle extras = new android.os.PersistableBundle();
                extras.putString("fx.job.extra", "now");
                Probe.value(this, CHANNEL, "job.schedule.now", scheduler.schedule(
                    new android.app.job.JobInfo.Builder(77, service).setExtras(extras).setOverrideDeadline(0).build()));
                Probe.value(this, CHANNEL, "job.schedule.later", scheduler.schedule(
                    new android.app.job.JobInfo.Builder(78, service).setMinimumLatency(60L * 60 * 1000).build()));
                android.app.job.JobInfo later = scheduler.getPendingJob(78);
                Probe.value(this, CHANNEL, "job.pending.service", later == null ? null : later.getService().getClassName());
                Probe.value(this, CHANNEL, "job.pending.extras", later == null ? null : later.getExtras().keySet().toString());
                boolean listed = false;
                for (android.app.job.JobInfo info : scheduler.getAllPendingJobs()) {
                    if (info.getId() == 78 && service.equals(info.getService())) listed = true;
                }
                Probe.value(this, CHANNEL, "job.pending.listed", listed);
                scheduler.cancel(78);
                Probe.value(this, CHANNEL, "job.pending.afterCancel", scheduler.getPendingJob(78) == null);
            } catch (Exception e) {
                Probe.value(this, CHANNEL, "job.error", e.toString());
            }
            return;
        }
        if (getIntent().getBooleanExtra("fx.stopself", false)) {
            // The service stops itself (stopSelf) instead of being stopped by its client.
            startService(new Intent(this, HelloService.class).putExtra("via", "stopSelf"));
            return;
        }
        // Our own service: started, bound, then unbound and stopped again a second later.
        final Intent svc = new Intent(this, HelloService.class);
        startService(svc.putExtra("via", "start"));
        serviceConnection = new android.content.ServiceConnection() {
            @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
                Probe.value(HelloActivity.this, CHANNEL, "service.bound.answer", ((HelloService.LocalBinder) binder).answer());
                new android.os.Handler(getMainLooper()).postDelayed(new Runnable() {
                    @Override public void run() {
                        unbindService(serviceConnection);
                        stopService(svc);
                    }
                }, 1000);
            }
            @Override public void onServiceDisconnected(android.content.ComponentName name) {}
        };
        bindService(new Intent(this, HelloService.class), serviceConnection, Context.BIND_AUTO_CREATE);
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
