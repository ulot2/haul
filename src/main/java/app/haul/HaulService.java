package app.haul;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/**
 * Runs the download queue ({@link Downloads.Runner}) as a foreground service, so Android does not stop it when the
 * user goes back to TikTok right after sharing. Used on Android 10-13, and on 14+ if Android refuses the job.
 */
public class HaulService extends Service implements Downloads.Host {
    private Downloads.Runner runner;
    private volatile Notification shown;

    @Override
    public void onCreate() {
        super.onCreate();
        Downloads.channels(this);
        runner = new Downloads.Runner(this, this);
    }

    /**
     * Downloads.start only starts this service when no runner is going, so each start gets its own runner thread.
     * When the queue runs out, stopSelfResult(startId) stops the service unless a newer start has come in since.
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Every start must promise the foreground notification at once.
        Notification n = shown;
        startForeground(Downloads.ONGOING, n != null ? n : runner.ready(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        Downloads.Runner r = runner = new Downloads.Runner(this, this);
        new Thread(() -> {
            if (r.drain()) stopSelfResult(startId);
        }, "haul-download").start();
        return START_NOT_STICKY;
    }

    /** A service starts at once on any network, so the runner checks Wi-Fi only itself. */
    @Override
    public boolean waitsForNetwork() {
        return false;
    }

    @Override
    public void show(Notification n) {
        shown = n;
        getSystemService(NotificationManager.class).notify(Downloads.ONGOING, n);
    }

    /**
     * Android 15+ lets a data-sync service run 6 hours a day, then calls this. The service must stop within seconds or
     * Android crashes the app, so everything still going or waiting is marked to try again later.
     */
    @Override
    public void onTimeout(int startId, int fgsType) {
        runner.stop(Downloads.TIME_OUT);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        shown = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
