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
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
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
     * Android stops the job when the connection drops, when the user stops Haul from the list of active apps, or to
     * save the system. Must return at once. True asks Android to start it again later, which it does for everything
     * but the user's own stop.
     */
    @Override
    public boolean onStopJob(JobParameters p) {
        boolean byUser = p.getStopReason() == JobParameters.STOP_REASON_USER;
        Downloads.Runner r = runner;
        if (r != null) r.stop(byUser ? Downloads.CANCEL_ALL : Downloads.REQUEUE);
        return !byUser;
    }

    @Override
    public void show(Notification n) {
        JobParameters p = params;
        if (p != null) setNotification(p, Downloads.ONGOING, n, JOB_END_NOTIFICATION_POLICY_REMOVE);
    }
}
