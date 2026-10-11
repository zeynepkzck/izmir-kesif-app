package app.izmirkesif;

import android.util.JsonReader;
import android.util.JsonToken;

import java.io.*;
import java.util.*;

/** Turns an Overpass API response for one ilçe into a {@link District}. */
final class Builder {
    static final float PIECE = 40f;

    static final String HIGHWAYS = "motorway|motorway_link|trunk|trunk_link|primary|primary_link|secondary|"
            + "secondary_link|tertiary|tertiary_link|unclassified|residential|living_street|pedestrian";

    static String query(String ilce) {
        // Modest timeout/maxsize: Overpass admits smaller declared jobs sooner.
        return "[out:json][timeout:240][maxsize:536870912];\n"
                + "area[\"boundary\"=\"administrative\"][\"admin_level\"=\"4\"][\"name\"=\"İzmir\"]->.il;\n"
                + "rel(area.il)[\"boundary\"=\"administrative\"][\"admin_level\"=\"6\"][\"name\"=\"" + ilce + "\"]->.ilce;\n"
                + ".ilce map_to_area->.a;\n"
                + ".ilce out geom;\n"
                + "rel(area.a)[\"boundary\"=\"administrative\"][\"admin_level\"~\"^(8|9|10)$\"][\"name\"];\n"
                + "out geom;\n"
                + "(\n"
                + "  way(area.a)[\"highway\"~\"^(" + HIGHWAYS + ")$\"];\n"
                + "  way(area.a)[\"highway\"~\"^(footway|path|steps)$\"][\"name\"];\n"
                + ");\n"
                + "out geom qt;\n";
    }

    static int classOf(String hw) {
        if (hw == null) return -1;
        if (hw.startsWith("motorway") || hw.startsWith("trunk") || hw.startsWith("primary")) return 0;
        if (hw.startsWith("secondary") || hw.startsWith("tertiary")) return 1;
        return 2;
    }

    private static final class Rel {
        String name, level;
        final List<float[]> lines = new ArrayList<>();
    }

    private static final class Way {
        int cls;
        List<float[]> lines;
    }

    private final String ilce;
    private final List<Rel> rels = new ArrayList<>();
    private final List<Way> ways = new ArrayList<>();
    private String remark;

    private Builder(String ilce) { this.ilce = ilce; }

    static District build(String ilce, InputStream in) throws IOException {
        Builder b = new Builder(ilce);
        b.parse(in);
        return b.make();
    }

    // ---- parsing -----------------------------------------------------------------------------

    private void parse(InputStream in) throws IOException {
        JsonReader r = new JsonReader(new BufferedReader(new InputStreamReader(in, "UTF-8"), 1 << 16));
        r.beginObject();
        while (r.hasNext()) {
            String k = r.nextName();
            if (k.equals("elements")) {
                r.beginArray();
                while (r.hasNext()) element(r);
                r.endArray();
            } else if (k.equals("remark")) {
                remark = r.nextString();
            } else {
                r.skipValue();
            }
        }
        r.endObject();
    }

    private void element(JsonReader r) throws IOException {
        String type = null;
        Map<String, String> tags = Collections.emptyMap();
        List<float[]> geom = null;
        List<float[]> memberLines = null;
        r.beginObject();
        while (r.hasNext()) {
            String k = r.nextName();
            if (k.equals("type")) type = r.nextString();
            else if (k.equals("tags")) tags = tags(r);
            else if (k.equals("geometry")) geom = geometry(r);
            else if (k.equals("members")) memberLines = members(r);
            else r.skipValue();
        }
        r.endObject();
        if ("way".equals(type) && geom != null) {
            int c = classOf(tags.get("highway"));
            if (c < 0) return;
            Way w = new Way();
            w.cls = c;
            w.lines = geom;
            ways.add(w);
        } else if ("relation".equals(type) && memberLines != null) {
            Rel rel = new Rel();
            rel.name = tags.get("name");
            rel.level = tags.get("admin_level");
            rel.lines.addAll(memberLines);
            rels.add(rel);
        }
    }

    private static Map<String, String> tags(JsonReader r) throws IOException {
        Map<String, String> m = new HashMap<>();
        r.beginObject();
        while (r.hasNext()) {
            String k = r.nextName();
            if (r.peek() == JsonToken.STRING) m.put(k, r.nextString());
            else r.skipValue();
        }
        r.endObject();
        return m;
    }

    /** A geometry array; null entries (nodes outside the data) split it into several polylines. */
    private static List<float[]> geometry(JsonReader r) throws IOException {
        List<float[]> out = new ArrayList<>(1);
        FloatList cur = new FloatList(32);
        r.beginArray();
        while (r.hasNext()) {
            if (r.peek() == JsonToken.NULL) {
                r.nextNull();
                if (cur.n >= 4) out.add(cur.toArray());
                cur = new FloatList(32);
                continue;
            }
            double lat = Double.NaN, lon = Double.NaN;
            r.beginObject();
            while (r.hasNext()) {
                String k = r.nextName();
                if (k.equals("lat")) lat = r.nextDouble();
                else if (k.equals("lon")) lon = r.nextDouble();
                else r.skipValue();
            }
            r.endObject();
            if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                cur.add(Geo.x(lon));
                cur.add(Geo.y(lat));
            }
        }
        r.endArray();
        if (cur.n >= 4) out.add(cur.toArray());
        return out;
    }

    private static List<float[]> members(JsonReader r) throws IOException {
        List<float[]> out = new ArrayList<>();
        r.beginArray();
        while (r.hasNext()) {
            String type = null, role = "";
            List<float[]> g = null;
            r.beginObject();
            while (r.hasNext()) {
                String k = r.nextName();
                if (k.equals("type")) type = r.nextString();
                else if (k.equals("role")) role = r.nextString();
                else if (k.equals("geometry")) g = geometry(r);
                else r.skipValue();
            }
            r.endObject();
            if ("way".equals(type) && g != null && (role.isEmpty() || role.equals("outer") || role.equals("inner")))
                out.addAll(g);
        }
        r.endArray();
        return out;
    }

    // ---- building ----------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private District make() throws IOException {
        if (ways.isEmpty()) {
            throw new IOException(remark != null ? "Sunucu: " + remark : "Bu ilçe için sokak verisi bulunamadı");
        }
        District d = new District(ilce);

        // İlçe boundary
        for (Rel rel : rels) if ("6".equals(rel.level) && ilce.equals(rel.name)) d.outline.addAll(rel.lines);
        Poly outline = new Poly(d.outline);

        // Mahalle level: whichever of 8/9/10 has the most named areas.
        Map<String, Integer> levelCount = new HashMap<>();
        for (Rel rel : rels) {
            if (rel.name == null || "6".equals(rel.level)) continue;
            Integer c = levelCount.get(rel.level);
            levelCount.put(rel.level, c == null ? 1 : c + 1);
        }
        String level = null;
        int best = 0;
        for (Map.Entry<String, Integer> e : levelCount.entrySet()) {
            if (e.getValue() > best) { best = e.getValue(); level = e.getKey(); }
        }
        List<Rel> mahs = new ArrayList<>();
        if (level != null) for (Rel rel : rels) if (level.equals(rel.level) && rel.name != null) mahs.add(rel);
        Poly[] mp = new Poly[mahs.size()];
        for (int m = 0; m < mp.length; m++) mp[m] = new Poly(mahs.get(m).lines);

        // Cut ways into short pieces, keep those inside the ilçe, and assign mahalle.
        FloatList seg = new FloatList(1 << 16);
        ByteArrayOutputStream cls = new ByteArrayOutputStream(1 << 14);
        List<Short> mahIdx = new ArrayList<>(1 << 14);
        int last = -1;
        for (Way w : ways) {
            for (float[] l : w.lines) {
                for (int i = 0; i + 3 < l.length; i += 2) {
                    float ax = l[i], ay = l[i + 1], bx = l[i + 2], by = l[i + 3];
                    double dl = Math.hypot(bx - ax, by - ay);
                    if (dl < 0.5) continue;
                    int k = (int) Math.ceil(dl / PIECE);
                    for (int j = 0; j < k; j++) {
                        float x1 = ax + (bx - ax) * j / k, y1 = ay + (by - ay) * j / k;
                        float x2 = ax + (bx - ax) * (j + 1) / k, y2 = ay + (by - ay) * (j + 1) / k;
                        float mx = (x1 + x2) / 2, my = (y1 + y2) / 2;
                        if (!outline.isEmpty() && !outline.contains(mx, my)) continue;
                        int m = -1;
                        if (last >= 0 && mp[last].contains(mx, my)) m = last;
                        else for (int q = 0; q < mp.length; q++) if (mp[q].contains(mx, my)) { m = q; break; }
                        if (m >= 0) last = m;
                        seg.add4(x1, y1, x2, y2);
                        cls.write(w.cls);
                        mahIdx.add((short) m);
                    }
                }
            }
        }
        d.n = seg.n / 4;
        if (d.n == 0) throw new IOException("Bu ilçe için sokak verisi bulunamadı");
        d.seg = seg.toArray();
        d.cls = cls.toByteArray();

        // Keep only mahalles that actually contain streets, sorted by name.
        double[] lenSum = new double[mp.length], sx = new double[mp.length], sy = new double[mp.length];
        for (int i = 0; i < d.n; i++) {
            int m = mahIdx.get(i);
            if (m < 0) continue;
            float x1 = d.seg[i * 4], y1 = d.seg[i * 4 + 1], x2 = d.seg[i * 4 + 2], y2 = d.seg[i * 4 + 3];
            double l = Math.hypot(x2 - x1, y2 - y1);
            lenSum[m] += l; sx[m] += (x1 + x2) / 2 * l; sy[m] += (y1 + y2) / 2 * l;
        }
        List<Integer> keep = new ArrayList<>();
        for (int m = 0; m < mp.length; m++) if (lenSum[m] > 0) keep.add(m);
        final Collator col = Collator.tr();
        Collections.sort(keep, new Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return col.compare(mahs.get(a).name, mahs.get(b).name); }
        });
        int[] remap = new int[mp.length];
        Arrays.fill(remap, -1);
        d.mahNames = new String[keep.size()];
        d.mahLabel = new float[keep.size() * 2];
        d.mahLines = new List[keep.size()];
        for (int k = 0; k < keep.size(); k++) {
            int m = keep.get(k);
            remap[m] = k;
            d.mahNames[k] = mahs.get(m).name;
            d.mahLabel[k * 2] = (float) (sx[m] / lenSum[m]);
            d.mahLabel[k * 2 + 1] = (float) (sy[m] / lenSum[m]);
            d.mahLines[k] = mahs.get(m).lines;
        }
        d.mah = new short[d.n];
        for (int i = 0; i < d.n; i++) {
            int m = mahIdx.get(i);
            d.mah[i] = (short) (m < 0 ? -1 : remap[m]);
        }
        d.finish();
        return d;
    }
}
