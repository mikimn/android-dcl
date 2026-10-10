package com.mikimn.fixture.manifest;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import com.mikimn.fixture.common.Probe;

/**
 * Declared {@code launchMode="singleTop"}. On its first creation it starts itself once more: with
 * singleTop the system must deliver that to the *existing* instance ({@code onNewIntent}) rather
 * than create a second one.
 */
public class SecondActivity extends Activity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Probe.log(this, "nav", "second.onCreate");
        if (!getIntent().getBooleanExtra("again", false)) {
            startActivity(new Intent(this, SecondActivity.class).putExtra("again", true));
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Probe.log(this, "nav", "second.onNewIntent");
    }
}
