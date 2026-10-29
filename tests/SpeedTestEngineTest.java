package dev.saband.lab;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Exercises transfer loops, HTTP status/length handling, budgets, partial failures and cancellation. */
public final class SpeedTestEngineTest {
    private static void check(boolean condition) {if(!condition)throw new AssertionError();}
    private static final SpeedTestEngine.Listener QUIET=new SpeedTestEngine.Listener() {
        public void phase(String p) {}public void progress(String p,long b,long n) {}
    };
    private static final class Endpoint implements SpeedTestEngine.Connections {
        volatile boolean failDownload,failUpload,truncated,compressed,html;
        volatile long delayMillis;final AtomicLong sent=new AtomicLong(),received=new AtomicLong();
        final CountDownLatch downloading=new CountDownLatch(1);
        public HttpURLConnection open(URL url) {
            return new HttpURLConnection(url) {
                volatile boolean disconnected;
                final boolean upload=url.getPath().equals("/__up");
                final long size=upload?0:Long.parseLong(url.getQuery().split("&")[0].substring(6));
                @Override public int getResponseCode() {return upload?(failUpload?503:200):size>0&&failDownload?503:200;}
                @Override public String getContentEncoding() {return compressed&&size>0?"gzip":null;}
                @Override public String getContentType() {return html&&size>0?"text/html":"application/octet-stream";}
                @Override public long getContentLengthLong() {return size;}
                @Override public InputStream getInputStream() {
                    if(upload)return new ByteArrayInputStream(new byte[0]);
                    return new InputStream() {
                        long remaining=truncated?size/2:size;
                        public int read() throws IOException {byte[] b=new byte[1];return read(b,0,1)<0?-1:b[0];}
                        public int read(byte[] b,int off,int len) throws IOException {
                            downloading.countDown();if(delayMillis>0)try {Thread.sleep(delayMillis);}catch(InterruptedException e) {throw new InterruptedIOException();}
                            if(disconnected)throw new IOException("Disconnected");if(remaining==0)return -1;
                            int n=(int)Math.min(len,Math.min(8192,remaining));remaining-=n;received.addAndGet(n);return n;
                        }
                    };
                }
                @Override public OutputStream getOutputStream() {
                    check(getRequestMethod().equals("POST"));check(fixedContentLength>0);return new OutputStream() {
                        public void write(int b) {sent.incrementAndGet();}
                        public void write(byte[] b,int off,int len) throws IOException {if(disconnected)throw new IOException("Disconnected");sent.addAndGet(len);}
                    };
                }
                public void connect() {}public boolean usingProxy() {return false;}public void disconnect() {disconnected=true;}
            };
        }
    }
    private static SpeedTestEngine engine(Endpoint e) {return new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(100003,20003,1000,4));}
    public static void main(String[] args) throws Exception {
        Endpoint e=new Endpoint();SpeedTestEngine.Result good=engine(e).run();
        check(good.complete()&&good.latencyMs>=0);check(good.download.bytes==100003&&good.upload.bytes==20003);
        check(e.received.get()==100003&&e.sent.get()==20003);check(good.receivedBytes==100003&&good.sentBytes==20003);
        check(good.download.capped&&good.upload.capped&&good.download.mbps()>0);
        check(Math.abs(new SpeedTestEngine.Rate(1000000,1000000000,false).mbps()-8)<0.0001);
        for(int mode=0;mode<4;mode++) {
            e=new Endpoint();e.failDownload=mode==0;e.truncated=mode==1;e.compressed=mode==2;e.html=mode==3;
            SpeedTestEngine.Result bad=engine(e).run();check(!bad.complete()&&!bad.error.isEmpty()&&bad.download==null&&bad.upload==null&&e.sent.get()==0);
        }
        e=new Endpoint();e.failUpload=true;SpeedTestEngine.Result partial=engine(e).run();
        check(!partial.complete()&&partial.download!=null&&partial.upload==null&&partial.sentBytes<=20003);
        e=new Endpoint();e.delayMillis=20;
        SpeedTestEngine.Result bounded=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(1000000,1000,65,1)).run();
        check(bounded.complete()&&bounded.download.bytes>0&&bounded.download.bytes<1000000&&!bounded.download.capped);
        e=new Endpoint();e.delayMillis=30;final Endpoint cancelling=e;final SpeedTestEngine cancelEngine=engine(e);
        ExecutorService executor=Executors.newSingleThreadExecutor();Future<SpeedTestEngine.Result> future=executor.submit(() -> cancelEngine.run());
        check(cancelling.downloading.await(2,TimeUnit.SECONDS));cancelEngine.cancel();SpeedTestEngine.Result stopped=future.get(2,TimeUnit.SECONDS);executor.shutdownNow();
        check(stopped.cancelled&&!stopped.complete()&&stopped.upload==null);
        e=new Endpoint();SpeedTestEngine preCancelled=engine(e);preCancelled.cancel();check(preCancelled.run().cancelled&&e.received.get()==0);
        e=new Endpoint();AtomicInteger guards=new AtomicInteger();
        SpeedTestEngine lost=new SpeedTestEngine(e,() -> {if(guards.incrementAndGet()>5)throw new IOException("SIM changed");},QUIET,new SpeedTestEngine.Config(100000,10000,1000,1));
        SpeedTestEngine.Result failed=lost.run();check(!failed.complete()&&failed.error.contains("SIM changed"));
        check(SpeedTestBands.serving(new int[]{78},false,0).length==0);
        check(SpeedTestBands.serving(new int[]{78},true,-1).length==0);
        check(SpeedTestBands.serving(new int[]{78},true,5001).length==0);
        check(Arrays.equals(SpeedTestBands.serving(new int[]{78,28,78,0,9999},true,5000),new int[]{28,78}));
        check(SpeedTestBands.summary(Arrays.asList(new int[]{78},new int[]{28},new int[0])).equals("n28, n78 · bands changed between samples · some samples unknown"));
        check(SpeedTestBands.summary(Collections.emptyList()).equals("unknown"));
        check(SpeedTestBands.summary(Arrays.asList(new int[]{78},new int[]{78})).equals("n78"));
        System.out.println("Speed tests passed: byte budgets, parallel transfers, Mbps, status/encoding/truncation, acknowledged uploads, deadlines, stop/route errors, fresh serving-only band samples and unknowns.");
    }
}
