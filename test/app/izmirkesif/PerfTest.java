package app.izmirkesif;
import java.io.*;
import java.util.*;
public class PerfTest {
    public static void main(String[] a) throws Exception {
        // 20 km x 20 km, 150 E-W + 150 N-S streets with a node every ~30 m, 60 mahalle squares, jagged ilçe border
        double la0 = 38.33, lo0 = 27.0, sz = 0.18;
        StringBuilder j = new StringBuilder("{\"elements\":[");
        StringBuilder ring = new StringBuilder("[");
        int K = 2000;
        for (int i = 0; i <= K; i++) { double t = 2*Math.PI*i/K; double rr = sz/2*(0.95+0.04*Math.sin(t*37));
            if (i>0) ring.append(','); ring.append("{\"lat\":"+(la0+sz/2+rr*Math.sin(t))+",\"lon\":"+(lo0+sz/2+rr*Math.cos(t))+"}"); }
        ring.append(']');
        j.append("{\"type\":\"relation\",\"members\":[{\"type\":\"way\",\"role\":\"outer\",\"geometry\":"+ring+"}],\"tags\":{\"admin_level\":\"6\",\"name\":\"Buca\"}}");
        int g = 8; double c = sz/g;
        for (int x=0;x<g;x++) for (int y=0;y<g;y++) {
            double a0=la0+y*c, b0=lo0+x*c;
            j.append(",{\"type\":\"relation\",\"members\":[{\"type\":\"way\",\"role\":\"outer\",\"geometry\":[");
            int P=40; for (int i=0;i<=P*4;i++){ int s=i/P, k=i%P; double f=k/(double)P; double la,lo;
                if (i==P*4){la=a0;lo=b0;} else if(s==0){la=a0;lo=b0+c*f;} else if(s==1){la=a0+c*f;lo=b0+c;} else if(s==2){la=a0+c;lo=b0+c-c*f;} else {la=a0+c-c*f;lo=b0;}
                if(i>0) j.append(','); j.append("{\"lat\":"+la+",\"lon\":"+lo+"}"); }
            j.append("]}],\"tags\":{\"admin_level\":\"8\",\"name\":\"M"+x+"_"+y+"\"}}");
        }
        int S = 150;
        for (int s=0;s<S;s++) for (int dir=0;dir<2;dir++) {
            j.append(",{\"type\":\"way\",\"geometry\":[");
            int N = 700;
            for (int i=0;i<=N;i++){ double f=i/(double)N; double la = dir==0? la0+sz*s/S : la0+sz*f; double lo = dir==0? lo0+sz*f : lo0+sz*s/S;
                if(i>0) j.append(','); j.append("{\"lat\":"+la+",\"lon\":"+lo+"}"); }
            j.append("],\"tags\":{\"highway\":\"residential\"}}");
        }
        j.append("]}");
        byte[] bytes = j.toString().getBytes("UTF-8");
        System.out.println("json MB=" + bytes.length/1048576.0);
        long t0=System.nanoTime();
        District d = Builder.build("Buca", new ByteArrayInputStream(bytes));
        long t1=System.nanoTime();
        System.out.printf("build %.0f ms pieces=%d km=%.0f mah=%d%n",(t1-t0)/1e6,d.n,d.total/1000,d.mahNames.length);
        File dir=new File("build/testdata"); dir.mkdirs();
        t0=System.nanoTime(); d.save(dir); t1=System.nanoTime();
        District e = District.load(new File(dir,"buca.dat")); long t2=System.nanoTime();
        System.out.printf("save %.0f ms load %.0f ms file=%.1f MB%n",(t1-t0)/1e6,(t2-t1)/1e6,new File(dir,"buca.dat").length()/1048576.0);
        Matcher m = new Matcher(); List<District> ds = Collections.singletonList(e);
        t0=System.nanoTime(); long t=0;
        for (int i=0;i<20000;i++){ t+=3000; m.feed(la0+0.05+ (i%2000)*0.00005, lo0+0.05+ (i/2000)*0.012, t, 10, ds);} 
        t1=System.nanoTime();
        System.out.printf("20000 fixes %.0f ms visited %.1f km%n",(t1-t0)/1e6, e.visitedLen/1000);
    }
}
