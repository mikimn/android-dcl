package com.mikimn.fixture.hello;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import com.mikimn.fixture.common.Probe;

/** Reports its lifecycle on the fx-hello channel, and exposes a binder with a known answer. */
public class HelloService extends Service {
    public class LocalBinder extends Binder {
        public int answer() { return 42; }
    }

    @Override public void onCreate() {
        super.onCreate();
        Probe.log(this, "fx-hello", "service.onCreate");
        Probe.value(this, "fx-hello", "service.application", getApplication().getClass().getName());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Probe.value(this, "fx-hello", "service.start", intent.getStringExtra("via") + ":" + startId);
        Probe.value(this, "fx-hello", "service.start.component", intent.getComponent() == null ? null : intent.getComponent().getClassName());
        Probe.value(this, "fx-hello", "service.start.routingExtras", intent.hasExtra("dclServiceClass") || intent.hasExtra("loadedApkName"));
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) {
        Probe.log(this, "fx-hello", "service.onBind");
        return new LocalBinder();
    }

    @Override public boolean onUnbind(Intent intent) {
        Probe.log(this, "fx-hello", "service.onUnbind");
        return false;
    }

    @Override public void onDestroy() {
        Probe.log(this, "fx-hello", "service.onDestroy");
        super.onDestroy();
    }
}
