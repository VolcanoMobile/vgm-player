package net.volcanomobile.vgmplayer.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;

import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.ui.player.MusicPlayerActivity;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class SplashActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 10;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        navigateToPlayer();
    }

    @AfterPermissionGranted(REQUEST_PERMISSION)
    private void navigateToPlayer() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
            Intent intent = new Intent(this, MusicPlayerActivity.class);
            startActivity(intent);
            finish();
        } else {
            EasyPermissions.requestPermissions(this, getString(R.string.read_permission_rational), REQUEST_PERMISSION, Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Forward results to EasyPermissions
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }
}