package com.mikimn.fixture.hello;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.mikimn.fixture.common.Probe;

/** Reports every broadcast it gets, and what the delivered Intent looks like to a loaded app. */
public class HelloReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String via = intent.getStringExtra("via");
        Probe.log(context, "fx-hello", "receiver." + via);
        Probe.value(context, "fx-hello", "receiver.component." + via,
            intent.getComponent() == null ? null : intent.getComponent().getPackageName() + "/" + intent.getComponent().getClassName());
        Probe.value(context, "fx-hello", "receiver.package." + via, intent.getPackage());
        Probe.value(context, "fx-hello", "receiver.routingExtras." + via, intent.hasExtra("dclReceiverClass") || intent.hasExtra("loadedApkName"));
        Probe.value(context, "fx-hello", "receiver.contextClass." + via, context.getClass().getName());
    }
}
