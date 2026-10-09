package com.mikimn.fixture.application;

import android.app.Application;
import com.mikimn.fixture.common.Probe;

/**
 * Tier 1: custom Application. {@code app.onCreate} must be recorded exactly once per process no
 * matter how many activities start (the DataStore duplicate-Application regression), and
 * {@code app.singleton} mimics a path-keyed singleton that throws if constructed twice.
 */
public class FxApplication extends Application {
    static final String CHANNEL = "fx-application";
    private static int instances;

    @Override public void onCreate() {
        super.onCreate();
        Probe.log(this, CHANNEL, "app.onCreate");
        Probe.value(this, CHANNEL, "app.class", getClass().getName());
        Probe.value(this, CHANNEL, "app.packageName", getPackageName());
        if (++instances > 1) {
            throw new IllegalStateException("There are multiple FxApplication instances active");
        }
    }
}
