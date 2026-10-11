package app.izmirkesif;

import java.util.Arrays;

final class FloatList {
    float[] a;
    int n;

    FloatList() { this(64); }
    FloatList(int cap) { a = new float[Math.max(4, cap)]; }

    void add(float v) {
        if (n == a.length) a = Arrays.copyOf(a, a.length * 2);
        a[n++] = v;
    }

    void add4(float v1, float v2, float v3, float v4) {
        if (n + 4 > a.length) a = Arrays.copyOf(a, Math.max(a.length * 2, n + 4));
        a[n++] = v1; a[n++] = v2; a[n++] = v3; a[n++] = v4;
    }

    float[] toArray() { return Arrays.copyOf(a, n); }
}
