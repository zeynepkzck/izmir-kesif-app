package app.izmirkesif;

import java.io.*;
import java.util.*;

public class LogicTest {
    // lat/lon helpers around İzmir centre: 0.001 deg lat ~ 110 m
    static String pt(double lat, double lon) { return "{\"lat\":" + lat + ",\"lon\":" + lon + "}"; }
    static String line(double[][] p) { StringBuilder b = new StringBuilder("["); for (int i = 0; i < p.length; i++) { if (i > 0) b.append(','); b.append(pt(p[i][0], p[i][1])); } return b.append(']').toString(); }

    public static void main(String[] a) throws Exception {
        double la0 = 38.40, lo0 = 27.10, la1 = 38.42, lo1 = 27.13; // ~2.2 km x 2.6 km
        double loMid = 27.115;
        StringBuilder j = new StringBuilder("{\"version\":0.6,\"elements\":[");
        // ilçe boundary as two open member ways forming a ring (tests ring-less PIP)
        j.append("{\"type\":\"relation\",\"id\":1,\"members\":[")
         .append("{\"type\":\"way\",\"ref\":1,\"role\":\"outer\",\"geometry\":").append(line(new double[][]{{la0,lo0},{la0,lo1},{la1,lo1}})).append("},")
         .append("{\"type\":\"way\",\"ref\":2,\"role\":\"outer\",\"geometry\":").append(line(new double[][]{{la1,lo1},{la1,lo0},{la0,lo0}})).append("},")
         .append("{\"type\":\"node\",\"ref\":5,\"role\":\"admin_centre\",\"lat\":38.41,\"lon\":27.11}")
         .append("],\"tags\":{\"admin_level\":\"6\",\"boundary\":\"administrative\",\"name\":\"Konak\"}},");
        // two mahalles west/east
        j.append("{\"type\":\"relation\",\"id\":2,\"members\":[{\"type\":\"way\",\"ref\":3,\"role\":\"outer\",\"geometry\":")
         .append(line(new double[][]{{la0,lo0},{la0,loMid},{la1,loMid},{la1,lo0},{la0,lo0}})).append("}],\"tags\":{\"admin_level\":\"8\",\"name\":\"Batı Mahallesi\"}},");
        j.append("{\"type\":\"relation\",\"id\":3,\"members\":[{\"type\":\"way\",\"ref\":4,\"role\":\"outer\",\"geometry\":")
         .append(line(new double[][]{{la0,loMid},{la0,lo1},{la1,lo1},{la1,loMid},{la0,loMid}})).append("}],\"tags\":{\"admin_level\":\"8\",\"name\":\"Doğu Mahallesi\"}},");
        // a neighbour ilçe's mahalle outside (should be dropped)
        j.append("{\"type\":\"relation\",\"id\":4,\"members\":[{\"type\":\"way\",\"ref\":9,\"role\":\"outer\",\"geometry\":")
         .append(line(new double[][]{{la1,lo0},{la1,lo1},{la1+0.01,lo1},{la1+0.01,lo0},{la1,lo0}})).append("}],\"tags\":{\"admin_level\":\"8\",\"name\":\"Komşu\"}},");
        // street grid: E-W streets every 0.002 lat, N-S every 0.003 lon
        int id = 100;
        List<String> ways = new ArrayList<>();
        for (double la = la0 + 0.002; la < la1; la += 0.002)
            ways.add("{\"type\":\"way\",\"id\":" + (id++) + ",\"geometry\":" + line(new double[][]{{la, lo0 + 0.0005}, {la, lo1 - 0.0005}}) + ",\"tags\":{\"highway\":\"residential\"}}");
        for (double lo = lo0 + 0.003; lo < lo1; lo += 0.003)
            ways.add("{\"type\":\"way\",\"id\":" + (id++) + ",\"geometry\":" + line(new double[][]{{la0 + 0.0005, lo}, {la1 - 0.0005, lo}}) + ",\"tags\":{\"highway\":\"secondary\"}}");
        // way partly outside the ilçe (north), with null gap
        ways.add("{\"type\":\"way\",\"id\":999,\"geometry\":[" + pt(la1 - 0.001, 27.105) + "," + pt(la1 + 0.005, 27.105) + ",null," + pt(la1+0.006,27.106) + "," + pt(la1+0.007,27.106) + "],\"tags\":{\"highway\":\"primary\"}}");
        ways.add("{\"type\":\"way\",\"id\":1000,\"geometry\":" + line(new double[][]{{38.41,27.11},{38.411,27.111}}) + ",\"tags\":{\"highway\":\"service\"}}");
        j.append(String.join(",", ways)).append("]}");

        District d = Builder.build("Konak", new ByteArrayInputStream(j.toString().getBytes("UTF-8")));
        System.out.printf("pieces=%d total=%.0f m mahalles=%s%n", d.n, d.total, Arrays.toString(d.mahNames));
        for (int m = 0; m < d.mahNames.length; m++) System.out.printf("  %s total=%.0f m%n", d.mahNames[m], d.mahTotal[m]);
        int noMah = 0; for (short s : d.mah) if (s < 0) noMah++;
        System.out.println("pieces without mahalle=" + noMah);
        float maxLen = 0; for (float l : d.len) maxLen = Math.max(maxLen, l);
        System.out.println("max piece len=" + maxLen);

        // Walk along E-W street at la0+0.006 from lo 27.102 to 27.112, fix every ~15 m with noise
        Random r = new Random(1);
        Matcher m = new Matcher();
        List<District> ds = Collections.singletonList(d);
        double la = la0 + 0.006; long t = 0;
        for (double lo = 27.102; lo <= 27.112; lo += 0.00017) {
            t += 10000;
            m.feed(la + r.nextGaussian() * 0.00004, lo + r.nextGaussian() * 0.00005, t, 8f, ds);
        }
        double walked = (27.112 - 27.102) * Geo.KX;
        System.out.printf("walked %.0f m, visited %.0f m (%.2f%%)%n", walked, d.visitedLen, d.percent());
        // check which classes got marked: crossing N-S (secondary) pieces should be few
        double secVisited = 0, resVisited = 0;
        for (int i = d.visited.nextSetBit(0); i >= 0; i = d.visited.nextSetBit(i + 1)) { if (d.cls[i] == 1) secVisited += d.len[i]; else resVisited += d.len[i]; }
        System.out.printf("residential visited %.0f m, crossing secondary visited %.0f m%n", resVisited, secVisited);
        for (int q = 0; q < d.mahNames.length; q++) System.out.printf("  %s %.2f%%%n", d.mahNames[q], d.mahPercent(q));

        // save / load roundtrip
        File dir = new File("build/testdata"); dir.mkdirs();
        d.save(dir); d.saveVisited(dir);
        District e = District.load(new File(dir, "konak.dat")); e.loadVisited(dir);
        System.out.printf("reloaded pieces=%d visited=%.0f m pct=%.2f contains(center)=%b mahAt=%d%n", e.n, e.visitedLen, e.percent(),
            e.contains(Geo.x(27.11), Geo.y(38.41)), e.mahAt(Geo.x(27.12), Geo.y(38.41)));
        System.out.println("outline lines floats=" + e.outlineLines.length + " chunks=" + e.cw + "x" + e.ch);
    }
}
