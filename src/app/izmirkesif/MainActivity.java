package app.izmirkesif;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements World.Listener, MapView.Listener {
    static final String EXTRA_FOCUS = "focus";

    private World world;
    private MapView map;
    private TextView summary, here, trackBtn, ilceChip, mahChip;
    private LinearLayout chips;
    private Ui.Ring ring;
    private LinearLayout empty;
    private boolean fitted;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.systemBars(this);
        world = World.get(this);

        FrameLayout root = new FrameLayout(this);
        map = new MapView(this, world);
        map.listener = this;
        root.addView(map, new FrameLayout.LayoutParams(-1, -1));

        // Top card: ring, title, km and where the map is centred
        LinearLayout top = Ui.vertical(this);
        top.setBackground(Ui.retro(this, Ui.CARD, 16, 4, Ui.INK, false));
        Ui.pad(top, 14, 12, 4);
        LinearLayout head = Ui.horizontal(this);
        ring = new Ui.Ring(this, Ui.INK, Ui.BERRY_LIGHT, Ui.CARD, Ui.INK);
        head.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 58), Ui.dp(this, 58)));
        head.addView(Ui.space(this, 14, 1));
        LinearLayout titles = Ui.vertical(this);
        titles.addView(Ui.text(this, "İzmir Keşif", 22, Ui.INK, Ui.BLACK));
        titles.addView(Ui.space(this, 1, 4));
        summary = Ui.text(this, "", 13, Ui.MUTED, Ui.BOLD);
        titles.addView(summary);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(head);
        chips = Ui.horizontal(this);
        chips.setPadding(0, Ui.dp(this, 12), 0, 0);
        ilceChip = chip(Ui.INK);
        mahChip = chip(Ui.BERRY);
        chips.addView(ilceChip);
        chips.addView(Ui.space(this, 8, 1));
        chips.addView(mahChip);
        top.addView(chips);
        here = Ui.text(this, "", 13, Ui.MUTED, Ui.REGULAR);
        here.setPadding(0, Ui.dp(this, 10), 0, 0);
        top.addView(here);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        tp.setMargins(Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14), 0);
        root.addView(top, tp);

        // Empty state
        empty = Ui.vertical(this);
        empty.setBackground(Ui.retro(this, Ui.CARD, 16, 4, Ui.INK, false));
        Ui.pad(empty, 20, 18, 4);
        empty.addView(Ui.text(this, "Haritayı çizelim", 22, Ui.INK, Ui.BLACK));
        empty.addView(Ui.space(this, 1, 8));
        TextView et = Ui.text(this, "Önce ilçe verisini indirmem gerekiyor. Merkez ilçelerle başlayabilir ya da istediğin ilçeleri seçebilirsin.\n\n"
                + "Veri indirmeden takibi başlatırsan da yolların kaydedilir; ilçeyi indirdiğinde geçtiğin sokaklar boyanır.",
                14, Ui.INK, Ui.REGULAR);
        et.setLineSpacing(0, 1.2f);
        empty.addView(et);
        empty.addView(Ui.space(this, 1, 16));
        TextView dlCentral = Ui.button(this, "Merkez 11 ilçeyi indir", Ui.INK, Ui.ON_DARK);
        dlCentral.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (String n : World.MERKEZ) if (world.byName(n) == null) world.download(n);
                startActivity(new Intent(MainActivity.this, DistrictsActivity.class));
            }
        });
        empty.addView(dlCentral);
        empty.addView(Ui.space(this, 1, 10));
        TextView pick = Ui.button(this, "İlçe seç", Ui.CARD, Ui.INK);
        pick.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, DistrictsActivity.class)); }
        });
        empty.addView(pick);
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        ep.setMargins(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        root.addView(empty, ep);

        // Bottom bar
        LinearLayout bottom = Ui.horizontal(this);
        TextView districtsBtn = Ui.button(this, "İlçeler", Ui.CARD, Ui.INK);
        icon(districtsBtn, R.drawable.ic_map);
        districtsBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, DistrictsActivity.class)); }
        });
        trackBtn = Ui.button(this, "", Ui.INK, Ui.ON_DARK);
        trackBtn.setTextSize(16);
        trackBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleTracking(); }
        });
        TextView locBtn = Ui.button(this, "", Ui.CARD, Ui.INK);
        locBtn.setContentDescription("Konumuma git");
        icon(locBtn, R.drawable.ic_locate);
        locBtn.setCompoundDrawablePadding(0);
        locBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { centerOnMe(); }
        });
        int h = Ui.dp(this, 58);
        bottom.addView(districtsBtn, new LinearLayout.LayoutParams(-2, h));
        LinearLayout.LayoutParams mid = new LinearLayout.LayoutParams(0, h, 1);
        mid.setMargins(Ui.dp(this, 8), 0, Ui.dp(this, 8), 0);
        bottom.addView(trackBtn, mid);
        bottom.addView(locBtn, new LinearLayout.LayoutParams(h, h));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        bp.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 20));
        root.addView(bottom, bp);

        setContentView(root);
        world.addListener(this);
        handleIntent(getIntent());
        refresh();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent i) {
        final String slug = i == null ? null : i.getStringExtra(EXTRA_FOCUS);
        if (slug == null) return;
        i.removeExtra(EXTRA_FOCUS);
        fitted = true;
        map.post(new Runnable() {
            @Override public void run() {
                District d = world.bySlug(slug);
                if (d != null) { map.follow = false; map.zoomTo(d.minX, d.minY, d.maxX, d.maxY); }
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override protected void onPause() {
        super.onPause();
        world.saveProgress();
    }

    @Override protected void onDestroy() {
        world.removeListener(this);
        super.onDestroy();
    }

    @Override public void onWorldChanged() { refresh(); }

    @Override public void onMapMoved() { updateHere(); }

    private void refresh() {
        if (!fitted && world.loaded && !world.districts.isEmpty()) {
            fitted = true;
            final float[] b = bounds();
            map.post(new Runnable() {
                @Override public void run() { map.zoomTo(b[0], b[1], b[2], b[3]); }
            });
        }
        if (map.follow && !Double.isNaN(world.lat)) {
            map.centerOn(Geo.x(world.lon), Geo.y(world.lat), 0f);
        }
        empty.setVisibility(world.loaded && world.districts.isEmpty() && !anyDownloading() ? View.VISIBLE : View.GONE);
        trackBtn.setText(world.tracking ? "Takibi durdur" : "Takibi başlat");
        trackBtn.setBackground(Ui.retro(this, world.tracking ? Ui.BERRY : Ui.INK, 14, 3, Ui.INK, false));
        icon(trackBtn, world.tracking ? R.drawable.ic_stop : R.drawable.ic_play);
        if (!world.loaded) {
            summary.setText("Yükleniyor…");
        } else if (world.districts.isEmpty()) {
            summary.setText(anyDownloading() ? "İlçe verisi indiriliyor…" : "Henüz ilçe indirilmedi");
        } else {
            double t = world.totalKm(), v = world.visitedKm();
            summary.setText(String.format(Ui.TR, "%.1f / %.0f km sokak keşfedildi", v, t));
        }
        double tot = world.totalKm();
        ring.set(tot > 0 ? 100 * world.visitedKm() / tot : 0);
        updateHere();
        map.invalidate();
    }

    private boolean anyDownloading() {
        for (String n : World.ILCELER) if (world.isBusy(n)) return true;
        return false;
    }

    private void updateHere() {
        float x = map.cx, y = map.cy;
        boolean atMe = map.follow && !Double.isNaN(world.lat);
        if (atMe) {
            x = Geo.x(world.lon);
            y = Geo.y(world.lat);
        }
        District d = world.at(x, y);
        if (d == null) {
            chips.setVisibility(View.GONE);
            here.setVisibility(View.VISIBLE);
            here.setText(world.districts.isEmpty() ? "Gittiğin sokaklar burada renklenecek" : "Haritayı indirdiğin bir ilçeye kaydır");
            return;
        }
        chips.setVisibility(View.VISIBLE);
        here.setVisibility(View.GONE);
        ilceChip.setText(d.name + " " + Ui.pct(d.percent()));
        ((android.graphics.drawable.GradientDrawable) ilceChip.getBackground()).setColor(World.colorOf(d.name));
        int m = d.mahAt(x, y);
        mahChip.setVisibility(m >= 0 ? View.VISIBLE : View.GONE);
        if (m >= 0) mahChip.setText(MapView.shortName(d.mahNames[m]) + " " + Ui.pct(d.mahPercent(m)));
    }

    private TextView chip(int color) {
        TextView t = Ui.text(this, "", 13, Ui.ON_DARK, Ui.BOLD);
        android.graphics.drawable.GradientDrawable g = Ui.round(this, color, 999);
        g.setStroke(Ui.dp(this, 2), Ui.INK);
        t.setBackground(g);
        t.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        t.setSingleLine(true);
        return t;
    }

    private void icon(TextView t, int res) {
        android.graphics.drawable.Drawable d = getDrawable(res);
        int s = Ui.dp(this, 20);
        d.setBounds(0, 0, s, s);
        t.setCompoundDrawables(d, null, null, null);
        t.setCompoundDrawablePadding(t.getText().length() == 0 ? 0 : Ui.dp(this, 8));
    }

    private float[] bounds() {
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (District d : world.districts) {
            x0 = Math.min(x0, d.minX); y0 = Math.min(y0, d.minY);
            x1 = Math.max(x1, d.maxX); y1 = Math.max(y1, d.maxY);
        }
        return new float[]{x0, y0, x1, y1};
    }

    private void centerOnMe() {
        if (Double.isNaN(world.lat)) {
            Location l = lastKnown();
            if (l != null) { world.lat = l.getLatitude(); world.lon = l.getLongitude(); world.acc = l.getAccuracy(); }
        }
        if (Double.isNaN(world.lat)) {
            Toast.makeText(this, world.tracking ? "Konum bekleniyor…" : "Konumunu görmek için takibi başlat", Toast.LENGTH_SHORT).show();
            return;
        }
        map.follow = true;
        map.centerOn(Geo.x(world.lon), Geo.y(world.lat), 0.15f);
    }

    private Location lastKnown() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null;
        try {
            LocationManager lm = getSystemService(LocationManager.class);
            Location a = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (a == null) return b;
            if (b == null) return a;
            return a.getTime() >= b.getTime() ? a : b;
        } catch (Exception e) {
            return null;
        }
    }

    // ---- tracking ----------------------------------------------------------------------------

    private void toggleTracking() {
        if (world.tracking) {
            stopService(new Intent(this, TrackingService.class));
            return;
        }
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 1);
            return;
        }
        startTracking();
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startTracking();
        } else {
            Toast.makeText(this, "Hassas konum izni olmadan sokakları boyayamam", Toast.LENGTH_LONG).show();
        }
    }

    private void startTracking() {
        LocationManager lm = getSystemService(LocationManager.class);
        if (Build.VERSION.SDK_INT >= 28 && !lm.isLocationEnabled()) {
            Toast.makeText(this, "Önce telefonun konum servisini aç", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            return;
        }
        startForegroundService(new Intent(this, TrackingService.class));
        map.follow = true;
        if (world.districts.isEmpty()) {
            Toast.makeText(this, "Takip başladı. İlçe verisini indirince geçtiğin sokaklar boyanacak.", Toast.LENGTH_LONG).show();
        }
    }
}
