package com.mikimn.fixture.common;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Fixture-side half of the test ProbeChannel protocol (see docs/TESTING.md): append one line per
 * event to {@code <filesDir>/probe/<channel>.log}. Loaded fixtures share the host's data dir, so
 * the instrumentation test can read the file without any shared classes.
 */
public final class Probe {
    private Probe() {}

    public static void log(Context context, String channel, String event) {
        File dir = new File(context.getFilesDir(), "probe");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        try (FileWriter w = new FileWriter(new File(dir, channel + ".log"), true)) {
            w.write(event + "\n");
        } catch (IOException e) {
            throw new RuntimeException("Probe write failed", e);
        }
    }

    public static void value(Context context, String channel, String key, Object value) {
        log(context, channel, key + "=" + value);
    }
}
