package app.izmirkesif;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** All 30 ilçe with download state and completion percentage. */
public class DistrictsActivity extends Activity implements World.Listener {
    private World world;
    private LinearLayout list;
    private TextView summary, summarySub;
    private Ui.Ring ring;
    private String lastKey;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.systemBars(this);
        world = World.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackground(Ui.paper(this));
        LinearLayout root = Ui.vertical(this);
        root.setPadding(Ui.dp(this, 16), Ui.dp(this, 20), Ui.dp(this, 16), Ui.dp(this, 28));
        scroll.addView(root);

        root.addView(header(this, "İlçeler", null));
        root.addView(Ui.space(this, 1, 18));

        // Summary card
        LinearLayout card = Ui.horizontal(this);
        card.setBackground(Ui.retro(this, Ui.MIDNIGHT, 16, 4, Ui.INK, false));
        Ui.pad(card, 16, 16, 4);
        ring = new Ui.Ring(this, Ui.ON_DARK, 0xFF3A6470, Ui.MIDNIGHT, Ui.ON_DARK);
        card.addView(ring, new LinearLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64)));
        card.addView(Ui.space(this, 16, 1));
        LinearLayout st = Ui.vertical(this);
        summary = Ui.text(this, "", 18, Ui.ON_DARK, Ui.BLACK);
        summarySub = Ui.text(this, "", 13, 0xFFC8D6D9, Ui.BOLD);
        st.addView(summary);
        st.addView(Ui.space(this, 1, 6));
        st.addView(summarySub);
        card.addView(st, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(card);
        root.addView(Ui.space(this, 1, 16));

        LinearLayout actions = Ui.horizontal(this);
        TextView central = Ui.button(this, "Merkez ilçeler", Ui.INK, Ui.ON_DARK);
        central.setTextSize(14);
        central.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (String n : World.MERKEZ) if (world.byName(n) == null) world.download(n);
            }
        });
        TextView all = Ui.button(this, "Tümünü indir", Ui.CARD, Ui.INK);
        all.setTextSize(14);
        all.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                for (String n : World.ILCELER) if (world.byName(n) == null) world.download(n);
            }
        });
        actions.addView(central, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(Ui.space(this, 10, 1));
        actions.addView(all, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(actions);
        TextView hint = Ui.text(this, "Bir ilçeye dokununca indirilir. İndirilmiş ilçeye dokununca mahalle yüzdelerini görürsün.",
                12, Ui.MUTED, Ui.REGULAR);
        hint.setLineSpacing(0, 1.2f);
        hint.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
        root.addView(hint);

        list = Ui.vertical(this);
        root.addView(list);

        root.addView(Ui.space(this, 1, 24));
        TextView reset = Ui.button(this, "Tüm ilerlemeyi sıfırla", Ui.CARD, Ui.BERRY);
        reset.setTextSize(14);
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { confirmReset(); }
        });
        root.addView(reset);

        setContentView(scroll);
        world.addListener(this);
        refresh();
    }

    /** Back button plus a big title; shared with the ilçe screen. */
    static LinearLayout header(final Activity a, String title, View right) {
        LinearLayout row = Ui.horizontal(a);
        ImageView back = new ImageView(a);
        back.setImageResource(R.drawable.ic_back);
        back.setContentDescription("Geri");
        back.setBackground(Ui.retro(a, Ui.CARD, 12, 2, Ui.INK, false));
        back.setScaleType(ImageView.ScaleType.CENTER);
        back.setPadding(0, 0, Ui.dp(a, 2), Ui.dp(a, 2));
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { a.finish(); }
        });
        row.addView(back, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
        if (title != null) {
            row.addView(Ui.space(a, 12, 1));
            row.addView(Ui.text(a, title, 30, Ui.INK, Ui.BLACK));
        }
        if (right != null) {
            row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
            row.addView(right, new LinearLayout.LayoutParams(Ui.dp(a, 46), Ui.dp(a, 46)));
        }
        return row;
    }

    @Override protected void onResume() {
        super.onResume();
        lastKey = null;
        refresh();
    }

    @Override protected void onDestroy() {
        world.removeListener(this);
        super.onDestroy();
    }

    @Override public void onWorldChanged() { refresh(); }

    private void refresh() {
        // Rebuild only when something shown here changed (GPS fixes arrive every few seconds).
        StringBuilder k = new StringBuilder();
        for (District d : world.districts) k.append(d.name).append(Math.round(d.visitedLen / 50)).append(';');
        for (String n : World.ILCELER) k.append(world.isBusy(n) ? world.status.get(n) : "").append(world.failed.contains(n) ? "!" : "").append(';');
        String key = k.toString();
        if (key.equals(lastKey)) return;
        lastKey = key;

        int have = world.districts.size();
        double tot = world.totalKm();
        ring.set(tot > 0 ? 100 * world.visitedKm() / tot : 0);
        summary.setText(have == 0 ? "Henüz ilçe yok" : have + " ilçe indirildi");
        summarySub.setText(String.format(Ui.TR, "%.1f / %.0f km keşfedildi", world.visitedKm(), tot));
        list.removeAllViews();
        for (final String name : World.ILCELER) {
            final District d = world.byName(name);
            String st = world.status.get(name);
            boolean busy = world.isBusy(name);
            boolean failed = !busy && world.failed.contains(name);
            int color = World.colorOf(name);

            LinearLayout row = Ui.vertical(this);
            row.setBackground(Ui.retro(this, Ui.CARD, 14, 0, Ui.INK, d == null && !busy));
            row.setPadding(Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
            LinearLayout line = Ui.horizontal(this);
            if (d != null || busy) {
                line.addView(Ui.dot(this, color, 14, false));
                line.addView(Ui.space(this, 10, 1));
            }
            line.addView(Ui.text(this, name, 18, d != null || busy ? Ui.INK : Ui.MUTED, Ui.BLACK), new LinearLayout.LayoutParams(0, -2, 1));
            TextView rt;
            if (busy) {
                rt = Ui.text(this, st != null ? st : "İndiriliyor…", 13, Ui.MUTED, Ui.BOLD);
            } else if (d != null) {
                rt = Ui.text(this, Ui.pct(d.percent()), 15, color, Ui.BOLD);
            } else if (failed) {
                rt = Ui.text(this, "Tekrar dene", 14, Ui.BERRY, Ui.BOLD);
            } else {
                rt = Ui.text(this, "İndir", 14, Ui.INK, Ui.BOLD);
                android.graphics.drawable.Drawable ic = getDrawable(R.drawable.ic_download);
                int s = Ui.dp(this, 16);
                ic.setBounds(0, 0, s, s);
                rt.setCompoundDrawables(null, null, ic, null);
                rt.setCompoundDrawablePadding(Ui.dp(this, 4));
            }
            rt.setGravity(Gravity.END);
            line.addView(rt);
            row.addView(line);
            if (d != null) {
                Ui.Bar bar = new Ui.Bar(this, color);
                bar.set(d.percent());
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, Ui.dp(this, 12));
                bp.topMargin = Ui.dp(this, 10);
                row.addView(bar, bp);
                TextView sub = Ui.text(this, Ui.km(d.visitedLen) + " / " + Ui.km(d.total) + " · " + d.mahNames.length + " mahalle", 12, Ui.MUTED, Ui.REGULAR);
                sub.setPadding(0, Ui.dp(this, 8), 0, 0);
                row.addView(sub);
            } else if (failed && st != null) {
                TextView sub = Ui.text(this, st, 12, Ui.MUTED, Ui.REGULAR);
                sub.setPadding(0, Ui.dp(this, 6), 0, 0);
                row.addView(sub);
            }
            row.setClickable(true);
            row.setForeground(getDrawable(android.R.drawable.list_selector_background));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (d != null) {
                        startActivity(new Intent(DistrictsActivity.this, DistrictActivity.class).putExtra("slug", d.slug));
                    } else {
                        world.download(name);
                    }
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = Ui.dp(this, 10);
            list.addView(row, lp);
        }
    }

    private void confirmReset() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
                .setTitle("İlerleme sıfırlansın mı?")
                .setMessage("Kaydedilen tüm yolların ve boyanan sokaklar silinir. İndirilmiş harita verisi kalır.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Sıfırla", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) { world.resetProgress(); }
                })
                .show();
    }
}
