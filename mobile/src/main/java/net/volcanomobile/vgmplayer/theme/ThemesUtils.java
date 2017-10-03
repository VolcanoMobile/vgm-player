package net.volcanomobile.vgmplayer.theme;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by philippesimons on 2/03/16.
 */
public class ThemesUtils {

    private static final String ACTION_THEME_CHANGED = "ACTION_THEME_CHANGED";
    private static final IntentFilter INTENT_FILTER = new IntentFilter(ACTION_THEME_CHANGED);
    private static final String EXTRA_THEME_MARSHALLING_ID = "EXTRA_THEME_MARSHALLING_ID";

    private static Map<Activity, BroadcastReceiver> RECEIVERS_MAP = new HashMap<>();

    public static void registerActivity(Activity activity) {
        ThemeChangedBroadcastReceiver receiver = new ThemeChangedBroadcastReceiver(activity);
        LocalBroadcastManager.getInstance(activity).registerReceiver(receiver, INTENT_FILTER);
        RECEIVERS_MAP.put(activity, receiver);
    }

    public static void unregisterActivity(Activity activity) {
        BroadcastReceiver receiver = RECEIVERS_MAP.get(activity);
        if(receiver != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(receiver);
            RECEIVERS_MAP.remove(activity);
        }
    }

    private static class ThemeChangedBroadcastReceiver extends BroadcastReceiver {

        private Activity activity;

        private ThemeChangedBroadcastReceiver(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            activity.recreate();
        }
    }


    public static void broadcastThemeChanged(@NonNull Context context, @NonNull Theme theme) {
        Intent broadcast = new Intent(ACTION_THEME_CHANGED);
        broadcast.putExtra(EXTRA_THEME_MARSHALLING_ID, theme.getMarshallingId());
        LocalBroadcastManager.getInstance(context).sendBroadcast(broadcast);
    }
}
