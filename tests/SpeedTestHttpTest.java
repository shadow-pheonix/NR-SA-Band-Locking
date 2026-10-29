package dev.saband.lab;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Real socket/HTTP regression for a stalled zero-byte probe and independent phases. */
public final class SpeedTestHttpTest {
    private static void check(boolean condition) {if(!condition)throw new AssertionError();}
    private static final SpeedTestEngine.Listener QUIET=new SpeedTestEngine.Listener() {
        public void phase(String p) {}public void progress(String p,long b,long n) {}
    };
    public static void main(String[] args) throws Exception {
        AtomicBoolean failDownload=new AtomicBoolean();AtomicInteger downloads=new AtomicInteger(),uploads=new AtomicInteger();
        AtomicLong sentToServer=new AtomicLong();CountDownLatch latencyEntered=new CountDownLatch(1);AtomicInteger runId=new AtomicInteger(1);
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        ExecutorService handlers=Executors.newCachedThreadPool(r -> {Thread t=new Thread(r,"local-speed-endpoint");t.setDaemon(true);return t;});server.setExecutor(handlers);
        server.createContext("/__down",exchange -> {
            try {
                long bytes=Long.parseLong(exchange.getRequestURI().getQuery().split("&")[0].substring(6));
                if(bytes==0) {if(exchange.getRequestURI().getQuery().contains("&run=3"))latencyEntered.countDown();try {Thread.sleep(1000);}catch(InterruptedException e) {return;}}
                else {downloads.incrementAndGet();if(failDownload.get()) {exchange.sendResponseHeaders(503,-1);return;}}
                exchange.getResponseHeaders().set("Content-Type","application/octet-stream");
                if(bytes==0)exchange.sendResponseHeaders(200,-1);
                else {
                    exchange.sendResponseHeaders(200,bytes);byte[] block=new byte[4096];
                    try(OutputStream out=exchange.getResponseBody()) {while(bytes>0) {int n=(int)Math.min(bytes,block.length);out.write(block,0,n);bytes-=n;}}
                }
            } catch(IOException ignored) {} finally {exchange.close();}
        });
        server.createContext("/__up",exchange -> {
            try {
                uploads.incrementAndGet();byte[] block=new byte[4096];int n;
                try(InputStream in=exchange.getRequestBody()) {while((n=in.read(block))!=-1)sentToServer.addAndGet(n);}
                exchange.sendResponseHeaders(200,-1);
            } catch(IOException ignored) {} finally {exchange.close();}
        });
        server.start();int port=server.getAddress().getPort();
        SpeedTestEngine.Connections local=url -> (HttpURLConnection)new URL("http",server.getAddress().getAddress().getHostAddress(),port,url.getFile()+(url.getQuery()==null?"?":"&")+"run="+runId.get()).openConnection();
        SpeedTestEngine.Config config=new SpeedTestEngine.Config(32768,16384,2000,2,250,100);
        try {
            SpeedTestEngine.Result r=new SpeedTestEngine(local,() -> {},QUIET,config).run();
            check(!r.complete()&&r.throughputComplete()&&r.latencyMs<0);
            check(r.download.bytes==32768&&r.upload.bytes==16384&&sentToServer.get()==16384);
            check(downloads.get()>0&&uploads.get()>0&&r.failedPhase.equals("Latency"));
            check(r.failures.get(0).requests.get(0).operation.equals("response_headers_including_connect_tls"));
            failDownload.set(true);uploads.set(0);runId.set(2);
            r=new SpeedTestEngine(local,() -> {},QUIET,config).run();
            check(r.download==null&&r.upload!=null&&r.failures.size()==2&&uploads.get()>0);
            check(r.failures.get(1).requests.stream().anyMatch(q -> q.httpStatus==503));
            downloads.set(0);uploads.set(0);runId.set(3);
            SpeedTestEngine stopped=new SpeedTestEngine(local,() -> {},QUIET,config);ExecutorService run=Executors.newSingleThreadExecutor();
            Future<SpeedTestEngine.Result> f=run.submit(() -> stopped.run());
            try {
                check(latencyEntered.await(1,TimeUnit.SECONDS));stopped.cancel();r=f.get(2,TimeUnit.SECONDS);
                check(r.cancelled&&downloads.get()==0&&uploads.get()==0);
            } finally {run.shutdownNow();}
            System.out.println("Real HTTP checks passed: delayed ping headers do not block download/upload; HTTP 503 stays phase-local; Stop prevents later phases. Localhost only.");
        } finally {server.stop(0);handlers.shutdownNow();}
    }
}
