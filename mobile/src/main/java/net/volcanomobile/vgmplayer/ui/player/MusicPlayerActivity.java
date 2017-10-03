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
package net.volcanomobile.vgmplayer.ui.player;

import android.Manifest;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.text.TextUtils;

import com.kobakei.ratethisapp.RateThisApp;

import net.volcanomobile.vgmplayer.Application;
import net.volcanomobile.vgmplayer.BuildConfig;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.service.MediaScannerService;
import net.volcanomobile.vgmplayer.service.MusicService;
import net.volcanomobile.vgmplayer.ui.BaseActivity;
import net.volcanomobile.vgmplayer.utils.LogHelper;
import net.volcanomobile.vgmplayer.utils.MediaIDHelper;
import net.volcanomobile.vgmplayer.utils.PreferencesHelper;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

/**
 * Main activity for the music player.
 * This class hold the MediaBrowser and the MediaController instances. It will create a MediaBrowser
 * when it is created and connect/disconnect on start/stop. Thus, a MediaBrowser will be always
 * connected while this activity is running.
 */
public class MusicPlayerActivity extends BaseActivity
        implements MediaBrowserFragment.MediaFragmentListener {

    private static final int REQUEST_PERMISSION = 10;

    private static final String TAG = LogHelper.makeLogTag(MusicPlayerActivity.class);
    private static final String SAVED_PENDING_DELETE_MUSIC_ID = BuildConfig.APPLICATION_ID + ".SAVED_PENDING_DELETE_MUSIC_ID";
    private static final String FRAGMENT_TAG = "mmp_list_container";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LogHelper.d(TAG, "Activity onCreate");

        setContentView(R.layout.activity_player);

        initializeToolbar();
        initializeBottomSheet();

        initializeFromParams(getIntent());

        // Only check if a full screen player is needed on the first time:
        if (savedInstanceState == null) {
            startFullScreenActivityIfNeeded(getIntent());
        } else {
            pendingDeleteMusicId = savedInstanceState.getString(SAVED_PENDING_DELETE_MUSIC_ID);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (getBrowseFragment() == null) {
            navigateToBrowser(null);
        }

        RateThisApp.onStart(this);
        RateThisApp.showRateDialogIfNeeded(this);

        String rootFolder = PreferencesHelper.getInstance(this).getRootFolder();
        if (!TextUtils.isEmpty(rootFolder)) {
            MediaScannerService.scanFolder(this, rootFolder);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (pendingDeleteMusicId != null) {
            outState.putString(SAVED_PENDING_DELETE_MUSIC_ID, pendingDeleteMusicId);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onMediaItemSelected(MediaBrowserCompat.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.getMediaId());
        if (item.isPlayable()) {
            MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(this);
            mediaController.getTransportControls().playFromMediaId(item.getMediaId(), null);
        } else if (item.isBrowsable()) {
            navigateToBrowser(item.getMediaId());
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.getMediaId());
        }
    }

    private String pendingDeleteMusicId;
    @Override
    public void onDeleteMediaItem(@NonNull MediaBrowserCompat.MediaItem item) {
        pendingDeleteMusicId = MediaIDHelper.extractMusicIDFromMediaID(item.getMediaId());
        doDeleteMediaItem();
    }

    @AfterPermissionGranted(REQUEST_PERMISSION)
    private void doDeleteMediaItem() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            if (getMediaBrowser().isConnected()) {
                Bundle args = new Bundle();
                args.putString(MusicService.ARG_MUSIC_ID, pendingDeleteMusicId);
                getMediaBrowser().sendCustomAction(MusicService.CUSTOM_ACTION_DELETE_ITEM, args, null);
                pendingDeleteMusicId = null;
            }
        } else {
            EasyPermissions.requestPermissions(this, getString(R.string.write_delete_permission_rational),
                    REQUEST_PERMISSION, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
    }

    private void navigateToBrowser(String mediaId) {
        LogHelper.d(TAG, "navigateToBrowser, mediaId=" + mediaId);
        MediaBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = MediaBrowserFragment.newInstance(mediaId);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null) {
                transaction.addToBackStack(null);
            }
            transaction.commit();
        }
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getSupportFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    protected void onMediaControllerConnected(@NonNull MediaControllerCompat mediaController) {
        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment != null) {
            fragment.onConnected(mediaController);
        }

        if (pendingDeleteMusicId != null && EasyPermissions.hasPermissions(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            Bundle args = new Bundle();
            args.putString(MusicService.ARG_MUSIC_ID, pendingDeleteMusicId);
            getMediaBrowser().sendCustomAction(MusicService.CUSTOM_ACTION_DELETE_ITEM, args, null);
            pendingDeleteMusicId = null;
        }
    }

    @Override
    protected void setTheme() {
        setTheme(Application.getInstance().getCurrentTheme().getOverlapSystemBarId());
    }
}
