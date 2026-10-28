package dev.saband.lab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Credential-protected plans are used only after the normal unlocked boot broadcast. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))BootPlans.schedule(context,0);
    }
}
