package app.izmirkesif;

import java.io.*;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Street network of one ilçe, cut into short pieces, plus what has been visited. */
final class District {
    static final int MAGIC = 0x495A4B31; // "IZK1"
    static final float CELL = 100f;   // matching grid
    static final float CHUNK = 1500f; // render culling grid
    static final int CLASSES = 3;     // 0 main roads, 1 secondary roads, 2 streets

    final String name, slug;

    // Stored data
    int n;
    float[] seg;   // x1,y1,x2,y2 per piece
    byte[] cls;
    short[] mah;   // mahalle index or -1
    String[] mahNames = new String[0];
    float[] mahLabel = new float[0]; // x,y per mahalle
    List<float[]>[] mahLines;        // boundary polylines per mahalle
    List<float[]> outline = new ArrayList<>();

    // Derived
    float[] len;
    double total, visitedLen;
    double[] mahTotal, mahVisited;
    float minX, minY, maxX, maxY;
    float labelX, labelY;
    Poly outlinePoly;
    Poly[] mahPolys;
    float[] outlineLines, mahBoundaryLines;
    private int gw, gh;
    private int[] cellStart, cellItems;
    int cw, ch;
    float[][][] chunks; // [chunk][class] -> line floats

    // Progress
    final BitSet visited = new BitSet();
    final FloatList visLines = new FloatList(256);
    boolean dirty;

    District(String name) {
        this.name = name;
        this.slug = Geo.slug(name);
    }

    // ---- derived data ------------------------------------------------------------------------

    void finish() {
        len = new float[n];
        minX = minY = Float.MAX_VALUE;
        maxX = maxY = -Float.MAX_VALUE;
        mahTotal = new double[mahNames.length];
        mahVisited = new double[mahNames.length];
        total = 0;
        double lx = 0, ly = 0;
        for (int i = 0; i < n; i++) {
            float x1 = seg[i * 4], y1 = seg[i * 4 + 1], x2 = seg[i * 4 + 2], y2 = seg[i * 4 + 3];
            float l = (float) Math.hypot(x2 - x1, y2 - y1);
            len[i] = l;
            total += l;
            lx += (x1 + x2) / 2 * l;
            ly += (y1 + y2) / 2 * l;
            if (mah[i] >= 0) mahTotal[mah[i]] += l;
            minX = Math.min(minX, Math.min(x1, x2)); maxX = Math.max(maxX, Math.max(x1, x2));
            minY = Math.min(minY, Math.min(y1, y2)); maxY = Math.max(maxY, Math.max(y1, y2));
        }
        if (total > 0) { labelX = (float) (lx / total); labelY = (float) (ly / total); }
        outlinePoly = new Poly(outline);
        outlineLines = toLines(outline);
        mahPolys = new Poly[mahNames.length];
        List<float[]> all = new ArrayList<>();
        for (int m = 0; m < mahNames.length; m++) {
            mahPolys[m] = new Poly(mahLines[m]);
            all.addAll(mahLines[m]);
        }
        mahBoundaryLines = toLines(all);
        buildGrid();
        buildChunks();
    }

    static float[] toLines(List<float[]> polylines) {
        FloatList f = new FloatList();
        for (float[] l : polylines)
            for (int i = 0; i + 3 < l.length; i += 2) f.add4(l[i], l[i + 1], l[i + 2], l[i + 3]);
        return f.toArray();
    }

    private void buildGrid() {
        if (n == 0) { gw = gh = 1; cellStart = new int[2]; cellItems = new int[0]; return; }
        gw = (int) ((maxX - minX) / CELL) + 1;
        gh = (int) ((maxY - minY) / CELL) + 1;
        int cells = gw * gh;
        int[] cnt = new int[cells + 1];
        for (int i = 0; i < n; i++) {
            int ax = cx(Math.min(seg[i * 4], seg[i * 4 + 2])), bx = cx(Math.max(seg[i * 4], seg[i * 4 + 2]));
            int ay = cy(Math.min(seg[i * 4 + 1], seg[i * 4 + 3])), by = cy(Math.max(seg[i * 4 + 1], seg[i * 4 + 3]));
            for (int y = ay; y <= by; y++) for (int x = ax; x <= bx; x++) cnt[y * gw + x + 1]++;
        }
        for (int c = 0; c < cells; c++) cnt[c + 1] += cnt[c];
        cellStart = cnt;
        cellItems = new int[cnt[cells]];
        int[] fill = new int[cells];
        for (int i = 0; i < n; i++) {
            int ax = cx(Math.min(seg[i * 4], seg[i * 4 + 2])), bx = cx(Math.max(seg[i * 4], seg[i * 4 + 2]));
            int ay = cy(Math.min(seg[i * 4 + 1], seg[i * 4 + 3])), by = cy(Math.max(seg[i * 4 + 1], seg[i * 4 + 3]));
            for (int y = ay; y <= by; y++) for (int x = ax; x <= bx; x++) {
                int c = y * gw + x;
                cellItems[cellStart[c] + fill[c]++] = i;
            }
        }
    }

    private int cx(float x) { int v = (int) ((x - minX) / CELL); return v < 0 ? 0 : Math.min(v, gw - 1); }
    private int cy(float y) { int v = (int) ((y - minY) / CELL); return v < 0 ? 0 : Math.min(v, gh - 1); }

    private void buildChunks() {
        cw = n == 0 ? 1 : (int) ((maxX - minX) / CHUNK) + 1;
        ch = n == 0 ? 1 : (int) ((maxY - minY) / CHUNK) + 1;
        int[][] cnt = new int[cw * ch][CLASSES];
        for (int i = 0; i < n; i++) cnt[chunkOf(i)][cls[i]] += 4;
        chunks = new float[cw * ch][CLASSES][];
        int[][] fill = new int[cw * ch][CLASSES];
        for (int c = 0; c < cw * ch; c++) for (int k = 0; k < CLASSES; k++) chunks[c][k] = new float[cnt[c][k]];
        for (int i = 0; i < n; i++) {
            int c = chunkOf(i), k = cls[i];
            float[] a = chunks[c][k];
            int f = fill[c][k];
            a[f] = seg[i * 4]; a[f + 1] = seg[i * 4 + 1]; a[f + 2] = seg[i * 4 + 2]; a[f + 3] = seg[i * 4 + 3];
            fill[c][k] = f + 4;
        }
    }

    private int chunkOf(int i) {
        float mx = (seg[i * 4] + seg[i * 4 + 2]) / 2, my = (seg[i * 4 + 1] + seg[i * 4 + 3]) / 2;
        int x = Math.min(cw - 1, Math.max(0, (int) ((mx - minX) / CHUNK)));
        int y = Math.min(ch - 1, Math.max(0, (int) ((my - minY) / CHUNK)));
        return y * cw + x;
    }

    // ---- progress ----------------------------------------------------------------------------

    double percent() { return total <= 0 ? 0 : 100.0 * visitedLen / total; }

    double mahPercent(int m) { return mahTotal[m] <= 0 ? 0 : 100.0 * mahVisited[m] / mahTotal[m]; }

    int mahAt(float x, float y) {
        for (int m = 0; m < mahPolys.length; m++) if (mahPolys[m].contains(x, y)) return m;
        return -1;
    }

    boolean contains(float x, float y) {
        if (!outlinePoly.isEmpty()) return outlinePoly.contains(x, y);
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }

    void markVisited(int i) {
        if (visited.get(i)) return;
        visited.set(i);
        visitedLen += len[i];
        if (mah[i] >= 0) mahVisited[mah[i]] += len[i];
        visLines.add4(seg[i * 4], seg[i * 4 + 1], seg[i * 4 + 2], seg[i * 4 + 3]);
        dirty = true;
    }

    void clearVisited() {
        visited.clear();
        visitedLen = 0;
        java.util.Arrays.fill(mahVisited, 0);
        visLines.n = 0;
        dirty = true;
    }

    /**
     * Marks pieces near (x,y). With a heading, only pieces running roughly along the direction of
     * travel count, so crossing streets at intersections are not painted.
     */
    int markNear(float x, float y, float thr, float hx, float hy, boolean useHeading) {
        if (n == 0 || x < minX - thr || x > maxX + thr || y < minY - thr || y > maxY + thr) return 0;
        int ax = cx(x - thr), bx = cx(x + thr), ay = cy(y - thr), by = cy(y + thr);
        int marked = 0;
        for (int gy = ay; gy <= by; gy++) {
            for (int gx = ax; gx <= bx; gx++) {
                int c = gy * gw + gx;
                for (int k = cellStart[c]; k < cellStart[c + 1]; k++) {
                    int i = cellItems[k];
                    if (visited.get(i)) continue;
                    float x1 = seg[i * 4], y1 = seg[i * 4 + 1], x2 = seg[i * 4 + 2], y2 = seg[i * 4 + 3];
                    float d = Geo.segDist(x, y, x1, y1, x2, y2);
                    if (d > thr) continue;
                    float l = len[i];
                    if (useHeading && l >= 6f) {
                        float dot = Math.abs(hx * (x2 - x1) / l + hy * (y2 - y1) / l);
                        if (dot < 0.8f) continue;
                    } else if (d > 10f) {
                        continue;
                    }
                    markVisited(i);
                    marked++;
                }
            }
        }
        return marked;
    }

    // ---- storage -----------------------------------------------------------------------------

    void save(File dir) throws IOException {
        File tmp = new File(dir, slug + ".dat.tmp");
        DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), 1 << 16));
        try {
            o.writeInt(MAGIC);
            o.writeUTF(name);
            o.writeInt(n);
            for (int i = 0; i < n * 4; i++) o.writeFloat(seg[i]);
            o.write(cls, 0, n);
            for (int i = 0; i < n; i++) o.writeShort(mah[i]);
            o.writeInt(mahNames.length);
            for (int m = 0; m < mahNames.length; m++) {
                o.writeUTF(mahNames[m]);
                o.writeFloat(mahLabel[m * 2]);
                o.writeFloat(mahLabel[m * 2 + 1]);
                writeLines(o, mahLines[m]);
            }
            writeLines(o, outline);
        } finally {
            o.close();
        }
        File f = new File(dir, slug + ".dat");
        if (!tmp.renameTo(f)) throw new IOException("rename failed");
    }

    private static void writeLines(DataOutputStream o, List<float[]> lines) throws IOException {
        o.writeInt(lines.size());
        for (float[] l : lines) {
            o.writeInt(l.length);
            for (float v : l) o.writeFloat(v);
        }
    }

    private static List<float[]> readLines(DataInputStream in) throws IOException {
        int k = in.readInt();
        List<float[]> r = new ArrayList<>(k);
        for (int j = 0; j < k; j++) {
            float[] l = new float[in.readInt()];
            for (int i = 0; i < l.length; i++) l[i] = in.readFloat();
            r.add(l);
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    static District load(File f) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f), 1 << 16));
        try {
            if (in.readInt() != MAGIC) throw new IOException("bad file");
            District d = new District(in.readUTF());
            d.n = in.readInt();
            d.seg = new float[d.n * 4];
            for (int i = 0; i < d.n * 4; i++) d.seg[i] = in.readFloat();
            d.cls = new byte[d.n];
            in.readFully(d.cls);
            d.mah = new short[d.n];
            for (int i = 0; i < d.n; i++) d.mah[i] = in.readShort();
            int mc = in.readInt();
            d.mahNames = new String[mc];
            d.mahLabel = new float[mc * 2];
            d.mahLines = new List[mc];
            for (int m = 0; m < mc; m++) {
                d.mahNames[m] = in.readUTF();
                d.mahLabel[m * 2] = in.readFloat();
                d.mahLabel[m * 2 + 1] = in.readFloat();
                d.mahLines[m] = readLines(in);
            }
            d.outline = readLines(in);
            d.finish();
            return d;
        } finally {
            in.close();
        }
    }

    void saveVisited(File dir) throws IOException {
        byte[] b;
        synchronized (visited) { b = visited.toByteArray(); }
        File tmp = new File(dir, slug + ".vis.tmp");
        DataOutputStream o = new DataOutputStream(new FileOutputStream(tmp));
        try {
            o.writeInt(n);
            o.writeInt(b.length);
            o.write(b);
        } finally {
            o.close();
        }
        if (!tmp.renameTo(new File(dir, slug + ".vis"))) throw new IOException("rename failed");
    }

    void loadVisited(File dir) {
        File f = new File(dir, slug + ".vis");
        if (!f.exists()) return;
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)));
            try {
                if (in.readInt() != n) return; // network changed, progress comes back from the track
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                BitSet bs = BitSet.valueOf(b);
                for (int i = bs.nextSetBit(0); i >= 0 && i < n; i = bs.nextSetBit(i + 1)) markVisited(i);
                dirty = false;
            } finally {
                in.close();
            }
        } catch (IOException ignored) {
        }
    }
}
