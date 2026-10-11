package app.izmirkesif;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the street network and paints visited streets.
 *
 * The full network (often 100k+ line pieces) is far too much to stroke on every frame, so it is
 * rendered on a background thread into a bitmap slightly larger than the screen. While panning or
 * zooming, that bitmap is just moved and scaled; once the gesture settles a sharp one is rendered
 * again. Only the visited streets, labels and the location dot are drawn live.
 */
final class MapView extends View {
    static final int BG = Ui.PAPER;
    static final float MIN_SCALE = 0.003f, MAX_SCALE = 12f;
    private static final float MARGIN = 0.25f; // extra bitmap area on each side, as a fraction of the view
    private static final long SETTLE_MS = 140;

    interface Listener { void onMapMoved(); }

    private final World world;
    Listener listener;

    float cx, cy;          // world point at the screen centre
    float scale = 0.02f;   // pixels per metre
    boolean follow;

    private final float dp;

    // ---- live layer ----
    private final Paint glow, core, labelP, ilceLabelP, dotP, dotRing, haloP, bitmapP;
    private final android.graphics.drawable.Drawable paper;
    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector gestures;
    private final OverScroller scroller;
    private int lastFx, lastFy;
    private final List<RectF> placed = new ArrayList<>();
    private final RectF tmp = new RectF();
    private final Matrix m = new Matrix();

    // ---- cached base layer ----
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Handler renderer;
    private final Object pool = new Object();
    private final List<Bitmap> spare = new ArrayList<>();
    private Bitmap front;
    private float fCx, fCy, fScale;
    private int fW, fH, fPad, fVersion = -1;
    private boolean rendering, again;

    private final Runnable settle = new Runnable() {
        @Override public void run() { requestRender(); }
    };

    MapView(Context c, World world) {
        super(c);
        this.world = world;
        dp = c.getResources().getDisplayMetrics().density;
        paper = Ui.paper(c);
        HandlerThread t = new HandlerThread("map-render");
        t.start();
        renderer = new Handler(t.getLooper());

        glow = stroke(0x33000000, 8f);
        glow.setStrokeCap(Paint.Cap.BUTT); // overlapping round caps would bead along the line
        core = stroke(0xFF000000, 3f);
        labelP = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelP.setColor(Ui.MUTED);
        labelP.setTextSize(10.5f * dp);
        labelP.setLetterSpacing(0.08f);
        labelP.setTypeface(Ui.face(c, Ui.BOLD));
        labelP.setTextAlign(Paint.Align.CENTER);
        labelP.setShadowLayer(2.5f * dp, 0, 0, Ui.PAPER);
        ilceLabelP = new Paint(labelP);
        ilceLabelP.setColor(Ui.INK);
        ilceLabelP.setTextSize(14 * dp);
        ilceLabelP.setLetterSpacing(0.02f);
        ilceLabelP.setTypeface(Ui.face(c, Ui.BLACK));
        dotP = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotP.setColor(Ui.INK);
        dotRing = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotRing.setColor(Ui.INK);
        haloP = new Paint(Paint.ANTI_ALIAS_FLAG);
        haloP.setColor(0x1F281822);
        bitmapP = new Paint(Paint.FILTER_BITMAP_FLAG);

        scroller = new OverScroller(c);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                zoomAt(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
        gestures = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                return true;
            }

            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                follow = false;
                cx += dx / scale;
                cy += dy / scale;
                moved();
                return true;
            }

            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                follow = false;
                lastFx = lastFy = 0;
                scroller.fling(0, 0, (int) -vx, (int) -vy, -100000, 100000, -100000, 100000);
                postInvalidateOnAnimation();
                return true;
            }

            @Override public boolean onDoubleTap(MotionEvent e) {
                zoomAt(2f, e.getX(), e.getY());
                return true;
            }
        });
    }

    private Paint stroke(int color, float widthDp) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(widthDp * dp);
        return p;
    }

    private void zoomAt(float f, float fx, float fy) {
        float ns = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale * f));
        float wx = cx + (fx - getWidth() / 2f) / scale, wy = cy + (fy - getHeight() / 2f) / scale;
        scale = ns;
        if (!follow) {
            cx = wx - (fx - getWidth() / 2f) / scale;
            cy = wy - (fy - getHeight() / 2f) / scale;
        }
        moved();
    }

    private void moved() {
        invalidate();
        ui.removeCallbacks(settle);
        ui.postDelayed(settle, SETTLE_MS);
        if (listener != null) listener.onMapMoved();
    }

    void zoomTo(float x0, float y0, float x1, float y1) {
        int w = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        int h = getHeight() > 0 ? getHeight() : getResources().getDisplayMetrics().heightPixels;
        cx = (x0 + x1) / 2;
        cy = (y0 + y1) / 2;
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, 0.8f * Math.min(w / Math.max(1f, x1 - x0), h / Math.max(1f, y1 - y0))));
        moved();
    }

    void centerOn(float x, float y, float minScale) {
        if (cx == x && cy == y && scale >= minScale) return;
        cx = x;
        cy = y;
        scale = Math.max(scale, minScale);
        moved();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        boolean a = scaleDetector.onTouchEvent(e);
        boolean b = gestures.onTouchEvent(e);
        return a || b || super.onTouchEvent(e);
    }

    @Override public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            int x = scroller.getCurrX(), y = scroller.getCurrY();
            cx += (x - lastFx) / scale;
            cy += (y - lastFy) / scale;
            lastFx = x;
            lastFy = y;
            moved();
            postInvalidateOnAnimation();
        }
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        requestRender();
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        ui.removeCallbacks(settle);
        renderer.getLooper().quitSafely();
    }

    // ---- base layer rendering -----------------------------------------------------------------

    /** Renders the base layer for the current view on the render thread. */
    void requestRender() {
        if (getWidth() == 0) return;
        if (rendering) { again = true; return; }
        rendering = true;
        again = false;
        final Job j = new Job();
        j.cx = cx; j.cy = cy; j.scale = scale; j.w = getWidth(); j.h = getHeight();
        j.pad = (int) (Math.min(j.w, j.h) * MARGIN);
        j.version = world.version;
        j.districts = new ArrayList<>(world.districts);
        renderer.post(new Runnable() {
            @Override public void run() {
                Bitmap r;
                try {
                    r = j.render();
                } catch (Throwable t) { // e.g. out of memory: keep the old picture, try again later
                    r = null;
                }
                final Bitmap b = r;
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (b == null) { rendering = false; return; }
                        Bitmap old = front;
                        front = b;
                        fCx = j.cx; fCy = j.cy; fScale = j.scale; fW = j.w; fH = j.h; fPad = j.pad; fVersion = j.version;
                        if (old != null) synchronized (pool) { spare.add(old); }
                        rendering = false;
                        invalidate();
                        if (again || fVersion != world.version) requestRender();
                    }
                });
            }
        });
    }

    private final class Job {
        float cx, cy, scale;
        int w, h, pad, version;
        List<District> districts;

        Bitmap render() {
            int bw = w + 2 * pad, bh = h + 2 * pad;
            Bitmap b = null;
            synchronized (pool) {
                while (!spare.isEmpty() && b == null) {
                    Bitmap s = spare.remove(spare.size() - 1);
                    if (s.getWidth() == bw && s.getHeight() == bh) b = s; else s.recycle();
                }
            }
            if (b == null) b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            c.drawColor(0, PorterDuff.Mode.CLEAR); // transparent: the paper shows through
            float vx0 = cx - (bw / 2f) / scale, vx1 = cx + (bw / 2f) / scale;
            float vy0 = cy - (bh / 2f) / scale, vy1 = cy + (bh / 2f) / scale;
            c.translate(bw / 2f, bh / 2f);
            c.scale(scale, scale);
            c.translate(-cx, -cy);
            float inv = 1f / scale;

            // Thin lines get cheaper and look no worse without anti-aliasing when zoomed far out.
            boolean aa = scale > 0.008f;
            Paint[] base = new Paint[District.CLASSES];
            int[] colors = {0xFFA69B97, 0xFFBBB1AB, 0xFFCFC6BE};
            float[] widths = {2.4f, 1.7f, 1.15f};
            for (int k = 0; k < base.length; k++) {
                base[k] = new Paint(aa ? Paint.ANTI_ALIAS_FLAG : 0);
                base[k].setStyle(Paint.Style.STROKE);
                base[k].setColor(colors[k]);
                base[k].setStrokeCap(Paint.Cap.ROUND);
                base[k].setStrokeWidth(widths[k] * dp * inv);
            }
            Paint mahP = new Paint(Paint.ANTI_ALIAS_FLAG);
            mahP.setStyle(Paint.Style.STROKE);
            mahP.setColor(0xFFDDD6D0);
            mahP.setStrokeWidth(1.2f * dp * inv);
            Paint outlineP = new Paint(Paint.ANTI_ALIAS_FLAG);
            outlineP.setStyle(Paint.Style.STROKE);
            outlineP.setColor(0xFF6B5A63);
            outlineP.setStrokeWidth(1.4f * dp * inv);
            outlineP.setPathEffect(new DashPathEffect(new float[]{6 * dp * inv, 4 * dp * inv}, 0));
            Paint fill = new Paint();
            fill.setStyle(Paint.Style.FILL);

            int minClass = scale < 0.004f ? 0 : scale < 0.012f ? 1 : 2;
            if (scale > 0.05f) {
                for (District d : districts)
                    if (visible(d, vx0, vy0, vx1, vy1) && d.mahBoundaryLines.length > 0) c.drawLines(d.mahBoundaryLines, mahP);
            }
            for (int k = District.CLASSES - 1; k >= 0; k--) {
                if (k > minClass) continue;
                for (District d : districts) {
                    if (!visible(d, vx0, vy0, vx1, vy1)) continue;
                    int ax = clamp((int) ((vx0 - d.minX) / District.CHUNK) - 1, d.cw), bx = clamp((int) ((vx1 - d.minX) / District.CHUNK) + 1, d.cw);
                    int ay = clamp((int) ((vy0 - d.minY) / District.CHUNK) - 1, d.ch), by = clamp((int) ((vy1 - d.minY) / District.CHUNK) + 1, d.ch);
                    for (int y = ay; y <= by; y++) {
                        for (int x = ax; x <= bx; x++) {
                            float[] a = d.chunks[y * d.cw + x][k];
                            if (a.length > 0) c.drawLines(a, base[k]);
                        }
                    }
                }
            }
            for (District d : districts) {
                if (visible(d, vx0, vy0, vx1, vy1) && d.outlineLines.length > 0) c.drawLines(d.outlineLines, outlineP);
            }
            return b;
        }
    }

    private static boolean visible(District d, float x0, float y0, float x1, float y1) {
        return !(d.maxX < x0 || d.minX > x1 || d.maxY < y0 || d.minY > y1);
    }

    // ---- frame ---------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        paper.setBounds(0, 0, (int) w, (int) h);
        paper.draw(c);
        if (front == null || fVersion != world.version) requestRender();
        if (front != null) {
            float k = scale / fScale;
            m.reset();
            m.postTranslate(-(fW / 2f + fPad), -(fH / 2f + fPad));
            m.postScale(k, k);
            m.postTranslate((fCx - cx) * scale + w / 2, (fCy - cy) * scale + h / 2);
            c.drawBitmap(front, m, bitmapP);
        }

        float vx0 = cx - w / 2 / scale, vx1 = cx + w / 2 / scale;
        float vy0 = cy - h / 2 / scale, vy1 = cy + h / 2 / scale;
        List<District> ds = world.districts;

        // Visited streets, in each ilçe's own colour
        c.save();
        c.translate(w / 2, h / 2);
        c.scale(scale, scale);
        c.translate(-cx, -cy);
        float inv = 1f / scale;
        glow.setStrokeWidth(9f * dp * inv);
        core.setStrokeWidth(3f * dp * inv);
        for (District d : ds) {
            if (d.visLines.n == 0 || !visible(d, vx0, vy0, vx1, vy1)) continue;
            int col = World.colorOf(d.name);
            glow.setColor((col & 0x00FFFFFF) | 0x3A000000);
            c.drawLines(d.visLines.a, 0, d.visLines.n, glow);
        }
        for (District d : ds) {
            if (d.visLines.n == 0 || !visible(d, vx0, vy0, vx1, vy1)) continue;
            core.setColor(World.colorOf(d.name));
            c.drawLines(d.visLines.a, 0, d.visLines.n, core);
        }
        c.restore();

        // Labels
        placed.clear();
        if (scale > 0.09f) {
            for (District d : ds) {
                for (int i = 0; i < d.mahNames.length; i++) {
                    float sx = (d.mahLabel[i * 2] - cx) * scale + w / 2, sy = (d.mahLabel[i * 2 + 1] - cy) * scale + h / 2;
                    if (sx < -100 || sx > w + 100 || sy < -20 || sy > h + 20) continue;
                    label(c, shortName(d.mahNames[i]).toUpperCase(Ui.TR), sx, sy, labelP);
                }
            }
        } else {
            for (District d : ds) {
                float sx = (d.labelX - cx) * scale + w / 2, sy = (d.labelY - cy) * scale + h / 2;
                label(c, d.name, sx, sy, ilceLabelP);
            }
        }

        // Current location
        if (!Double.isNaN(world.lat)) {
            float sx = (Geo.x(world.lon) - cx) * scale + w / 2, sy = (Geo.y(world.lat) - cy) * scale + h / 2;
            float r = Math.max(14 * dp, world.acc * scale);
            c.drawCircle(sx, sy, r, haloP);
            c.drawCircle(sx, sy, 9.5f * dp, dotRing);
            dotP.setColor(Ui.CARD);
            c.drawCircle(sx, sy, 7.5f * dp, dotP);
            dotP.setColor(Ui.INK);
            c.drawCircle(sx, sy, 4.5f * dp, dotP);
        }
    }

    private void label(Canvas c, String text, float x, float y, Paint p) {
        float tw = p.measureText(text) / 2 + 4 * dp, th = p.getTextSize() * 0.7f + 2 * dp;
        tmp.set(x - tw, y - th, x + tw, y + th);
        for (RectF r : placed) if (RectF.intersects(r, tmp)) return;
        placed.add(new RectF(tmp));
        c.drawText(text, x, y + p.getTextSize() * 0.35f, p);
    }

    static String shortName(String n) {
        if (n.endsWith(" Mahallesi")) return n.substring(0, n.length() - 10);
        if (n.endsWith(" Mah.")) return n.substring(0, n.length() - 5);
        return n;
    }

    private static int clamp(int v, int n) { return v < 0 ? 0 : Math.min(v, n - 1); }
}
