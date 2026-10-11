package app.izmirkesif;

/** Local equirectangular projection centred on İzmir. Units are metres, y grows southwards. */
final class Geo {
    static final double LAT0 = 38.42, LON0 = 27.14;
    static final double KY = 110574.0;
    static final double KX = 111320.0 * Math.cos(Math.toRadians(LAT0));

    static float x(double lon) { return (float) ((lon - LON0) * KX); }
    static float y(double lat) { return (float) (-(lat - LAT0) * KY); }

    static float segDist(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float l2 = dx * dx + dy * dy;
        float t = l2 == 0 ? 0 : ((px - x1) * dx + (py - y1) * dy) / l2;
        if (t < 0) t = 0; else if (t > 1) t = 1;
        float ex = x1 + t * dx - px, ey = y1 + t * dy - py;
        return (float) Math.sqrt(ex * ex + ey * ey);
    }

    static String slug(String name) {
        StringBuilder b = new StringBuilder();
        for (char c : name.toCharArray()) {
            switch (c) {
                case 'ç': case 'Ç': b.append('c'); break;
                case 'ğ': case 'Ğ': b.append('g'); break;
                case 'ı': case 'İ': case 'I': b.append('i'); break;
                case 'ö': case 'Ö': b.append('o'); break;
                case 'ş': case 'Ş': b.append('s'); break;
                case 'ü': case 'Ü': b.append('u'); break;
                default: b.append(Character.toLowerCase(c));
            }
        }
        return b.toString();
    }

    private Geo() {}
}
