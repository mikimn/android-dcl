package com.mikimn.fixture.manifest;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class PrivateService extends Service {
    @Override public IBinder onBind(Intent intent) { return null; }
}
