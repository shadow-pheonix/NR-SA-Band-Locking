package dev.saband.lab;

import android.content.Context;
import android.media.*;
import android.os.*;

/** Quiet, opt-in, bounded chirp for a genuinely new measurement, never repeated cache. */
public final class SignalFeedback {
    private final Handler handler=new Handler(Looper.getMainLooper());private final AudioManager manager;
    private AudioTrack track;private long last=-1000;private boolean enabled;
    public SignalFeedback(Context context) {manager=context.getSystemService(AudioManager.class);}
    public void enabled(boolean value) {enabled=value;if(!value)stop();}
    public boolean enabled() {return enabled;}
    public void stop() {handler.removeCallbacksAndMessages(null);if(track!=null) {try {track.stop();}catch(RuntimeException ignored) {}try {track.release();}catch(RuntimeException ignored) {}track=null;}}
    public boolean sample(int rsrp,long now) {
        if(!enabled||!SignalHunt.validRsrp(rsrp)||now-last<1000||manager==null)return true;
        try {
            if(manager.getRingerMode()!=AudioManager.RINGER_MODE_NORMAL)return true;last=now;stop();
            final int rate=16000,count=1440;short[] pcm=new short[count];double hz=SignalHunt.toneHz(rsrp);
            for(int i=0;i<count;i++) {double envelope=Math.min(1,i/160.0)*Math.min(1,(count-i)/320.0);pcm[i]=(short)(Math.sin(2*Math.PI*hz*i/rate)*3500*envelope);}
            track=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(count*2).build();
            if(track.getState()!=AudioTrack.STATE_INITIALIZED||track.write(pcm,0,pcm.length)!=pcm.length)throw new IllegalStateException("Audio unavailable");
            track.play();handler.postDelayed(this::stop,160);return true;
        } catch(RuntimeException e) {stop();enabled=false;return false;}
    }
}
