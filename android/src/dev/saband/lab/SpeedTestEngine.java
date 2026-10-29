package dev.saband.lab;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Bounded HTTP benchmark. The Android adapter supplies a specific cellular Network. */
public final class SpeedTestEngine {
    public static final String HOST="https://speed.cloudflare.com";
    public static final int UPLOAD_CHUNK_BYTES=250000;
    public static final int MAX_UPLOAD_CHUNK_BYTES=4000000,DOWNLOAD_REQUEST_BYTES=25000000;
    public static final int STANDARD_SECONDS=20,EXTENDED_SECONDS=60,CONNECT_TIMEOUT_MS=10000,RESPONSE_TIMEOUT_MS=15000;
    public static final int LATENCY_BUDGET_MS=8000,LATENCY_SOCKET_TIMEOUT_MS=5000;
    public interface Connections {HttpURLConnection open(URL url) throws IOException;}
    public interface Guard {void check() throws IOException;}
    public interface Listener {void phase(String name);void progress(String name,long bytes,long nanos);default void latency(double ms) {}}
    public static final class Config {
        final long downloadBytes,uploadBytes,phaseMillis;final int streams,latencyBudgetMillis,latencySocketMillis;
        public Config() {this(STANDARD_SECONDS);}
        public Config(int seconds) {
            this(Long.MAX_VALUE,Long.MAX_VALUE,seconds*1000L,4);
            if(seconds!=STANDARD_SECONDS&&seconds!=EXTENDED_SECONDS)throw new IllegalArgumentException("Choose 20 or 60 seconds per direction.");
        }
        Config(long down,long up,long millis,int streams) {
            this(down,up,millis,streams,LATENCY_BUDGET_MS,LATENCY_SOCKET_TIMEOUT_MS);
        }
        Config(long down,long up,long millis,int streams,int latencyBudget,int latencySocket) {
            if(down<1||up<1||millis<1||millis>60000||streams<1||streams>4)throw new IllegalArgumentException();
            if(latencyBudget<1||latencyBudget>8000||latencySocket<1||latencySocket>5000)throw new IllegalArgumentException();
            downloadBytes=down;uploadBytes=up;phaseMillis=millis;this.streams=streams;
            latencyBudgetMillis=latencyBudget;latencySocketMillis=latencySocket;
        }
    }
    public static final class Rate {
        public final long bytes,nanos;public final boolean capped;
        Rate(long bytes,long nanos,boolean capped) {this.bytes=bytes;this.nanos=nanos;this.capped=capped;}
        public double mbps() {return nanos<=0?0:bytes*8000.0/nanos;}
    }
    public static final class Result {
        public double latencyMs=-1;public Rate download,upload;
        public long receivedBytes,sentBytes,uploadAcknowledgedBytes;
        public String error="",failedPhase="",errorType="";public boolean cancelled,aborted;
        public int latencyAttempts,latencyValidSamples;
        public final List<Failure> failures=new ArrayList<>();
        public boolean complete() {return !cancelled&&!aborted&&error.isEmpty()&&latencyMs>=0&&download!=null&&upload!=null;}
        public boolean throughputComplete() {return !cancelled&&!aborted&&download!=null&&upload!=null;}
    }
    /** A changed/disallowed route is fatal; endpoint measurement failures are not. */
    public static final class AbortException extends IOException {public AbortException(String message) {super(message);}}
    public static final class RequestInfo {
        public final String method,endpoint,operation,errorType;public final int httpStatus;public final long elapsedMs,payloadBytes;
        RequestInfo(Request r) {
            method=r.method;endpoint=r.endpoint;operation=r.operation;errorType=r.errorType;httpStatus=r.httpStatus;payloadBytes=r.payloadBytes;
            elapsedMs=TimeUnit.NANOSECONDS.toMillis((r.finished==0?System.nanoTime():r.finished)-r.started);
        }
    }
    public static final class Failure {
        public final String phase,message,errorType;public final long elapsedMs;public final List<RequestInfo> requests;
        Failure(String phase,Throwable cause,Scope scope) {
            this.phase=phase;errorType=cause.getClass().getSimpleName();
            boolean timeout=cause instanceof SocketTimeoutException||cause instanceof TimeoutException;
            String detail=cause.getMessage()==null?errorType:cause.getMessage();
            message=timeout?phase+" request timed out.":phase+" failed: "+detail;
            elapsedMs=scope==null?0:TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-scope.started);
            requests=new ArrayList<>();if(scope!=null)synchronized(scope.recent) {for(Request r:scope.recent)requests.add(new RequestInfo(r));}
        }
    }
    private static final class Request {
        final String method,endpoint;final long payloadBytes,started=System.nanoTime();final Scope scope;
        volatile HttpURLConnection connection;volatile String operation="open_connection",errorType="";volatile int httpStatus=-1;volatile long finished;
        final AtomicBoolean closing=new AtomicBoolean();
        Request(Scope scope,String method,String endpoint,long bytes) {this.scope=scope;this.method=method;this.endpoint=endpoint;payloadBytes=bytes;}
    }
    private static final class Scope {
        final long started=System.nanoTime();final AtomicBoolean stopped=new AtomicBoolean();
        final Set<Request> active=Collections.newSetFromMap(new ConcurrentHashMap<Request,Boolean>());
        final ArrayDeque<Request> recent=new ArrayDeque<>();final ExecutorService pool;
        Scope(int workers) {pool=Executors.newFixedThreadPool(workers,r -> {Thread t=new Thread(r,"sa-speed-phase");t.setDaemon(true);return t;});}
        void close() {
            if(!stopped.compareAndSet(false,true))return;pool.shutdownNow();
            // Closing a hostile/stalled provider must not hold up the next phase or the UI.
            for(Request request:active)if(request.connection!=null)releaseAsync(request);
        }
    }
    private final Connections connections;private final Guard guard;private final Listener listener;private final Config config;
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final AtomicLong received=new AtomicLong(),sent=new AtomicLong(),acknowledged=new AtomicLong();
    public SpeedTestEngine(Connections c,Guard g,Listener l) {this(c,g,l,new Config());}
    public SpeedTestEngine(Connections c,Guard g,Listener l,Config config) {connections=c;guard=g;listener=l;this.config=config;}
    /** Nonblocking; the worker observes this within its polling interval and closes active sockets. */
    public void cancel() {cancelled.set(true);}
    private void check() throws IOException {
        if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Test stopped.");
        try {guard.check();}catch(AbortException e) {throw e;}catch(IOException e) {throw new AbortException(e.getMessage());}
    }
    public Result run() {
        Result result=new Result();String phase="Latency";
        try {
            check();listener.phase(phase);measureLatency(result);
            check();phase="Download";listener.phase(phase);result.download=transfer(false,config.downloadBytes,result);
            check();phase="Upload";listener.phase(phase);result.upload=transfer(true,config.uploadBytes,result);
            check();
        } catch(Exception e) {
            result.cancelled=cancelled.get();result.aborted=!result.cancelled;
            record(result,phase,root(e),null);
            if(result.cancelled)result.error="Stopped; incomplete results.";
        } finally {
            result.receivedBytes=received.get();result.sentBytes=sent.get();result.uploadAcknowledgedBytes=acknowledged.get();
        }
        return result;
    }
    private static Throwable root(Throwable e) {while(e instanceof ExecutionException&&e.getCause()!=null)e=e.getCause();return e;}
    private void canContinue(Exception e) throws Exception {
        Throwable cause=root(e);
        if(cause instanceof AbortException)throw (AbortException)cause;
        if(cause instanceof InterruptedException) {Thread.currentThread().interrupt();throw (InterruptedException)cause;}
        check();
    }
    private static void record(Result result,String phase,Throwable cause,Scope scope) {
        Failure failure=new Failure(phase,cause,scope);result.failures.add(failure);
        if(result.failedPhase.isEmpty()) {result.failedPhase=phase;result.errorType=failure.errorType;}
        result.error+=(result.error.isEmpty()?"":"\n")+failure.message;
    }
    private Request open(Scope scope,String path,int readTimeout,long bytes,boolean upload) throws IOException {
        check();if(scope.stopped.get())throw new InterruptedIOException("Phase ended.");
        Request request=new Request(scope,upload?"POST":"GET",upload?"/__up":"/__down",bytes);scope.active.add(request);
        synchronized(scope.recent) {if(scope.recent.size()==8)scope.recent.removeFirst();scope.recent.addLast(request);}
        try {
            HttpURLConnection c=connections.open(new URL(HOST+path));request.connection=c;
            if(scope.stopped.get())throw new InterruptedIOException("Phase ended.");
            c.setConnectTimeout(Math.min(CONNECT_TIMEOUT_MS,readTimeout));c.setReadTimeout(readTimeout);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
            c.setRequestProperty("Cache-Control","no-cache, no-store");c.setRequestProperty("Accept-Encoding","identity");
            c.setRequestProperty("User-Agent","SA-Band-Lab");return request;
        } catch(IOException|RuntimeException e) {request.errorType=e.getClass().getSimpleName();release(request);throw e;}
    }
    private static void releaseAsync(Request request) {
        if(request.closing.get())return;
        Thread closer=new Thread(() -> {try {release(request);}catch(Exception ignored) {}},"sa-speed-close");closer.setDaemon(true);closer.start();
    }
    private static void release(Request request) {
        if(!request.closing.compareAndSet(false,true))return;
        boolean complete=request.operation.equals("complete");if(complete)request.operation="closing_connection";
        try {if(request.connection!=null)request.connection.disconnect();}
        finally {request.finished=System.nanoTime();if(complete)request.operation="complete";request.scope.active.remove(request);}
    }
    private static void status(Request request) throws IOException {
        if(request.scope.stopped.get())throw new InterruptedIOException("Phase ended.");
        HttpURLConnection c=request.connection;request.operation="response_headers_including_connect_tls";request.httpStatus=c.getResponseCode();
        if(request.httpStatus!=200)throw new IOException("Speed endpoint returned HTTP "+request.httpStatus+".");
        String encoding=c.getContentEncoding();if(encoding!=null&&!encoding.equalsIgnoreCase("identity"))throw new IOException("Unexpected compressed test response.");
    }
    private double latency(Scope scope) throws IOException {
        Request r=open(scope,"/__down?bytes=0&nonce="+UUID.randomUUID(),config.latencySocketMillis,0,false);
        try {long start=System.nanoTime();status(r);r.operation="complete";return (System.nanoTime()-start)/1000000.0;}
        catch(IOException e) {r.errorType=e.getClass().getSimpleName();throw e;}
        finally {releaseAsync(r);}
    }
    private void measureLatency(Result result) throws Exception {
        Scope scope=new Scope(1);double[] samples=new double[3];int valid=0;Throwable failure=null;
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(config.latencyBudgetMillis);
        try {
            for(int i=0;i<samples.length&&System.nanoTime()<deadline;i++) {
                check();result.latencyAttempts++;Future<Double> future=scope.pool.submit(() -> latency(scope));
                try {
                    while(true) {
                        check();long remaining=deadline-System.nanoTime();
                        if(remaining<=0)throw new TimeoutException("Latency phase deadline expired.");
                        try {samples[valid]=future.get(Math.min(100,Math.max(1,TimeUnit.NANOSECONDS.toMillis(remaining))),TimeUnit.MILLISECONDS);valid++;break;}
                        catch(TimeoutException e) {if(System.nanoTime()>=deadline)throw e;}
                    }
                } catch(Exception e) {canContinue(e);failure=root(e);if(!future.isDone())break;}
                finally {if(!future.isDone())future.cancel(true);}
            }
            if(valid>0) {Arrays.sort(samples,0,valid);result.latencyMs=valid%2==0?(samples[valid/2-1]+samples[valid/2])/2:samples[valid/2];listener.latency(result.latencyMs);}
            result.latencyValidSamples=valid;
            if(failure!=null)record(result,"Latency",failure,scope);
        } finally {scope.close();}
    }
    private Rate transfer(boolean upload,long budget,Result result) throws Exception {
        Scope scope=new Scope(config.streams);
        long start=System.nanoTime(),deadline=start+TimeUnit.MILLISECONDS.toNanos(config.phaseMillis);
        AtomicBoolean stop=scope.stopped;AtomicLong completed=new AtomicLong(),lastFinished=new AtomicLong(start);List<Future<?>> tasks=new ArrayList<>();
        for(int i=0;i<config.streams;i++) {
            final long quota=budget/config.streams+(i<budget%config.streams?1:0);
            tasks.add(scope.pool.submit(() -> {
                try {if(upload)upload(scope,quota,deadline,stop,completed);else download(scope,quota,deadline,stop,completed);return null;}
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
            check();if(measuredBytes<=0)throw new IOException(upload?"No upload payload was acknowledged.":"No download data was received.");
        } catch(Exception e) {
            canContinue(e);record(result,upload?"Upload":"Download",root(e),scope);return null;
        } finally {
            scope.close();
            for(Future<?> f:tasks)if(!f.isDone())f.cancel(true);
        }
        return new Rate(measuredBytes,Math.max(1,finished-start),measuredBytes>=budget);
    }
    private boolean running(long deadline,AtomicBoolean stop) throws IOException {check();return !stop.get()&&System.nanoTime()<deadline;}
    private int responseTimeout() {return (int)Math.min(RESPONSE_TIMEOUT_MS,config.phaseMillis);}
    private void download(Scope scope,long quota,long deadline,AtomicBoolean stop,AtomicLong completed) throws IOException {
        byte[] buffer=new byte[65536];long count=0;
        while(count<quota&&running(deadline,stop)) {
            long request=Math.min(DOWNLOAD_REQUEST_BYTES,quota-count);
            Request trace=open(scope,"/__down?bytes="+request+"&nonce="+UUID.randomUUID(),responseTimeout(),request,false);HttpURLConnection c=trace.connection;
            try {
                status(trace);long declared=c.getContentLengthLong();if(declared>=0&&declared!=request)throw new IOException("Unexpected download size.");
                String type=c.getContentType();if(type!=null&&(type.contains("text/html")||type.contains("application/json")))throw new IOException("Speed endpoint returned a page instead of test data.");
                trace.operation="reading_response_body";try(InputStream in=c.getInputStream()) {
                    long part=0;
                    while(part<request&&running(deadline,stop)) {
                        int n=in.read(buffer,0,(int)Math.min(buffer.length,request-part));
                        if(n<0)throw new IOException("Truncated speed-test download.");
                        part+=n;count+=n;received.addAndGet(n);if(running(deadline,stop))completed.addAndGet(n);
                    }
                }
                trace.operation="complete";
            } catch(IOException e) {trace.errorType=e.getClass().getSimpleName();if(e instanceof AbortException||!stop.get()&&System.nanoTime()<deadline)throw e;else return;}
            finally {release(trace);}
        }
    }
    private void upload(Scope scope,long quota,long deadline,AtomicBoolean stop,AtomicLong completed) throws IOException {
        byte[] buffer=new byte[32768];new Random().nextBytes(buffer);long count=0;int nextChunk=UPLOAD_CHUNK_BYTES;
        while(count<quota&&running(deadline,stop)) {
            int chunk=(int)Math.min(nextChunk,quota-count);Request trace=open(scope,"/__up",responseTimeout(),chunk,true);HttpURLConnection c=trace.connection;long requestStart=System.nanoTime();
            try {
                c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/octet-stream");c.setFixedLengthStreamingMode(chunk);
                trace.operation="opening_upload_stream";try(OutputStream out=c.getOutputStream()) {trace.operation="sending_upload";int n=0;while(n<chunk) {if(!running(deadline,stop))return;int take=Math.min(buffer.length,chunk-n);out.write(buffer,0,take);sent.addAndGet(take);n+=take;}}
                status(trace);trace.operation="reading_response_body";
                try(InputStream in=c.getInputStream()) {int total=0,n;while((n=in.read(buffer,0,Math.min(buffer.length,4097-total)))!=-1) {total+=n;if(total>4096)throw new IOException("Unexpected upload response size.");}}
                acknowledged.addAndGet(chunk);if(running(deadline,stop))completed.addAndGet(chunk);count+=chunk;
                trace.operation="complete";
                long elapsed=System.nanoTime()-requestStart;
                if(elapsed<500000000L)nextChunk=Math.min(MAX_UPLOAD_CHUNK_BYTES,nextChunk*2);
                else if(elapsed>1000000000L)nextChunk=Math.max(62500,nextChunk/2);
            } catch(IOException e) {trace.errorType=e.getClass().getSimpleName();if(e instanceof AbortException||!stop.get()&&System.nanoTime()<deadline)throw e;else return;}
            finally {release(trace);}
        }
    }
}
