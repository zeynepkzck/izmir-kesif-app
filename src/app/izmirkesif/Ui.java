package app.izmirkesif;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/** Retro paper theme: Kurobeni ink, Blackberry and Moscow Midnight accents, Archivo type. */
final class Ui {
    static final int PAPER = 0xFFF1EEE8;
    static final int CARD = 0xFFFBF9F6;
    static final int INK = 0xFF281822;        // Kurobeni
    static final int BERRY = 0xFF48192E;      // Blackberry
    static final int MIDNIGHT = 0xFF204952;   // Moscow Midnight
    static final int BERRY_LIGHT = 0xFFDCCBD2;
    static final int MUTED = 0xFF5A4D53;
    static final int ON_DARK = 0xFFE9E3DD;
    static final int DASHED = 0xFF9A8D92;
    static final Locale TR = new Locale("tr", "TR");

    static final int REGULAR = 0, BOLD = 1, BLACK = 2;
    private static Typeface[] faces;

    static Typeface face(Context c, int style) {
        if (faces == null) {
            faces = new Typeface[3];
            try {
                faces[REGULAR] = c.getResources().getFont(R.font.archivo_medium);
                faces[BOLD] = c.getResources().getFont(R.font.archivo_bold);
                faces[BLACK] = c.getResources().getFont(R.font.archivo_black);
            } catch (Exception e) {
                faces[REGULAR] = Typeface.DEFAULT;
                faces[BOLD] = Typeface.DEFAULT_BOLD;
                faces[BLACK] = Typeface.DEFAULT_BOLD;
            }
        }
        return faces[style];
    }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** The paper texture, tiled (mirrored so the seams don't show). */
    static Drawable paper(Context c) {
        Bitmap b = BitmapFactory.decodeResource(c.getResources(), R.drawable.kagit);
        BitmapDrawable d = new BitmapDrawable(c.getResources(), b);
        d.setTileModeXY(Shader.TileMode.MIRROR, Shader.TileMode.MIRROR);
        return d;
    }

    static GradientDrawable round(Context c, int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** A card with a 2dp ink border and a hard offset shadow, like old print. */
    static Drawable retro(Context c, int fill, float radiusDp, float shadowDp, int shadowColor, boolean dashed) {
        int s = dp(c, shadowDp);
        GradientDrawable shadow = round(c, shadowColor, radiusDp);
        GradientDrawable card = round(c, fill, radiusDp);
        if (dashed) card.setStroke(dp(c, 2), DASHED, dp(c, 6), dp(c, 4));
        else card.setStroke(dp(c, 2), INK);
        if (s == 0) return card;
        LayerDrawable l = new LayerDrawable(new Drawable[]{shadow, card});
        l.setLayerInset(0, s, s, 0, 0);
        l.setLayerInset(1, 0, 0, s, s);
        return l;
    }

    /** Padding for a view whose background is {@link #retro}: content stays off the shadow. */
    static void pad(View v, float h, float vert, float shadowDp) {
        Context c = v.getContext();
        v.setPadding(dp(c, h), dp(c, vert), dp(c, h + shadowDp), dp(c, vert + shadowDp));
    }

    static TextView text(Context c, String s, float sp, int color, int style) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(face(c, style));
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView button(Context c, String s, int fill, int fg) {
        TextView t = text(c, s, 15, fg, fill == CARD ? BOLD : BLACK);
        t.setGravity(Gravity.CENTER);
        t.setBackground(retro(c, fill, 14, 3, INK, false));
        t.setForeground(new RippleDrawable(ColorStateList.valueOf(0x22281822), null, round(c, 0xFFFFFFFF, 14)));
        pad(t, 16, 14, 3);
        t.setClickable(true);
        return t;
    }

    static String pct(double p) {
        if (p > 0 && p < 0.1) return "%" + String.format(TR, "%.2f", p);
        return "%" + String.format(TR, "%.1f", p);
    }

    static String km(double meters) {
        return String.format(TR, meters < 10000 ? "%.1f km" : "%.0f km", meters / 1000);
    }

    static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static void systemBars(Activity a) {
        a.getWindow().setStatusBarColor(PAPER);
        a.getWindow().setNavigationBarColor(PAPER);
        a.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, wDp), dp(c, hDp)));
        return v;
    }

    static View dot(Context c, int color, int sizeDp, boolean empty) {
        View v = new View(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(empty ? 0 : color);
        g.setStroke(dp(c, 2), empty ? DASHED : INK);
        v.setBackground(g);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** Progress bar: ink outline, light Blackberry track, solid fill. */
    static final class Bar extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float fraction;
        private final int color;

        Bar(Context c, int color) {
            super(c);
            this.color = color;
        }

        void set(double percent) {
            fraction = (float) Math.max(0, Math.min(1, percent / 100));
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float b = dp(getContext(), 2), h = getHeight(), w = getWidth();
            float rad = h / 2;
            r.set(b / 2, b / 2, w - b / 2, h - b / 2);
            p.setStyle(Paint.Style.FILL);
            p.setColor(BERRY_LIGHT);
            c.drawRoundRect(r, rad, rad, p);
            if (fraction > 0) {
                c.save();
                Path clip = new Path();
                clip.addRoundRect(r, rad, rad, Path.Direction.CW);
                c.clipPath(clip);
                p.setColor(color);
                c.drawRect(r.left, r.top, r.left + (r.width()) * fraction, r.bottom, p);
                c.restore();
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(b);
            p.setColor(INK);
            c.drawRoundRect(r, rad, rad, p);
        }
    }

    /** Circular progress ring with the percentage in the middle. */
    static final class Ring extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float fraction;
        private String label = "";
        private final int fill, track, hole, textColor;

        Ring(Context c, int fill, int track, int hole, int textColor) {
            super(c);
            this.fill = fill;
            this.track = track;
            this.hole = hole;
            this.textColor = textColor;
        }

        void set(double percent) {
            fraction = (float) Math.max(0, Math.min(1, percent / 100));
            label = pct(percent);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            float s = Math.min(getWidth(), getHeight()), b = dp(getContext(), 2);
            float cx = getWidth() / 2f, cy = getHeight() / 2f, outer = s / 2 - b / 2, inner = outer * 0.72f;
            p.setStyle(Paint.Style.FILL);
            p.setColor(track);
            c.drawCircle(cx, cy, outer, p);
            p.setColor(fill);
            r.set(cx - outer, cy - outer, cx + outer, cy + outer);
            c.drawArc(r, -90, 360 * fraction, true, p);
            p.setColor(hole);
            c.drawCircle(cx, cy, inner, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(b);
            p.setColor(INK);
            c.drawCircle(cx, cy, outer, p);
            c.drawCircle(cx, cy, inner, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(textColor);
            p.setTypeface(face(getContext(), BLACK));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(inner * (label.length() > 5 ? 0.42f : 0.5f));
            c.drawText(label, cx, cy - (p.descent() + p.ascent()) / 2, p);
        }
    }

    private Ui() {}
}
