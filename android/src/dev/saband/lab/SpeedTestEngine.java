package dev.saband.lab;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded HTTP benchmark. The Android adapter supplies a specific cellular Network. */
public final class SpeedTestEngine {
    public static final String HOST="https://speed.cloudflare.com";
    public interface Connections {HttpURLConnection open(URL url) throws IOException;}
    public interface Guard {void check() throws IOException;}
    public interface Listener {void phase(String name);void progress(String name,long bytes,long nanos);}
    public static final class Config {
        final long downloadBytes,uploadBytes,phaseMillis;final int streams;
        public Config() {this(100000000,20000000,10000,4);}
        Config(long down,long up,long millis,int streams) {
            if(down<1||up<1||millis<1||streams<1||streams>4)throw new IllegalArgumentException();
            downloadBytes=down;uploadBytes=up;phaseMillis=millis;this.streams=streams;
        }
    }
    public static final class Rate {
        public final long bytes,nanos;public final boolean capped;
        Rate(long bytes,long nanos,boolean capped) {this.bytes=bytes;this.nanos=nanos;this.capped=capped;}
        public double mbps() {return nanos<=0?0:bytes*8000.0/nanos;}
    }
    public static final class Result {
        public double latencyMs=-1;public Rate download,upload;
        public long receivedBytes,sentBytes;public String error="";public boolean cancelled;
        public boolean complete() {return !cancelled&&error.isEmpty()&&download!=null&&upload!=null;}
    }
    private final Connections connections;private final Guard guard;private final Listener listener;private final Config config;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final Set<HttpURLConnection> active=Collections.newSetFromMap(new ConcurrentHashMap<HttpURLConnection,Boolean>());
    private final AtomicLong received=new AtomicLong(),sent=new AtomicLong();
    private final ExecutorService pool=Executors.newFixedThreadPool(4,r -> {Thread t=new Thread(r,"sa-speed-http");t.setDaemon(true);return t;});
    public SpeedTestEngine(Connections c,Guard g,Listener l) {this(c,g,l,new Config());}
    SpeedTestEngine(Connections c,Guard g,Listener l,Config config) {connections=c;guard=g;listener=l;this.config=config;}
    /** Nonblocking; the worker observes this within its polling interval and closes active sockets. */
    public void cancel() {cancelled.set(true);}
    private void check() throws IOException {
        if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Test stopped.");
        guard.check();
    }
    public Result run() {
        Result result=new Result();
        try {
            check();listener.phase("Latency");double[] samples=new double[3];
            for(int i=0;i<samples.length;i++) {
                Future<Double> f=pool.submit(() -> latency());
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
                try {while(true) {check();try {samples[i]=f.get(100,TimeUnit.MILLISECONDS);break;}catch(TimeoutException e) {if(System.nanoTime()>=deadline)throw new IOException("Latency request timed out.");}}}
                finally {if(!f.isDone()) {f.cancel(true);disconnect();}}
            }
            Arrays.sort(samples);result.latencyMs=samples[1];
            listener.phase("Download");result.download=transfer(false,config.downloadBytes);
            check();listener.phase("Upload");result.upload=transfer(true,config.uploadBytes);
            check();
        } catch(Exception e) {
            Throwable cause=e instanceof ExecutionException&&e.getCause()!=null?e.getCause():e;
            result.cancelled=cancelled.get();result.error=result.cancelled?"Stopped; incomplete results.":cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage();
        } finally {
            disconnect();pool.shutdownNow();result.receivedBytes=received.get();result.sentBytes=sent.get();
        }
        return result;
    }
    private HttpURLConnection open(String path) throws IOException {
        check();HttpURLConnection c=connections.open(new URL(HOST+path));active.add(c);
        c.setConnectTimeout(3000);c.setReadTimeout(3000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
        c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("Accept-Encoding","identity");
        c.setRequestProperty("User-Agent","SA-Band-Lab");return c;
    }
    private void release(HttpURLConnection c) {try {c.disconnect();}finally {active.remove(c);}}
    private void disconnect() {for(HttpURLConnection c:active)try {c.disconnect();}catch(Exception ignored) {}}
    private static void status(HttpURLConnection c) throws IOException {
        if(c.getResponseCode()!=200)throw new IOException("Speed endpoint returned HTTP "+c.getResponseCode()+".");
        String encoding=c.getContentEncoding();if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("Unexpected compressed test response.");
    }
    private double latency() throws IOException {
        HttpURLConnection c=open("/__down?bytes=0&nonce="+UUID.randomUUID());
        try {long start=System.nanoTime();status(c);return (System.nanoTime()-start)/1000000.0;}
        finally {release(c);}
    }
    private Rate transfer(boolean upload,long budget) throws Exception {
        long start=System.nanoTime(),deadline=start+TimeUnit.MILLISECONDS.toNanos(config.phaseMillis);
        AtomicBoolean stop=new AtomicBoolean();AtomicLong completed=new AtomicLong(),lastFinished=new AtomicLong(start);List<Future<?>> tasks=new ArrayList<>();
        for(int i=0;i<config.streams;i++) {
            final long quota=budget/config.streams+(i<budget%config.streams?1:0);
            tasks.add(pool.submit(() -> {
                try {if(upload)upload(quota,deadline,stop,completed);else download(quota,deadline,stop,completed);return null;}
                finally {lastFinished.accumulateAndGet(System.nanoTime(),Math::max);}
            }));
        }
        long finished=0,measuredBytes=0;
        try {
            while(true) {
                check();boolean done=true;
                for(Future<?> f:tasks) {if(f.isDone())f.get();else done=false;}
                long now=System.nanoTime();listener.progress(upload?"Upload":"Download",completed.get(),now-start);
                if(done||now>=deadline) {finished=done?Math.min(lastFinished.get(),deadline):deadline;measuredBytes=completed.get();break;}
                Thread.sleep(100);
            }
        } finally {
            stop.set(true);disconnect();
            for(Future<?> f:tasks)if(!f.isDone())f.cancel(true);
        }
        check();if(measuredBytes<=0)throw new IOException(upload?"No upload payload was acknowledged.":"No download data was received.");
        return new Rate(measuredBytes,Math.max(1,finished-start),measuredBytes>=budget);
    }
    private boolean running(long deadline,AtomicBoolean stop) throws IOException {check();return !stop.get()&&System.nanoTime()<deadline;}
    private void download(long quota,long deadline,AtomicBoolean stop,AtomicLong completed) throws IOException {
        if(quota==0)return;HttpURLConnection c=open("/__down?bytes="+quota+"&nonce="+UUID.randomUUID());
        try {
            status(c);long declared=c.getContentLengthLong();if(declared>=0&&declared!=quota)throw new IOException("Unexpected download size.");
            String type=c.getContentType();if(type!=null&&(type.contains("text/html")||type.contains("application/json")))throw new IOException("Speed endpoint returned a page instead of test data.");
            try(InputStream in=c.getInputStream()) {
                byte[] buffer=new byte[65536];long count=0;
                while(count<quota&&running(deadline,stop)) {
                    int n=in.read(buffer,0,(int)Math.min(buffer.length,quota-count));
                    if(n<0)throw new IOException("Truncated speed-test download.");
                    count+=n;received.addAndGet(n);if(running(deadline,stop))completed.addAndGet(n);
                }
            }
        } catch(IOException e) {if(!stop.get()&&System.nanoTime()<deadline)throw e;}
        finally {release(c);}
    }
    private void upload(long quota,long deadline,AtomicBoolean stop,AtomicLong completed) throws IOException {
        byte[] buffer=new byte[32768];new Random().nextBytes(buffer);long count=0;
        while(count<quota&&running(deadline,stop)) {
            int chunk=(int)Math.min(1000000,quota-count);HttpURLConnection c=open("/__up");
            try {
                c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/octet-stream");c.setFixedLengthStreamingMode(chunk);
                try(OutputStream out=c.getOutputStream()) {int n=0;while(n<chunk) {if(!running(deadline,stop))return;int take=Math.min(buffer.length,chunk-n);out.write(buffer,0,take);sent.addAndGet(take);n+=take;}}
                status(c);
                try(InputStream in=c.getInputStream()) {int total=0,n;while((n=in.read(buffer,0,Math.min(buffer.length,4097-total)))!=-1) {total+=n;if(total>4096)throw new IOException("Unexpected upload response size.");}}
                if(running(deadline,stop))completed.addAndGet(chunk);count+=chunk;
            } catch(IOException e) {if(!stop.get()&&System.nanoTime()<deadline)throw e;else return;}
            finally {release(c);}
        }
    }
}
