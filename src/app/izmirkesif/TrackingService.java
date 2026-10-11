package app.izmirkesif;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.util.Locale;

/** Foreground service that records GPS fixes while tracking is on. */
public class TrackingService extends Service implements LocationListener {
    static final String ACTION_STOP = "app.izmirkesif.STOP";
    static final String CHANNEL = "tracking";
    static final int NOTIF_ID = 1;

    private LocationManager lm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean started;

    private final Runnable periodic = new Runnable() {
        @Override public void run() {
            World w = World.get(TrackingService.this);
            w.saveProgress();
            updateNotification();
            handler.postDelayed(this, 30000);
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (started) return START_STICKY;
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Konum takibi", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            else startForeground(NOTIF_ID, notification());
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        lm = getSystemService(LocationManager.class);
        try {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 4f, this, Looper.getMainLooper());
        } catch (SecurityException e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        started = true;
        World.get(this).startSession();
        handler.postDelayed(periodic, 30000);
        return START_STICKY;
    }

    private Notification notification() {
        World w = World.get(this);
        Intent open = new Intent(this, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pOpen = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent pStop = PendingIntent.getService(this, 1,
                new Intent(this, TrackingService.class).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE);
        String text = w.loaded && !w.districts.isEmpty()
                ? String.format(new Locale("tr"), "%.1f km sokak keşfedildi", w.visitedKm())
                : "Gittiğin yollar kaydediliyor";
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle("İzmir Keşif takipte")
                .setContentText(text)
                .setContentIntent(pOpen)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Durdur", pStop).build())
                .build();
    }

    private void updateNotification() {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, notification());
    }

    @Override public void onLocationChanged(Location l) {
        World.get(this).onFix(l.getLatitude(), l.getLongitude(), l.getTime(), l.hasAccuracy() ? l.getAccuracy() : 30f);
    }

    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
    @Override public void onProviderEnabled(String provider) { }
    @Override public void onProviderDisabled(String provider) { }

    @Override public void onDestroy() {
        handler.removeCallbacks(periodic);
        if (lm != null) lm.removeUpdates(this);
        if (started) World.get(this).endSession();
        started = false;
        super.onDestroy();
    }
}
