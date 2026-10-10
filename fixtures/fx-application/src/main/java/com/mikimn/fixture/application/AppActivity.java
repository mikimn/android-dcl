package com.mikimn.fixture.application;

import android.app.Activity;
import android.os.Bundle;
import com.mikimn.fixture.common.Probe;

public class AppActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Probe.log(this, FxApplication.CHANNEL, "activity.onCreate");
        Probe.value(this, FxApplication.CHANNEL, "activity.applicationClass", getApplication().getClass().getName());
        Probe.value(this, FxApplication.CHANNEL, "activity.sameApplication",
                getApplication() == getApplicationContext());
    }
}
