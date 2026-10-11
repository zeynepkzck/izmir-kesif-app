package app.izmirkesif;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** One ilçe: overall completion and per-mahalle percentages. */
public class DistrictActivity extends Activity implements World.Listener {
    private World world;
    private String slug;
    private LinearLayout root;
    private long lastKey = -1;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.systemBars(this);
        world = World.get(this);
        slug = getIntent().getStringExtra("slug");
        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.paper(this));
        root = Ui.vertical(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(root);
        setContentView(scroll);
        world.addListener(this);
        refresh();
    }

    @Override protected void onDestroy() {
        world.removeListener(this);
        super.onDestroy();
    }

    @Override public void onWorldChanged() { refresh(); }

    private void refresh() {
        final District d = world.bySlug(slug);
        long key = d == null ? -2 : Math.round(d.visitedLen / 20) * 31 + world.version;
        if (key == lastKey) return; // nothing visible changed
        lastKey = key;
        root.removeAllViews();

        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more);
        more.setContentDescription("Diğer seçenekler");
        more.setBackground(Ui.retro(this, Ui.CARD, 12, 2, Ui.INK, false));
        more.setScaleType(ImageView.ScaleType.CENTER);
        more.setPadding(0, 0, Ui.dp(this, 2), Ui.dp(this, 2));
        more.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (d != null) options(d); }
        });
        root.addView(DistrictsActivity.header(this, null, d != null ? more : null));
        root.addView(Ui.space(this, 1, 18));
        if (d == null) {
            root.addView(Ui.text(this, "Bu ilçe artık indirilmiş değil.", 16, Ui.INK, Ui.REGULAR));
            return;
        }
        int color = World.colorOf(d.name);

        // Hero card
        LinearLayout hero = Ui.horizontal(this);
        hero.setBackground(Ui.retro(this, color, 18, 5, color == Ui.BERRY ? Ui.INK : Ui.BERRY, false));
        Ui.pad(hero, 18, 18, 5);
        Ui.Ring ring = new Ui.Ring(this, Ui.ON_DARK, color == Ui.BERRY ? Ui.INK : Ui.BERRY, color, Ui.ON_DARK);
        ring.set(d.percent());
        hero.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 100), Ui.dp(this, 100)));
        hero.addView(Ui.space(this, 16, 1));
        LinearLayout ht = Ui.vertical(this);
        TextView name = Ui.text(this, d.name, 30, Ui.ON_DARK, Ui.BLACK);
        name.setMaxLines(2);
        ht.addView(name);
        ht.addView(Ui.space(this, 1, 8));
        ht.addView(Ui.text(this, Ui.km(d.visitedLen) + " / " + Ui.km(d.total) + " sokak", 14, Ui.ON_DARK, Ui.BOLD));
        int touched = 0;
        for (int m = 0; m < d.mahNames.length; m++) if (d.mahVisited[m] > 0) touched++;
        if (d.mahNames.length > 0) {
            ht.addView(Ui.space(this, 1, 4));
            ht.addView(Ui.text(this, d.mahNames.length + " mahallenin " + touched + "'inde dolaştın", 14, Ui.ON_DARK, Ui.BOLD));
        }
        hero.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(hero);
        root.addView(Ui.space(this, 1, 14));

        TextView show = Ui.button(this, "Haritada göster", Ui.CARD, Ui.INK);
        android.graphics.drawable.Drawable ic = getDrawable(R.drawable.ic_map);
        int s = Ui.dp(this, 18);
        ic.setBounds(0, 0, s, s);
        show.setCompoundDrawables(ic, null, null, null);
        show.setCompoundDrawablePadding(Ui.dp(this, 8));
        show.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                startActivity(new Intent(DistrictActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_FOCUS, d.slug));
            }
        });
        root.addView(show);

        root.addView(Ui.space(this, 1, 24));
        if (d.mahNames.length == 0) {
            root.addView(Ui.text(this, "Bu ilçe için OpenStreetMap'te mahalle sınırı bulunamadı.", 14, Ui.MUTED, Ui.REGULAR));
            return;
        }
        root.addView(Ui.text(this, "Mahalleler", 24, Ui.INK, Ui.BLACK));
        List<Integer> order = new ArrayList<>();
        for (int m = 0; m < d.mahNames.length; m++) order.add(m);
        Collections.sort(order, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) {
                int c = Double.compare(d.mahPercent(b), d.mahPercent(a));
                return c != 0 ? c : Integer.compare(a, b);
            }
        });
        for (int m : order) {
            boolean seen = d.mahVisited[m] > 0;
            LinearLayout row = Ui.vertical(this);
            row.setBackground(Ui.retro(this, Ui.CARD, 12, 0, Ui.INK, !seen));
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
            LinearLayout line = Ui.horizontal(this);
            line.addView(Ui.text(this, MapView.shortName(d.mahNames[m]), 15, seen ? Ui.INK : Ui.MUTED, Ui.BOLD), new LinearLayout.LayoutParams(0, -2, 1));
            line.addView(Ui.text(this, Ui.pct(d.mahPercent(m)), 14, seen ? color : Ui.MUTED, Ui.BOLD));
            row.addView(line);
            if (seen) {
                Ui.Bar bar = new Ui.Bar(this, color);
                bar.set(d.mahPercent(m));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 10));
                bp.topMargin = Ui.dp(this, 8);
                row.addView(bar, bp);
            }
            TextView sub = Ui.text(this, Ui.km(d.mahVisited[m]) + " / " + Ui.km(d.mahTotal[m]) + (seen ? "" : " · henüz keşfedilmedi"), 12, Ui.MUTED, Ui.REGULAR);
            sub.setPadding(0, Ui.dp(this, 6), 0, 0);
            row.addView(sub);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 8);
            root.addView(row, lp);
        }
    }

    private void options(final District d) {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle(d.name)
                .setItems(new String[]{"Haritayı güncelle (yeniden indir)", "İlçe verisini sil"}, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) world.download(d.name);
                        else world.delete(d);
                        finish();
                    }
                })
                .show();
    }
}
