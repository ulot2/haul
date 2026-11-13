package app.haul;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Runs the download queue ({@link Downloads.Runner}) as a user-initiated data transfer job, on Android 14+. Android
 * keeps such a job going while the user is in another app, starts it once there is a connection, and has no daily
 * limit for it. The user can stop it from the notification shade's list of active apps.
 */
@TargetApi(34)
public class HaulJob extends JobService implements Downloads.Host {
    static final int ID = 1;

    private Downloads.Runner runner;
    private JobParameters params;

    /** Asks Android to run the queue. Must be called while Haul is on screen (the share sheet counts). */
    static boolean schedule(Context c) {
        try {
            JobInfo job = new JobInfo.Builder(ID, new ComponentName(c, HaulJob.class))
                    .setUserInitiated(true)
                    // Wi-Fi only: Android holds the job until an unmetered network, and stops it if that goes.
                    .setRequiredNetworkType(Haul.wifiOnly(c) ? JobInfo.NETWORK_TYPE_UNMETERED : JobInfo.NETWORK_TYPE_ANY)
                    .setEstimatedNetworkBytes(50L << 20, 64L << 10)   // a typical short video; only a hint
                    .build();
            return c.getSystemService(JobScheduler.class).schedule(job) == JobScheduler.RESULT_SUCCESS;
        } catch (RuntimeException e) {
            return false;   // the foreground service takes over
        }
    }

    @Override
    public boolean onStartJob(JobParameters p) {
        Downloads.channels(this);
        params = p;
        Downloads.Runner r = runner = new Downloads.Runner(this, this);
        setNotification(p, Downloads.ONGOING, r.ready(), JOB_END_NOTIFICATION_POLICY_REMOVE);
        new Thread(() -> {
            if (r.drain()) jobFinished(p, false);
        }, "haul-download").start();
        return true;
    }

    /**
     * Android stopped the job. Must return at once; true asks Android to start it again later.
     * - The connection dropped: the link goes back in the queue, and the job starts again once there is one.
     * - Anything else (battery or background restrictions, which some phones, Xiaomi's among them, apply to apps
     *   installed from outside the Play Store): the downloads stop with Android's reason, and Haul switches to the
     *   foreground service, which those phones leave alone, for good. A tap on the haul tries it again that way.
     */
    @Override
    public boolean onStopJob(JobParameters p) {
        int reason = p.getStopReason();
        Downloads.Runner r = runner;
        if (reason == JobParameters.STOP_REASON_CONSTRAINT_CONNECTIVITY) {
            if (r != null) r.stop(Downloads.REQUEUE, "");
            return true;
        }
        Haul.prefs(this).edit().putBoolean(Downloads.USE_SERVICE, true).putInt("job_stop_reason", reason).apply();
        boolean byUser = reason == JobParameters.STOP_REASON_USER;
        if (r != null) r.stop(byUser ? Downloads.CANCEL_ALL : Downloads.FAIL_ALL, why(reason));
        return false;
    }

    /** Android's stop reason, in words, for the haul's row. */
    static String why(int reason) {
        String what;
        switch (reason) {
            case JobParameters.STOP_REASON_USER: what = "it was stopped from Android's list of running apps (or the phone closed Haul)"; break;
            case JobParameters.STOP_REASON_BACKGROUND_RESTRICTION: what = "of the phone's background restrictions for Haul"; break;
            case JobParameters.STOP_REASON_APP_STANDBY: what = "of the phone's battery restrictions for Haul"; break;
            case JobParameters.STOP_REASON_QUOTA: what = "Haul used up its background time"; break;
            case JobParameters.STOP_REASON_DEVICE_STATE: what = "of battery saver or the phone's state"; break;
            case JobParameters.STOP_REASON_TIMEOUT: what = "it ran too long"; break;
            case JobParameters.STOP_REASON_SYSTEM_PROCESSING: what = "the phone needed the resources"; break;
            default: what = "of reason " + reason;
        }
        return "Android stopped the download because " + what + ". Tap to try again: Haul now downloads in a way it "
                + "leaves alone.";
    }

    @Override
    public boolean waitsForNetwork() {
        return true;
    }

    @Override
    public void show(Notification n) {
        JobParameters p = params;
        if (p != null) setNotification(p, Downloads.ONGOING, n, JOB_END_NOTIFICATION_POLICY_REMOVE);
    }
}
