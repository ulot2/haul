package app.haul;

import android.app.Application;

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        HaulService.channels(this);
        // A new process means no download is running: anything marked as going was cut off when Android stopped the app.
        Haul.settle(this);
        // Unpack Python and FFmpeg now (a few seconds the first time), so the first download starts sooner.
        new Thread(() -> {
            try {
                Engine.init(this);
            } catch (Exception ignored) {
                // tried again, with an error shown, when a download starts
            }
        }).start();
    }
}
