package com.mikimn.fixture.hello;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import com.mikimn.fixture.common.Probe;

/** Tier 0/1: one activity, no custom resources. Records its lifecycle and what it can see. */
public class HelloActivity extends Activity {
    static final String CHANNEL = "fx-hello";

    // The implicit broadcast below is the point (it exercises delivery by action to a non-exported
    // receiver of a loaded app), so the lint check against it does not apply.
    @android.annotation.SuppressLint("UnsafeImplicitIntentLaunch")
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
        // Broadcasts to our own receiver: explicit (by class), implicit (by action) and restricted to
        // our own package. The system can't resolve any of them for a loaded app without help.
        sendBroadcast(new Intent(this, HelloReceiver.class).putExtra("via", "explicit"));
        sendBroadcast(new Intent("fx.hello.PING").putExtra("via", "implicit"));
        sendBroadcast(new Intent("fx.hello.PING").setPackage("com.mikimn.fixture.hello").putExtra("via", "package"));
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
