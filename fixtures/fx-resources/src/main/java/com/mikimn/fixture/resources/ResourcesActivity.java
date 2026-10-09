package com.mikimn.fixture.resources;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;
import com.mikimn.fixture.common.Probe;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Tier 1: every resource kind the loader has to resolve, reported through the probe. */
public class ResourcesActivity extends Activity {
    static final String CHANNEL = "fx-resources";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TextView title = (TextView) findViewById(R.id.title);
        Probe.log(this, CHANNEL, "onCreate");
        Probe.value(this, CHANNEL, "titleView", title.getText());
        Probe.value(this, CHANNEL, "string", getString(R.string.title));
        Probe.value(this, CHANNEL, "plural.1", getResources().getQuantityString(R.plurals.items, 1, 1));
        Probe.value(this, CHANNEL, "plural.3", getResources().getQuantityString(R.plurals.items, 3, 3));
        Probe.value(this, CHANNEL, "color", Integer.toHexString(getResources().getColor(R.color.accent, getTheme())));
        Probe.value(this, CHANNEL, "dimen", getResources().getDimensionPixelSize(R.dimen.gap));
        Probe.value(this, CHANNEL, "drawable", getDrawable(R.drawable.box) != null);
        Probe.value(this, CHANNEL, "raw", readAll(getResources().openRawResource(R.raw.payload)));
        try {
            Probe.value(this, CHANNEL, "asset", readAll(getAssets().open("asset.txt")));
        } catch (IOException e) {
            Probe.value(this, CHANNEL, "asset", "ERROR " + e);
        }
        Probe.value(this, CHANNEL, "resourceIdPackage", Integer.toHexString(R.string.title >>> 24));
        Probe.value(this, CHANNEL, "identifier",
                getResources().getIdentifier("title", "string", getPackageName()) == R.string.title);
        TypedValue bg = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.colorBackground, bg, true);
        Probe.value(this, CHANNEL, "themeBackground", Integer.toHexString(bg.data));
        Probe.value(this, CHANNEL, "layoutChildren", ((ViewGroup) title.getParent()).getChildCount());
    }

    private static String readAll(InputStream in) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            return r.readLine();
        } catch (IOException e) {
            return "ERROR " + e;
        }
    }
}
