package com.mikimn.fixture.hello;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Handler;
import android.os.Looper;
import com.mikimn.fixture.common.Probe;

/** A JobService of a loaded app: what WorkManager's SystemJobService is, in miniature. */
public class HelloJobService extends JobService {
    @Override public void onCreate() {
        super.onCreate();
        Probe.log(this, "fx-hello", "job.onCreate");
    }

    @Override public boolean onStartJob(final JobParameters params) {
        Probe.value(this, "fx-hello", "job.start", params.getJobId() + ":" + params.getExtras().getString("fx.job.extra"));
        Probe.value(this, "fx-hello", "job.routingExtras",
            params.getExtras().containsKey("dclJobClass") || params.getExtras().containsKey("dclJobApk"));
        // keep running past onStartJob, then finish through jobFinished(): it needs the host's job engine
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                jobFinished(params, false);
                Probe.log(HelloJobService.this, "fx-hello", "job.finished");
            }
        }, 300);
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        Probe.log(this, "fx-hello", "job.onStopJob");
        return false;
    }

    @Override public void onDestroy() {
        Probe.log(this, "fx-hello", "job.onDestroy");
        super.onDestroy();
    }
}
