package net.volcanomobile.vgmplayer.service;

import android.Manifest;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;

import net.volcanomobile.vgmplayer.BuildConfig;
import net.volcanomobile.vgmplayer.R;
import net.volcanomobile.vgmplayer.dao.Album;
import net.volcanomobile.vgmplayer.dao.AlbumDao;
import net.volcanomobile.vgmplayer.dao.AppDatabase;
import net.volcanomobile.vgmplayer.dao.Media;
import net.volcanomobile.vgmplayer.dao.MediaDao;
import net.volcanomobile.vgmplayer.dao.MediaWithAlbum;
import net.volcanomobile.vgmplayer.utils.LogHelper;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class MediaScannerService extends IntentService {

    private static final String TAG = LogHelper.makeLogTag(MediaScannerService.class);

    private static final String ACTION_SCAN_FOLDER = "ACTION_SCAN_FOLDER";

    private static final String CHANNEL_ID = BuildConfig.APPLICATION_ID + ".SCANNER_CHANNEL_ID";
    private static final int NOTIFICATION_ID = 666;

    private final FileFilter midiFilter;
    private final FileFilter artFilter;
    private final BitmapFactory.Options mThumbOptions;
    private File mThumbsDir;

    private NotificationManager mNotificationManager;
    private NotificationCompat.Builder mBuilder;

    public MediaScannerService() {
        super("MediaScannerService");

        midiFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                if(file.isDirectory()) {
                    return !file.getAbsolutePath().endsWith("/Android");
                }
                String name = file.getName().toLowerCase();
                return name.endsWith(".vgm") || name.endsWith(".vgz");
            }
        };
        artFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                if(file.isDirectory()) {
                    return false;
                }
                String name = file.getName().toLowerCase();
                return name.endsWith(".png") || name.endsWith(".jpg");
            }
        };

        mThumbOptions = new BitmapFactory.Options();
        mThumbOptions.inPreferredConfig = Bitmap.Config.RGB_565;
    }

    private IRemoteVGMScannerService mRemoteService = null;
    private CountDownLatch mConnectedSignal = null;
    private ServiceConnection mConnection = new ServiceConnection() {


        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRemoteService = IRemoteVGMScannerService.Stub.asInterface(service);
            if(mConnectedSignal != null) {
                mConnectedSignal.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mRemoteService = null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        mThumbsDir = new File(getFilesDir(), "albumthumbs");
        if (!mThumbsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            mThumbsDir.mkdirs();
        }
    }

    public static void scanFolder(Context context, String folder) {
        Uri data = Uri.fromFile(new File(folder));
        context.startService(new Intent(context, MediaScannerService.class)
                .setAction(ACTION_SCAN_FOLDER).setData(data));
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            if (ACTION_SCAN_FOLDER.equals(intent.getAction())) {
                Uri folder = intent.getData();
                if (folder != null) {
                    scanFolder(folder.getPath());
                }
            }
        }
    }

    private void scanFolder(String root) {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

            Intent remoteScanner = new Intent(this, RemoteVGMScannerService.class);
            remoteScanner.setAction(IRemoteVGMScannerService.class.getName());
            bindService(remoteScanner, mConnection, Context.BIND_AUTO_CREATE);

            MediaDao mediaDao = AppDatabase.getInstance(this).mediaDao();
            AlbumDao albumDao = AppDatabase.getInstance(this).albumDao();

            File rootFolder = new File(root);

            if (!rootFolder.exists() || !rootFolder.isDirectory()) {
                LogHelper.d(TAG, root + " doesn't exists or is not a directory");
                return;
            }

            List<File> files = getFiles(rootFolder);

            if (files.size() == 0) {
                // empty DB
                albumDao.deleteAll();
            } else {
                Map<String, File> filesInDirectory = new HashMap<>();
                List<Long> idsToRemove = new ArrayList<>();

                for (File file : files) {
                    filesInDirectory.put(file.getAbsolutePath(), file);
                }

                // build knownMedias map
                Map<String, Long> knownMedias = new HashMap<>();
                List<MediaWithAlbum> mediaList = mediaDao.loadAll().blockingFirst();
                Set<Long> albumsIdsToCheck = new HashSet<>();

                for (MediaWithAlbum media : mediaList) {
                    long id = media.getUid();
                    String path = Uri.parse(media.getData()).getPath();

                    if (!filesInDirectory.containsKey(path)) {
                        idsToRemove.add(id);
                        albumsIdsToCheck.add(media.getAlbumId());
                    } else {
                        knownMedias.put(path, id);
                    }
                }

                for (int i = 0; i <= idsToRemove.size() / 20; i++) {
                    // remove from DAO by 20
                    List<Long> sub = idsToRemove.subList(i * 20, Math.min((i+1) * 20, idsToRemove.size()));
                    mediaDao.deleteByIds(sub);
                }
                idsToRemove.clear();

                // build knownAlbums map
                Map<String, Album> knownAlbums = new HashMap<>();
                List<Album> albumList = albumDao.loadAll().blockingFirst();

                for (Album album : albumList) {
                    if (albumsIdsToCheck.contains(album.getUid())
                            && mediaDao.loadByAlbumIds(album.getUid()).blockingFirst().size() == 0) {
                        idsToRemove.add(album.getUid());
                    } else {
                        knownAlbums.put(album.getFolder(), album);
                    }
                }

                for (int i = 0; i <= idsToRemove.size() / 20; i++) {
                    // remove from DAO by 20
                    List<Long> sub = idsToRemove.subList(i * 20, Math.min((i+1) * 20, idsToRemove.size()));
                    albumDao.deleteByIds(sub);
                }
                idsToRemove.clear();

                // check for updated Album Artworks
                List<Album> toUpdate = new ArrayList<>();

                for (Album album : knownAlbums.values()) {
                    String folder = album.getFolder();
                    String albumArt = findArt(folder);
                    if (!TextUtils.isEmpty(albumArt) && !albumArt.equals(album.getAlbumArt())) {
                        album.setAlbumArt(albumArt);

                        // update thumb
                        generateThumbnail(albumArt, album.getUid());

                        toUpdate.add(album);
                    } else if (TextUtils.isEmpty(albumArt) && !TextUtils.isEmpty(album.getAlbumArt())) {
                        album.setAlbumArt(null);
                        File thumbFile = new File(mThumbsDir, String.valueOf(album.getUid()));
                        //noinspection ResultOfMethodCallIgnored
                        thumbFile.delete();
                        toUpdate.add(album);
                    }
                }

                // update DAO
                albumDao.update(toUpdate);

                // add new medias
                List<Media> newMedias = new ArrayList<>();

                for (String fileName : filesInDirectory.keySet()) {
                    File file = new File(fileName);

                    if (knownMedias.get(fileName) == null) {
                        LogHelper.d(TAG, "new file: " + fileName);

                        showNotification(file.getName());

                        String parentFolder = file.getParent();
                        Album album = knownAlbums.get(parentFolder);

                        // new album ?
                        if (album == null) {
                            album = new Album();
                            Uri parentUri = Uri.fromFile(new File(parentFolder));
                            album.setTitle(parentUri.getLastPathSegment());
                            album.setFolder(parentFolder);
                            String albumArt = findArt(parentFolder);
                            if (albumArt != null) {
                                album.setAlbumArt(albumArt);
                            }
                            album.setUid(albumDao.insert(album));
                            knownAlbums.put(parentFolder, album);

                            if (albumArt != null) {
                                generateThumbnail(albumArt, album.getUid());
                            }
                        }

                        if(mRemoteService == null) {
                            // wait for connection
                            try {
                                mConnectedSignal = new CountDownLatch(1);
                                mConnectedSignal.await();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        mConnectedSignal = null;

                        long duration = 0;
                        try {
                            duration = mRemoteService.getFileDuration(file.getAbsolutePath());
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }

                        String title = file.getName();

                        Media media = new Media();
                        media.setTitle(title.substring(0, title.lastIndexOf('.')));
                        media.setDuration(duration);
                        media.setAlbumId(album.getUid());
                        media.setAlbum(album.getTitle());
                        media.setData(Uri.fromFile(file).toString());

                        newMedias.add(media);
                    }
                }

                if (newMedias.size() > 0) {
                    mediaDao.insert(newMedias);
                }
            }

            unbindService(mConnection);

        } else {
            LogHelper.d(TAG, "READ_EXTERNAL_STORAGE not granted");
        }

        cancelNotification();
    }

    private void showNotification(String filename) {
        // Notification channels are only supported on Android O+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        if (mBuilder == null) {
            mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID);
            mBuilder.setSmallIcon(R.drawable.ic_refresh_black_24dp);
            mBuilder.setProgress(0, 0, true);
            mBuilder.setContentTitle(getString(R.string.scan_notification_title));
        }
        mBuilder.setContentText(filename);
        mNotificationManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void cancelNotification() {
        mNotificationManager.cancel(NOTIFICATION_ID);
    }

    private void generateThumbnail(@NonNull String albumArt, long albumId) {
        OutputStream out = null;
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            mThumbOptions.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(albumArt, mThumbOptions);

            // Calculate inSampleSize
            mThumbOptions.inSampleSize = calculateInSampleSize(mThumbOptions, 128, 128);

            // Decode bitmap with inSampleSize set
            mThumbOptions.inJustDecodeBounds = false;
            Bitmap art = BitmapFactory.decodeFile(albumArt, mThumbOptions);

            if (art != null) {
                Bitmap thumb = ThumbnailUtils.extractThumbnail(art, 128, 128, ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
                if (thumb == null) {
                    if (!art.isRecycled()) {
                        art.recycle();
                    }
                    return;
                }
                File thumbFile = new File(mThumbsDir, String.valueOf(albumId));
                //noinspection ResultOfMethodCallIgnored
                thumbFile.delete();
                out = new BufferedOutputStream(new FileOutputStream(thumbFile));
                thumb.compress(Bitmap.CompressFormat.JPEG, 75, out);
                thumb.recycle();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private static int calculateInSampleSize(
            BitmapFactory.Options options, int reqWidth, int reqHeight) {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    @NonNull
    private List<File> getFiles(@NonNull File directory) {
        List<File> result = new ArrayList<>();

        File[] files = directory.listFiles(midiFilter);
        if(files == null) {
            return result;
        }

        for(File file : files) {
            if(file.isDirectory()) {
                result.addAll(getFiles(file));
            } else {
                result.add(file);
            }
        }

        return result;
    }

    @Nullable
    private String findArt(@NonNull String parent) {
        File folder = new File(parent);
        File[] images = folder.listFiles(artFilter);
        return (images != null && images.length >= 1) ? images[0].getAbsolutePath() : null;
    }

    /**
     * Creates Notification Channel. This is required in Android O+ to display notifications.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        if (mNotificationManager.getNotificationChannel(CHANNEL_ID) == null) {
            NotificationChannel notificationChannel =
                    new NotificationChannel(CHANNEL_ID,
                            getString(R.string.scanner_notification_channel),
                            NotificationManager.IMPORTANCE_LOW);

            notificationChannel.setDescription(
                    getString(R.string.scanner_notification_channel_description));

            mNotificationManager.createNotificationChannel(notificationChannel);
        }
    }
}
