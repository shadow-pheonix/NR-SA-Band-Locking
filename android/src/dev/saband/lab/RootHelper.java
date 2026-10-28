package dev.saband.lab;

import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Shared bounded root transport for foreground and boot-time operations. */
public final class RootHelper {
    private static String quote(String s) {return "'"+s.replace("'","'\\''")+"'";}
    public static JSONObject invoke(android.content.Context context,String... args) throws Exception {
        File helper=new File(context.getApplicationInfo().nativeLibraryDir,"libsahelper.so");
        if(!helper.isFile())throw new IOException("ARM64 root helper was not extracted by Android.");
        StringBuilder command=new StringBuilder(quote(helper.getAbsolutePath()));
        for(String a:args)command.append(' ').append(quote(a));
        Process p=new ProcessBuilder("su","-c",command.toString()).redirectErrorStream(true).start();
        ByteArrayOutputStream data=new ByteArrayOutputStream();AtomicReference<IOException> readError=new AtomicReference<>();
        // Drain while the helper runs: a long diagnostic reply must not fill
        // the subprocess pipe while its parent is waiting for it to exit.
        Thread reader=new Thread(() -> {
            try(InputStream in=p.getInputStream()) {byte[] chunk=new byte[2048];int n;
                while((n=in.read(chunk))!=-1) {if(data.size()+n>32768)throw new IOException("Unexpectedly large helper output.");data.write(chunk,0,n);}
            } catch(IOException e) {readError.set(e);}
        },"sa-helper-output");reader.setDaemon(true);reader.start();
        if(!p.waitFor(75,TimeUnit.SECONDS)) {p.destroyForcibly();reader.join(1000);throw new IOException("Root helper timed out. Retain the backup and read preferences before retrying.");}
        reader.join(2000);
        if(reader.isAlive()) {p.getInputStream().close();throw new IOException("Helper output did not close. Read preferences before retrying.");}
        if(readError.get()!=null)throw readError.get();
        String output=data.toString("UTF-8");
        JSONObject result=null;
        for(String line:output.split("\n"))if(line.trim().startsWith("{")) {try {result=new JSONObject(line.trim());}catch(Exception ignored) {}}
        if(result==null)throw new IOException("No helper response. Grant root to SA Band Lab. Output: "+output);
        return result;
    }
}
