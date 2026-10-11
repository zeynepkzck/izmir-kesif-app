package app.izmirkesif;

import java.util.List;

/**
 * Turns a stream of GPS fixes into visited street pieces. Between two consecutive fixes the path
 * is sampled every few metres, so streets passed between fixes are painted too.
 */
final class Matcher {
    static final float MAX_ACCURACY = 40f;

    private float px, py;
    private long pt;
    private boolean has;

    void reset() { has = false; }

    int feed(double lat, double lon, long time, float acc, List<District> ds) {
        if (acc < 0) { reset(); return 0; }
        if (acc > MAX_ACCURACY) return 0;
        float x = Geo.x(lon), y = Geo.y(lat);
        float thr = Math.max(15f, Math.min(25f, acc));
        if (!has) {
            has = true;
            px = x; py = y; pt = time;
            return mark(x, y, 10f, 0, 0, false, ds);
        }
        float dx = x - px, dy = y - py;
        float d = (float) Math.hypot(dx, dy);
        long dt = time - pt;
        if (d < 3f) return 0;
        int marked = 0;
        if (d <= 400f && dt > 0 && dt <= 120000 && d / (dt / 1000f) < 60f) {
            float hx = dx / d, hy = dy / d;
            int steps = (int) Math.ceil(d / 8f);
            for (int i = 1; i <= steps; i++) {
                float f = i / (float) steps;
                marked += mark(px + dx * f, py + dy * f, thr, hx, hy, true, ds);
            }
        } else {
            marked += mark(x, y, 10f, 0, 0, false, ds);
        }
        px = x; py = y; pt = time;
        return marked;
    }

    private static int mark(float x, float y, float thr, float hx, float hy, boolean heading, List<District> ds) {
        int m = 0;
        for (int i = 0; i < ds.size(); i++) m += ds.get(i).markNear(x, y, thr, hx, hy, heading);
        return m;
    }
}
