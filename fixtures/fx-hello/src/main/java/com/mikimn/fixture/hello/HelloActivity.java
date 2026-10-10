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
        Probe.value(this, CHANNEL, "intent.hostExtras",
            launch.hasExtra("activityClassName") || launch.hasExtra("apkAssetFileName") || launch.hasExtra("loadedApkName"));
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
