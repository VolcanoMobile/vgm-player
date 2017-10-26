/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.volcanomobile.vgmplayer;

import android.net.wifi.WifiManager;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.kobakei.ratethisapp.RateThisApp;

import net.volcanomobile.vgmplayer.theme.Theme;
import net.volcanomobile.vgmplayer.theme.ThemesUtils;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteOrder;

/**
 * The {@link android.app.Application} for the uAmp application.
 */
public class Application extends android.app.Application {

    private static Application sInstance;
    private Theme currentTheme = Theme.getFallback();

    public Application() {
        sInstance = this;
    }

    public static Application getInstance() {
        return sInstance;
    }

    /**
     * Gets the currently-selected theme for the app.
     * @return Theme that is currently selected, which is the actual theme ID that can
     * be passed to setTheme() when creating an activity.
     */
    @NonNull
    public Theme getCurrentTheme() {
        return currentTheme;
    }

    public void setCurrentTheme(@NonNull Theme theme) {
        if(theme != currentTheme) {
            currentTheme = theme;
            ThemesUtils.broadcastThemeChanged(this, theme);
        }
    }

    private Theme unmarshalCurrentTheme() {
        int id = PreferencesHelper.getInstance(this).getThemeId();
        Theme result = Theme.ofMarshallingId(id);
        if (result == null) {
            Log.d("Application", "Theme id=" + id + " is invalid, using fallback.");
            result = Theme.getFallback();
        }
        return result;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        initStrictMode();

        currentTheme = unmarshalCurrentTheme();
        RateThisApp.Config config = new RateThisApp.Config(3, 10);
        RateThisApp.init(config);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Glide.get(this).clearMemory();
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        Glide.get(this).trimMemory(level);
    }

    private void initStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());

            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
        }
    }

    public static String getWifiIpAddress() {
        WifiManager wifiManager = (WifiManager) sInstance.getSystemService(WIFI_SERVICE);
        int ipAddress = wifiManager.getConnectionInfo().getIpAddress();

        // Convert little-endian to big-endianif needed
        if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
            ipAddress = Integer.reverseBytes(ipAddress);
        }

        byte[] ipByteArray = BigInteger.valueOf(ipAddress).toByteArray();

        String ipAddressString;
        try {
            ipAddressString = InetAddress.getByAddress(ipByteArray).getHostAddress();
        } catch (UnknownHostException ex) {
            Log.e("WIFIIP", "Unable to get host address.");
            ipAddressString = null;
        }

        return ipAddressString;
    }
}
