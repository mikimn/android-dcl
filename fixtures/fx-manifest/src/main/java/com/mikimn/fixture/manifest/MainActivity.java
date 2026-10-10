package com.mikimn.fixture.manifest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.mikimn.fixture.common.Probe;

/**
 * Only ever *launched* by navigation tests (the other tests just parse the manifest): reports
 * itself, then navigates to {@link SecondActivity}.
 */
public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Probe.log(this, "nav", "main.onCreate");
        startActivity(new Intent(this, SecondActivity.class));
    }
}
