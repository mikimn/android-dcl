package com.mikimn.fixture.manifest;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.mikimn.fixture.common.Probe;

/** Reports deliveries on the "recv" probe channel (used by ReceiverRegistryTest). */
public class FxReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Probe.log(context, "recv", "onReceive:" + intent.getAction());
    }
}
