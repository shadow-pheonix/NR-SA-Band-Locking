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
        volatile boolean failDownload,failUpload,truncated,compressed,html,timeoutUpload,timeoutLatency,holdLatency,holdLatencyClose;
        volatile int failDownloadAfter=-1,failUploadAfter=-1;
        final AtomicBoolean releaseLatency=new AtomicBoolean();final CountDownLatch latencyEntered=new CountDownLatch(1);
        volatile long delayMillis,uploadResponseMillis;final AtomicLong sent=new AtomicLong(),received=new AtomicLong(),largestUpload=new AtomicLong();
        final AtomicInteger downloadRequests=new AtomicInteger(),uploadRequests=new AtomicInteger();final CountDownLatch uploading=new CountDownLatch(1);
        final CountDownLatch downloading=new CountDownLatch(1);
        public HttpURLConnection open(URL url) {
            return new HttpURLConnection(url) {
                volatile boolean disconnected;
                final boolean upload=url.getPath().equals("/__up");
                final long size=upload?0:Long.parseLong(url.getQuery().split("&")[0].substring(6));
                @Override public int getResponseCode() throws IOException {
                    if(!upload&&size==0) {
                        latencyEntered.countDown();
                        while(holdLatency&&!releaseLatency.get()) {try {Thread.sleep(10);}catch(InterruptedException ignored) {}}
                        if(timeoutLatency)throw new SocketTimeoutException("mock latency timeout");
                    }
                    if(upload) {
                        if(failUploadAfter>=0&&uploadRequests.incrementAndGet()>failUploadAfter)return 503;
                        uploading.countDown();
                        if(timeoutUpload||uploadResponseMillis>getReadTimeout())throw new SocketTimeoutException("mock response timeout");
                        long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(uploadResponseMillis);
                        while(System.nanoTime()<until) {
                            if(disconnected)throw new IOException("Disconnected");
                            try {Thread.sleep(10);}catch(InterruptedException e) {throw new InterruptedIOException();}
                        }
                    } else if(size>0&&downloadRequests.incrementAndGet()>failDownloadAfter&&failDownloadAfter>=0)return 429;
                    return upload?(failUpload?503:200):size>0&&failDownload?503:200;
                }
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
                    check(getRequestMethod().equals("POST"));check(fixedContentLength>0);largestUpload.accumulateAndGet(fixedContentLength,Math::max);return new OutputStream() {
                        public void write(int b) {sent.incrementAndGet();}
                        public void write(byte[] b,int off,int len) throws IOException {if(disconnected)throw new IOException("Disconnected");sent.addAndGet(len);}
                    };
                }
                public void connect() {}public boolean usingProxy() {return false;}public void disconnect() {
                    disconnected=true;
                    while(!upload&&size==0&&holdLatencyClose&&!releaseLatency.get()) {try {Thread.sleep(10);}catch(InterruptedException ignored) {}}
                }
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
            SpeedTestEngine.Result bad=engine(e).run();check(!bad.complete()&&!bad.error.isEmpty()&&bad.upload!=null&&e.sent.get()>0);
            if(mode==1)check(bad.download!=null&&bad.download.partial&&bad.download.bytes>0&&!bad.throughputComplete());
            else check(bad.download==null); // Invalid headers/HTML/encoding contribute no measurement bytes.
        }
        e=new Endpoint();e.failDownloadAfter=1;
        SpeedTestEngine.Result limited=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(Long.MAX_VALUE,100003,2000,1)).run();
        check(!limited.complete()&&!limited.throughputComplete()&&!limited.cancelled&&!limited.aborted);
        check(limited.download!=null&&limited.download.partial&&limited.download.bytes==SpeedTestEngine.DOWNLOAD_REQUEST_BYTES);
        check(limited.download.completionReason.equals("http_429")&&limited.download.nanos>0&&limited.download.nanos<2000000000L);
        check(limited.upload!=null&&!limited.upload.partial&&limited.upload.bytes==100003);
        check(limited.receivedBytes==limited.download.bytes&&e.downloadRequests.get()==2);
        check(limited.error.contains("HTTP 429")&&limited.failures.get(0).requests.stream().anyMatch(q -> q.httpStatus==429));
        check(Math.abs(limited.download.mbps()-limited.download.bytes*8000.0/limited.download.nanos)<0.00001);
        e=new Endpoint();e.failDownloadAfter=0;
        SpeedTestEngine.Result noBytes=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(100003,20003,1000,1)).run();
        check(noBytes.download==null&&noBytes.receivedBytes==0&&noBytes.upload!=null&&e.downloadRequests.get()==1);
        e=new Endpoint();e.failUploadAfter=1;
        SpeedTestEngine.Result uploadPartial=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(100003,600000,2000,1)).run();
        check(uploadPartial.upload!=null&&uploadPartial.upload.partial&&!uploadPartial.throughputComplete());
        check(uploadPartial.upload.bytes==SpeedTestEngine.UPLOAD_CHUNK_BYTES&&uploadPartial.uploadAcknowledgedBytes==SpeedTestEngine.UPLOAD_CHUNK_BYTES);
        check(uploadPartial.sentBytes==600000&&uploadPartial.upload.bytes<uploadPartial.sentBytes&&uploadPartial.upload.completionReason.equals("http_503"));
        e=new Endpoint();e.failDownloadAfter=4;
        SpeedTestEngine.Result parallelPartial=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(Long.MAX_VALUE,20003,2000,4)).run();
        check(parallelPartial.download!=null&&parallelPartial.download.partial&&parallelPartial.download.bytes>=SpeedTestEngine.DOWNLOAD_REQUEST_BYTES);
        check(parallelPartial.download.bytes<=parallelPartial.receivedBytes&&parallelPartial.download.nanos<2000000000L);
        e=new Endpoint();e.failUpload=true;SpeedTestEngine.Result partial=engine(e).run();
        check(!partial.complete()&&partial.download!=null&&partial.upload==null&&partial.sentBytes<=20003);
        check(partial.failedPhase.equals("Upload")&&partial.error.contains("HTTP 503"));
        e=new Endpoint();e.timeoutUpload=true;SpeedTestEngine.Result timeout=engine(e).run();
        check(timeout.download!=null&&timeout.latencyMs>=0&&timeout.upload==null&&!timeout.cancelled);
        check(timeout.failedPhase.equals("Upload")&&timeout.errorType.equals("SocketTimeoutException")&&timeout.error.contains("Upload request timed out"));
        check(timeout.uploadAcknowledgedBytes==0); // Sent bytes are not an upload-speed result.
        e=new Endpoint();e.timeoutLatency=true;SpeedTestEngine.Result noPing=engine(e).run();
        check(!noPing.complete()&&noPing.throughputComplete()&&noPing.latencyMs<0&&noPing.latencyAttempts==3&&noPing.latencyValidSamples==0);
        check(noPing.download!=null&&noPing.upload!=null&&noPing.failedPhase.equals("Latency"));
        check(noPing.failures.size()==1&&noPing.failures.get(0).requests.size()==3);
        check(noPing.failures.get(0).requests.get(0).operation.equals("response_headers_including_connect_tls"));
        e=new Endpoint();e.holdLatency=true;
        long began=System.nanoTime();SpeedTestEngine.Result stuck;
        try {stuck=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(100003,20003,1000,4,200,100)).run();}
        finally {e.releaseLatency.set(true);}
        check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)<2000);
        check(stuck.throughputComplete()&&stuck.latencyMs<0&&stuck.latencyAttempts==1&&stuck.errorType.equals("TimeoutException"));
        e=new Endpoint();e.holdLatencyClose=true;
        try {SpeedTestEngine.Result cleanup=engine(e).run();check(cleanup.complete()&&cleanup.latencyValidSamples==3);}
        finally {e.releaseLatency.set(true);}
        e=new Endpoint();e.timeoutLatency=true;e.failDownload=true;
        SpeedTestEngine.Result twoErrors=engine(e).run();check(twoErrors.failures.size()==2&&twoErrors.upload!=null&&twoErrors.download==null&&!twoErrors.throughputComplete());
        final Endpoint tlsEndpoint=new Endpoint();
        SpeedTestEngine.Connections tlsFailure=url -> {
            if(url.getQuery()!=null&&url.getQuery().startsWith("bytes=0&"))throw new javax.net.ssl.SSLHandshakeException("mock TLS failure");
            return tlsEndpoint.open(url);
        };
        SpeedTestEngine.Result tlsResult=new SpeedTestEngine(tlsFailure,() -> {},QUIET,new SpeedTestEngine.Config(1000,1000,1000,2)).run();
        check(tlsResult.throughputComplete()&&tlsResult.errorType.equals("SSLHandshakeException"));
        check(tlsResult.failures.get(0).requests.get(0).operation.equals("open_connection"));
        SpeedTestEngine.Result routeResult=new SpeedTestEngine(url -> {throw new SpeedTestEngine.AbortException("Data SIM changed");},() -> {},QUIET,new SpeedTestEngine.Config(1000,1000,1000,2)).run();
        check(routeResult.aborted&&routeResult.latencyAttempts==1&&routeResult.download==null&&routeResult.upload==null);
        e=new Endpoint();e.uploadResponseMillis=3200;
        SpeedTestEngine.Result slowResponse=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(1000,40000,5000,4)).run();
        check(slowResponse.complete()&&slowResponse.upload.bytes==40000&&slowResponse.uploadAcknowledgedBytes==40000);
        SpeedTestEngine.Config standard=new SpeedTestEngine.Config(),extended=new SpeedTestEngine.Config(60);
        check(standard.phaseMillis==20000&&extended.phaseMillis==60000);
        check(standard.downloadBytes==Long.MAX_VALUE&&extended.uploadBytes==Long.MAX_VALUE);
        boolean rejected=false;try {new SpeedTestEngine.Config(0);}catch(IllegalArgumentException ex) {rejected=true;}check(rejected);
        e=new Endpoint();SpeedTestEngine.Config timed=new SpeedTestEngine.Config(Long.MAX_VALUE,Long.MAX_VALUE,600,4);
        SpeedTestEngine.Result sustained=new SpeedTestEngine(e,() -> {},QUIET,timed).run();
        check(sustained.complete()&&sustained.download.bytes>100000000&&sustained.upload.bytes>20000000);
        check(sustained.download.nanos>=600000000L&&sustained.upload.nanos>=600000000L);
        check(!sustained.download.capped&&!sustained.upload.capped&&e.downloadRequests.get()>4);
        check(e.largestUpload.get()>SpeedTestEngine.UPLOAD_CHUNK_BYTES&&e.largestUpload.get()<=SpeedTestEngine.MAX_UPLOAD_CHUNK_BYTES);
        e=new Endpoint();e.delayMillis=20;
        SpeedTestEngine.Result bounded=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(1000000,1000,65,1)).run();
        check(bounded.complete()&&bounded.download.bytes>0&&bounded.download.bytes<1000000&&!bounded.download.capped);
        e=new Endpoint();e.delayMillis=30;final Endpoint cancelling=e;final SpeedTestEngine cancelEngine=engine(e);
        ExecutorService executor=Executors.newSingleThreadExecutor();Future<SpeedTestEngine.Result> future=executor.submit(() -> cancelEngine.run());
        check(cancelling.downloading.await(2,TimeUnit.SECONDS));cancelEngine.cancel();SpeedTestEngine.Result stopped=future.get(2,TimeUnit.SECONDS);executor.shutdownNow();
        check(stopped.cancelled&&!stopped.complete()&&stopped.upload==null);
        e=new Endpoint();e.uploadResponseMillis=10000;final Endpoint stalled=e;
        final SpeedTestEngine stopUpload=new SpeedTestEngine(e,() -> {},QUIET,new SpeedTestEngine.Config(1000,40000,15000,4));
        executor=Executors.newSingleThreadExecutor();future=executor.submit(() -> stopUpload.run());check(stalled.uploading.await(2,TimeUnit.SECONDS));
        stopUpload.cancel();SpeedTestEngine.Result cancelledUpload=future.get(2,TimeUnit.SECONDS);executor.shutdownNow();
        check(cancelledUpload.cancelled&&cancelledUpload.download!=null&&cancelledUpload.upload==null);
        e=new Endpoint();e.holdLatency=true;final Endpoint stoppedLatency=e;final SpeedTestEngine cancelPing=engine(e);
        executor=Executors.newSingleThreadExecutor();future=executor.submit(() -> cancelPing.run());check(stoppedLatency.latencyEntered.await(2,TimeUnit.SECONDS));
        try {cancelPing.cancel();SpeedTestEngine.Result stopPing=future.get(2,TimeUnit.SECONDS);check(stopPing.cancelled&&stopPing.download==null&&stopPing.upload==null);}
        finally {stoppedLatency.releaseLatency.set(true);executor.shutdownNow();}
        e=new Endpoint();SpeedTestEngine preCancelled=engine(e);preCancelled.cancel();check(preCancelled.run().cancelled&&e.received.get()==0);
        e=new Endpoint();AtomicInteger guards=new AtomicInteger();
        SpeedTestEngine lost=new SpeedTestEngine(e,() -> {if(guards.incrementAndGet()>5)throw new IOException("SIM changed");},QUIET,new SpeedTestEngine.Config(100000,10000,1000,1));
        SpeedTestEngine.Result failed=lost.run();check(!failed.complete()&&failed.aborted&&failed.error.contains("SIM changed")&&failed.upload==null);
        check(SpeedTestBands.serving(new int[]{78},false,0).length==0);
        check(SpeedTestBands.serving(new int[]{78},true,-1).length==0);
        check(SpeedTestBands.serving(new int[]{78},true,5001).length==0);
        check(Arrays.equals(SpeedTestBands.serving(new int[]{78,28,78,0,9999},true,5000),new int[]{28,78}));
        check(SpeedTestBands.summary(Arrays.asList(new int[]{78},new int[]{28},new int[0])).equals("n28, n78 · bands changed between samples · some samples unknown"));
        check(SpeedTestBands.summary(Collections.emptyList()).equals("unknown"));
        check(SpeedTestBands.summary(Arrays.asList(new int[]{78},new int[]{78})).equals("n78"));
        check(SpeedometerScale.fraction(0)==0&&SpeedometerScale.fraction(-5)==0&&SpeedometerScale.fraction(Double.NaN)==0);
        check(SpeedometerScale.fraction(5000)==1&&SpeedometerScale.fraction(20000)==1);
        check(SpeedometerScale.fraction(266)>SpeedometerScale.fraction(100)&&SpeedometerScale.fraction(100)>SpeedometerScale.fraction(10));
        System.out.println("Speed tests passed: retained partial download/upload rates after HTTP errors, no-byte failures, bounded parallel measurement windows, byte budgets, deadlines, Stop/route guards and serving samples.");
    }
}
