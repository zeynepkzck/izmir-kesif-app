package app.izmirkesif;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/** App-wide state: downloaded districts, the GPS track, progress and downloads. Main thread only. */
final class World {
    static final String[] ILCELER = {
            "Aliağa", "Balçova", "Bayındır", "Bayraklı", "Bergama", "Beydağ", "Bornova", "Buca", "Çeşme",
            "Çiğli", "Dikili", "Foça", "Gaziemir", "Güzelbahçe", "Karabağlar", "Karaburun", "Karşıyaka",
            "Kemalpaşa", "Kınık", "Kiraz", "Konak", "Menderes", "Menemen", "Narlıdere", "Ödemiş",
            "Seferihisar", "Selçuk", "Tire", "Torbalı", "Urla"};
    static final String[] MERKEZ = {
            "Konak", "Karşıyaka", "Bornova", "Buca", "Bayraklı", "Çiğli", "Gaziemir", "Karabağlar",
            "Balçova", "Narlıdere", "Güzelbahçe"};
    static final String[] SERVERS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"};

    /** Colour per ilçe for its painted streets, from the Kurobeni / Midnight / Blackberry palette. */
    static final int[] PALETTE = {Ui.INK, Ui.MIDNIGHT, Ui.BERRY, 0xFF3A6470, 0xFF6E3550, 0xFF4F8591};
    private static final Map<String, Integer> COLORS = new HashMap<>();
    static {
        // Central ilçe picked by hand so neighbours never share a colour.
        String[][] fixed = {{"Konak", "0"}, {"Karşıyaka", "1"}, {"Bornova", "2"}, {"Bayraklı", "5"}, {"Buca", "3"},
                {"Karabağlar", "4"}, {"Gaziemir", "0"}, {"Balçova", "1"}, {"Narlıdere", "2"}, {"Güzelbahçe", "0"},
                {"Çiğli", "2"}, {"Menemen", "0"}, {"Kemalpaşa", "1"}, {"Torbalı", "2"}, {"Menderes", "1"}};
        for (String[] f : fixed) COLORS.put(f[0], PALETTE[Integer.parseInt(f[1])]);
    }

    static int colorOf(String ilce) {
        Integer c = COLORS.get(ilce);
        if (c != null) return c;
        for (int i = 0; i < ILCELER.length; i++) if (ILCELER[i].equals(ilce)) return PALETTE[i % PALETTE.length];
        return PALETTE[0];
    }

    interface Listener { void onWorldChanged(); }

    private static World inst;

    static World get(Context c) {
        if (inst == null) inst = new World(c.getApplicationContext());
        return inst;
    }

    final Context app;
    final File dir, trackFile;
    final Handler main = new Handler(Looper.getMainLooper());
    final List<District> districts = new ArrayList<>();
    boolean loaded;
    /** Bumped whenever the set of districts changes, so the map knows to redraw its base layer. */
    int version;
    boolean tracking;

    double lat = Double.NaN, lon = Double.NaN;
    float acc;

    private final Matcher live = new Matcher();
    private final List<double[]> pending = new ArrayList<>();
    private DataOutputStream track;
    private final List<Listener> listeners = new ArrayList<>();

    // Downloads: name -> status text; queue is processed one at a time.
    final Map<String, String> status = new HashMap<>();
    final Set<String> failed = new HashSet<>();
    private final ArrayDeque<String> queue = new ArrayDeque<>();
    /** Overpass gives each client two slots, so two ilçe download side by side. */
    private static final int PARALLEL = 2;
    private final Map<String, Integer> active = new HashMap<>();
    private long lastNotify;

    private World(Context app) {
        this.app = app;
        dir = new File(app.getFilesDir(), "districts");
        dir.mkdirs();
        trackFile = new File(app.getFilesDir(), "track.bin");
        new Thread(new Runnable() {
            @Override public void run() { loadAll(); }
        }, "load").start();
    }

    // ---- listeners ---------------------------------------------------------------------------

    void addListener(Listener l) { listeners.add(l); }
    void removeListener(Listener l) { listeners.remove(l); }

    void changed() {
        for (Listener l : new ArrayList<>(listeners)) l.onWorldChanged();
    }

    private void changedThrottled() {
        long now = SystemClock.uptimeMillis();
        if (now - lastNotify > 400) { lastNotify = now; changed(); }
    }

    // ---- loading -----------------------------------------------------------------------------

    private void loadAll() {
        final List<District> ds = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().endsWith(".dat")) continue;
                try {
                    District d = District.load(f);
                    d.loadVisited(dir);
                    ds.add(d);
                } catch (Exception e) {
                    f.delete();
                }
            }
        }
        main.post(new Runnable() {
            @Override public void run() {
                districts.addAll(ds);
                version++;
                sortDistricts();
                loaded = true;
                for (double[] p : pending) live.feed(p[0], p[1], (long) p[2], (float) p[3], districts);
                pending.clear();
                changed();
            }
        });
    }

    private void sortDistricts() {
        final Collator c = Collator.tr();
        Collections.sort(districts, new Comparator<District>() {
            @Override public int compare(District a, District b) { return c.compare(a.name, b.name); }
        });
    }

    District byName(String name) {
        for (District d : districts) if (d.name.equals(name)) return d;
        return null;
    }

    District bySlug(String slug) {
        for (District d : districts) if (d.slug.equals(slug)) return d;
        return null;
    }

    District at(float x, float y) {
        for (District d : districts) if (d.contains(x, y)) return d;
        return null;
    }

    double totalKm() { double t = 0; for (District d : districts) t += d.total; return t / 1000; }
    double visitedKm() { double t = 0; for (District d : districts) t += d.visitedLen; return t / 1000; }

    // ---- tracking ----------------------------------------------------------------------------

    void startSession() {
        tracking = true;
        live.reset();
        try {
            track = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(trackFile, true)));
            writePoint(0, 0, System.currentTimeMillis(), -1); // session break
        } catch (IOException ignored) {
        }
        changed();
    }

    void endSession() {
        tracking = false;
        if (track != null) {
            try { track.close(); } catch (IOException ignored) { }
            track = null;
        }
        saveProgress();
        changed();
    }

    void onFix(double la, double lo, long time, float accuracy) {
        lat = la; lon = lo; acc = accuracy;
        writePoint(la, lo, time, accuracy);
        if (!loaded) pending.add(new double[]{la, lo, time, accuracy});
        else live.feed(la, lo, time, accuracy, districts);
        changed();
    }

    private void writePoint(double la, double lo, long time, float accuracy) {
        if (track == null) return;
        try {
            track.writeDouble(la);
            track.writeDouble(lo);
            track.writeLong(time);
            track.writeFloat(accuracy);
            track.flush();
        } catch (IOException ignored) {
        }
    }

    /** Replays the whole recorded track over a freshly downloaded district. */
    private void replay(District d) {
        if (!trackFile.exists()) return;
        List<District> one = Collections.singletonList(d);
        Matcher m = new Matcher();
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(trackFile), 1 << 16));
            try {
                while (true) {
                    double la = in.readDouble(), lo = in.readDouble();
                    long t = in.readLong();
                    float a = in.readFloat();
                    m.feed(la, lo, t, a, one);
                }
            } finally {
                in.close();
            }
        } catch (EOFException ignored) {
        } catch (IOException ignored) {
        }
    }

    void saveProgress() {
        for (District d : districts) {
            if (!d.dirty) continue;
            try {
                d.saveVisited(dir);
                d.dirty = false;
            } catch (IOException ignored) {
            }
        }
    }

    void resetProgress() {
        if (track != null) {
            try { track.close(); } catch (IOException ignored) { }
            track = null;
        }
        trackFile.delete();
        for (District d : districts) {
            d.clearVisited();
            new File(dir, d.slug + ".vis").delete();
            d.dirty = false;
        }
        live.reset();
        if (tracking) {
            try {
                track = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(trackFile, true)));
            } catch (IOException ignored) {
            }
        }
        changed();
    }

    // ---- downloads ---------------------------------------------------------------------------

    boolean isBusy(String name) { return queue.contains(name) || active.containsKey(name); }

    void download(String name) {
        if (isBusy(name)) return;
        failed.remove(name);
        queue.add(name);
        status.put(name, "Sırada");
        pump();
        changed();
    }

    void delete(District d) {
        districts.remove(d);
        version++;
        new File(dir, d.slug + ".dat").delete();
        new File(dir, d.slug + ".vis").delete();
        changed();
    }

    private void pump() {
        while (active.size() < PARALLEL && !queue.isEmpty()) {
            final String name = queue.poll();
            // Each slot starts with a different server so parallel downloads don't queue on one.
            int slot = active.containsValue(0) ? 1 : 0;
            final int first = slot;
            active.put(name, slot);
            status.put(name, "İndiriliyor…");
            new Thread(new Runnable() {
                @Override public void run() { fetch(name, first); }
            }, "download").start();
        }
    }

    private void post(final String name, final String text) {
        main.post(new Runnable() {
            @Override public void run() {
                status.put(name, text);
                changedThrottled();
            }
        });
    }

    private void fetch(final String name, int firstServer) {
        String error = "İndirilemedi";
        District result = null;
        for (int k = 0; k < SERVERS.length; k++) {
            String server = SERVERS[(firstServer + k) % SERVERS.length];
            HttpURLConnection c = null;
            try {
                post(name, "Sunucuya bağlanılıyor…");
                c = (HttpURLConnection) new URL(server).openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(600000);
                c.setDoOutput(true);
                c.setRequestMethod("POST");
                c.setRequestProperty("User-Agent", "IzmirKesif/1.0 (Android)");
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                byte[] body = ("data=" + URLEncoder.encode(Builder.query(name), "UTF-8")).getBytes("UTF-8");
                OutputStream os = c.getOutputStream();
                os.write(body);
                os.close();
                int code = c.getResponseCode();
                if (code != 200) {
                    error = code == 429 || code == 504 ? "Sunucu meşgul, sonra tekrar dene" : "Sunucu hatası (" + code + ")";
                    continue;
                }
                post(name, "Veri hazırlanıyor (sunucu)…");
                InputStream in = new CountingStream(c.getInputStream()) {
                    @Override void progress(long bytes) {
                        post(name, String.format(Locale.US, "İndiriliyor… %.1f MB", bytes / 1048576.0));
                    }
                };
                District d = Builder.build(name, in);
                post(name, "İşleniyor…");
                d.save(dir);
                replay(d);
                d.saveVisited(dir);
                d.dirty = false;
                result = d;
                break;
            } catch (Exception e) {
                error = e instanceof IOException && e.getMessage() != null && e.getMessage().startsWith("Sunucu")
                        ? e.getMessage() : "Bağlantı hatası, tekrar dene";
                if (e.getMessage() != null && e.getMessage().startsWith("Bu ilçe")) { error = e.getMessage(); break; }
            } finally {
                if (c != null) c.disconnect();
            }
        }
        final District done = result;
        final String err = error;
        main.post(new Runnable() {
            @Override public void run() {
                if (done != null) {
                    District old = byName(name);
                    if (old != null) districts.remove(old);
                    districts.add(done);
                    version++;
                    sortDistricts();
                    status.remove(name);
                } else {
                    failed.add(name);
                    status.put(name, err);
                }
                active.remove(name);
                pump();
                changed();
            }
        });
    }

    abstract static class CountingStream extends FilterInputStream {
        private long count, reported;

        CountingStream(InputStream in) { super(in); }

        abstract void progress(long bytes);

        private void add(long n) {
            if (n <= 0) return;
            count += n;
            if (count - reported > 256 * 1024) { reported = count; progress(count); }
        }

        @Override public int read() throws IOException { int b = super.read(); if (b >= 0) add(1); return b; }
        @Override public int read(byte[] b, int o, int l) throws IOException { int n = super.read(b, o, l); add(n); return n; }
    }
}
