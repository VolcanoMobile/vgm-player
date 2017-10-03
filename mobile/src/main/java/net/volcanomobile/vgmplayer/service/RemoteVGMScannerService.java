package net.volcanomobile.vgmplayer.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.Nullable;

/**
 * Created by philippesimons on 13/10/16.
 */

public class RemoteVGMScannerService extends Service {

    static {
        System.loadLibrary("vgmplay");
        System.loadLibrary("VGMScanner_JNI");
    }

    private final IRemoteVGMScannerService.Stub mBinder = new IRemoteVGMScannerService.Stub() {

        @Override
        public int getFileDuration(String fileName) throws RemoteException {
            return nativeGetDuration(fileName);
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private native int nativeGetDuration(String filename);

}
