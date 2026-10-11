package app.izmirkesif;

import java.util.List;

/**
 * Even-odd point-in-polygon over an unordered set of boundary polylines. Ring assembly is
 * unnecessary: as long as the polylines together form closed rings, crossing counts are correct.
 * Edges are bucketed into horizontal strips so each query only looks at nearby edges.
 */
final class Poly {
    final float minX, minY, maxX, maxY;
    private final float[] e;
    private final float stripH;
    private final int[][] strips;

    Poly(List<float[]> lines) {
        int edges = 0;
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (float[] l : lines) {
            edges += Math.max(0, l.length / 2 - 1);
            for (int i = 0; i < l.length; i += 2) {
                x0 = Math.min(x0, l[i]); x1 = Math.max(x1, l[i]);
                y0 = Math.min(y0, l[i + 1]); y1 = Math.max(y1, l[i + 1]);
            }
        }
        minX = x0; minY = y0; maxX = x1; maxY = y1;
        e = new float[edges * 4];
        int k = 0;
        for (float[] l : lines) {
            for (int i = 0; i + 3 < l.length; i += 2) {
                e[k++] = l[i]; e[k++] = l[i + 1]; e[k++] = l[i + 2]; e[k++] = l[i + 3];
            }
        }
        float h = maxY - minY;
        stripH = Math.max(50f, h / 256f);
        int ns = edges == 0 ? 1 : (int) (h / stripH) + 1;
        int[] cnt = new int[ns];
        for (int i = 0; i < edges; i++) {
            int a = strip(Math.min(e[i * 4 + 1], e[i * 4 + 3])), b = strip(Math.max(e[i * 4 + 1], e[i * 4 + 3]));
            for (int s = a; s <= b; s++) cnt[s]++;
        }
        strips = new int[ns][];
        for (int s = 0; s < ns; s++) strips[s] = new int[cnt[s]];
        int[] fill = new int[ns];
        for (int i = 0; i < edges; i++) {
            int a = strip(Math.min(e[i * 4 + 1], e[i * 4 + 3])), b = strip(Math.max(e[i * 4 + 1], e[i * 4 + 3]));
            for (int s = a; s <= b; s++) strips[s][fill[s]++] = i;
        }
    }

    private int strip(float y) {
        int s = (int) ((y - minY) / stripH);
        return s < 0 ? 0 : Math.min(s, strips == null ? Integer.MAX_VALUE : strips.length - 1);
    }

    boolean isEmpty() { return e.length == 0; }

    boolean contains(float x, float y) {
        if (e.length == 0 || x < minX || x > maxX || y < minY || y > maxY) return false;
        boolean in = false;
        for (int i : strips[strip(y)]) {
            float ax = e[i * 4], ay = e[i * 4 + 1], bx = e[i * 4 + 2], by = e[i * 4 + 3];
            if ((ay > y) != (by > y)) {
                float cx = ax + (y - ay) * (bx - ax) / (by - ay);
                if (x < cx) in = !in;
            }
        }
        return in;
    }
}
