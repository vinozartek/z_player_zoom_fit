package com.flutter_zplayer.video;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.analytics.AnalyticsListener;
import com.google.android.exoplayer2.offline.DownloadHelper;
import com.google.android.exoplayer2.offline.DownloadRequest;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.ExoPlayerView;
import com.google.android.exoplayer2.ui.PlayerControlView;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.JSONMethodCodec;
import kotlin.jvm.internal.Intrinsics;

import com.flutter_zplayer.FlutterAVPlayer;
import com.flutter_zplayer.MediaNotificationManagerService;
import com.flutter_zplayer.PlayerNotificationUtil;
import com.flutter_zplayer.PlayerState;
import com.flutter_zplayer.R;
import com.flutter_zplayer.TinyDB;

import static android.media.MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT;

public class PlayerLayout extends ExoPlayerView implements  DownloadTracker.Listener,FlutterAVPlayer, EventChannel.StreamHandler, PlaybackPreparer, PlayerControlView.VisibilityListener {
    /**
     * The notification channel id we'll send notifications too
     */
    public static final String mNotificationChannelId = "NotificationBarController";
    /**
     * Playback Rate for the MediaPlayer is always 1.0.
     */
    private static final float PLAYBACK_RATE = 1.0f;
    /**
     * The notification id.
     */
    private static final int NOTIFICATION_ID = 0;
    public static SimpleExoPlayer activePlayer;
    private boolean isShowingTrackSelectionDialog;
    private boolean isShowingDownloadTrackSelectionDialog;
    private final String TAG = "PlayerLayout";
    /**
     * Reference to the {@link SimpleExoPlayer}
     */
    SimpleExoPlayer mPlayerView;
    boolean isBound = true;
    private PlayerLayout instance;
    /**
     * The underlying {@link MediaSessionCompat}.
     */
    private MediaSessionCompat mMediaSessionCompat;
    /**
     * An instance of Flutter event sink
     */
    private EventChannel.EventSink eventSink;
    /**
     * App main activity
     */
    private Activity activity;
    private FragmentManager fragmentManager;
    private int viewId;

    private DefaultTrackSelector trackSelector;

    /**
     * Context
     */
    private DataSource.Factory dataSourceFactory;
    private Context context;

    private BinaryMessenger messenger;

    private String url = "";

    private String title = "";

    private String subtitle = "";
    private String userId="";

    private String preferredAudioLanguage = "mul";

    private long position = -1;
    private boolean useExtensionRenderers;
    private DownloadTracker downloadTracker;

    private boolean autoPlay = false;
    private boolean startAutoPlay;
    private int startWindow;
    private long startPosition;
    DownloadApplication application;
    private boolean showControls = false;
    private static final String KEY_WINDOW = "window";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";
    TinyDB tinyDB;
    ArrayList<String> downloadList;
    private long mediaDuration = 0L;
    /**
     * Whether we have bound to a {@link MediaNotificationManagerService}.
     */
    private boolean mIsBoundMediaNotificationManagerService;
    /**
     * The {@link MediaNotificationManagerService} we are bound to.
     */
    private MediaNotificationManagerService mMediaNotificationManagerService;
    /**
     * The {@link ServiceConnection} serves as glue between this activity and the {@link MediaNotificationManagerService}.
     */
    private ServiceConnection mMediaNotificationManagerServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {

            mMediaNotificationManagerService = ((MediaNotificationManagerService.MediaNotificationManagerServiceBinder) service)
                    .getService();

            mMediaNotificationManagerService.setActivePlayer(instance);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

            mMediaNotificationManagerService = null;
        }
    };

    public PlayerLayout(Context context) {
        super(context);
    }

    public PlayerLayout(@NonNull Context context,
                        Activity activity,
                        BinaryMessenger messenger,
                        int id,
                        Object arguments) {

        super(context);
        this.activity = activity;

        this.context = context;

        this.messenger = messenger;

        this.viewId = id;
        // App app = (App)getApplicationContext();


        try {

            JSONObject args = (JSONObject) arguments;

            this.url = args.getString("url");
            this.userId=args.getString("userId");
            this.title = args.getString("title");

            this.subtitle = args.getString("subtitle");

            this.preferredAudioLanguage = args.getString("preferredAudioLanguage");

            this.position = Double.valueOf(args.getDouble("position")).intValue();

            this.autoPlay = args.getBoolean("autoPlay");

            this.showControls = args.getBoolean("showControls");

            TinyDB tinyDB=new TinyDB(activity);
            tinyDB.putString("userId",userId);
            new EventChannel(
                    messenger,
                    "zplayer/NativeVideoPlayerEventChannel_" + this.viewId,
                    JSONMethodCodec.INSTANCE).setStreamHandler(this);
            LocalBroadcastManager lbm = LocalBroadcastManager.getInstance(context);
            lbm.registerReceiver(receiver, new IntentFilter("download_status"));
            initPlayer();


        } catch (Exception e) { /* ignore */ }
        tinyDB=new TinyDB(context);
        downloadList=tinyDB.getListString("urls");
        instance = this;
        /* release previous instance */
        if (activePlayer != null) {

            activePlayer.release();
        }

        activePlayer = mPlayerView;
    }


    @Override
    public void onListen(Object o, EventChannel.EventSink eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public void onCancel(Object o) {
        this.eventSink = null;
        downloadTracker.addListener(this);
    }

    private void initPlayer() {
        Log.e("zxfjhnsdjnfh","sfzdlkjsdkf");
        application =new DownloadApplication(activity,context,userId);
        useExtensionRenderers = application.useExtensionRenderers();
        Log.e("fnjkds ",url);
        downloadTracker = application.getDownloadTracker(userId);
        downloadTracker.addListener(this);
        trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(
                trackSelector.buildUponParameters()
        );

        mPlayerView = new SimpleExoPlayer.Builder(context).setTrackSelector(trackSelector).build();
        mPlayerView.setPlayWhenReady(this.autoPlay);

        mPlayerView.addAnalyticsListener(new PlayerAnalyticsEventsListener());

        if (this.position >= 0) {

            mPlayerView.seekTo(this.position);
        }

        setUseController(showControls);


        this.setPlayer(mPlayerView);
        this.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.e("samjhjsahfnsadf","skfjidsafdf");
                JSONObject message = new JSONObject();
                try {
                    message.put("name", "ClickListener");
                    message.put("onClicked",true);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                eventSink.success(message);
            }
        });
        listenForPlayerTimeChange();
        updateMediaSource();
        //    setupMediaSession();
//


    }


    private void setupMediaSession() {

        ComponentName receiver = new ComponentName(context.getPackageName(),
                RemoteReceiver.class.getName());

        /* Create a new MediaSession */
        mMediaSessionCompat = new MediaSessionCompat(context,
                PlayerLayout.class.getSimpleName(), receiver, null);

        mMediaSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                | MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);

        mMediaSessionCompat.setCallback(new MediaSessionCallback());

        mMediaSessionCompat.setActive(true);

        setAudioMetadata();

        updatePlaybackState(PlayerState.PLAYING);
    }

    private void setAudioMetadata() {

        MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
                .build();

        mMediaSessionCompat.setMetadata(metadata);
    }

    private PlaybackStateCompat.Builder getPlaybackStateBuilder() {

        PlaybackStateCompat playbackState = mMediaSessionCompat.getController().getPlaybackState();

        return playbackState == null
                ? new PlaybackStateCompat.Builder()
                : new PlaybackStateCompat.Builder(playbackState);
    }

    private void updatePlaybackState(PlayerState playerState) {

        if (mMediaSessionCompat == null) return;

        PlaybackStateCompat.Builder newPlaybackState = getPlaybackStateBuilder();

        long capabilities = getCapabilities(playerState);

        newPlaybackState.setActions(capabilities);

        int playbackStateCompat = PlaybackStateCompat.STATE_NONE;

        switch (playerState) {
            case PLAYING:
                playbackStateCompat = PlaybackStateCompat.STATE_PLAYING;
                break;
            case PAUSED:
                playbackStateCompat = PlaybackStateCompat.STATE_PAUSED;
                break;
            case BUFFERING:
                playbackStateCompat = PlaybackStateCompat.STATE_BUFFERING;
                break;
            case IDLE:
                playbackStateCompat = PlaybackStateCompat.STATE_STOPPED;
                break;
        }
        newPlaybackState.setState(playbackStateCompat, (long) mPlayerView.getCurrentPosition(), PLAYBACK_RATE);

        mMediaSessionCompat.setPlaybackState(newPlaybackState.build());

        updateNotification(capabilities);
    }

    private @PlaybackStateCompat.Actions
    long getCapabilities(PlayerState playerState) {
        long capabilities = 0;

        switch (playerState) {
            case PLAYING:
            case BUFFERING:
                capabilities |= PlaybackStateCompat.ACTION_PAUSE
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case PAUSED:
                capabilities |= PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_STOP;
                break;
            case IDLE:
                capabilities |= PlaybackStateCompat.ACTION_PLAY;
                break;
        }

        return capabilities;
    }

    private void updateNotification(long capabilities) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            createNotificationChannel();
        }

        NotificationCompat.Builder notificationBuilder = PlayerNotificationUtil.from(
                activity, context, mMediaSessionCompat, mNotificationChannelId);

        if ((capabilities & PlaybackStateCompat.ACTION_PAUSE) != 0) {
            notificationBuilder.addAction(R.drawable.ic_pause, "Pause",
                    PlayerNotificationUtil.getActionIntent(context, KeyEvent.KEYCODE_MEDIA_PAUSE));
        }

        if ((capabilities & PlaybackStateCompat.ACTION_PLAY) != 0) {
            notificationBuilder.addAction(R.drawable.ic_play, "Play",
                    PlayerNotificationUtil.getActionIntent(context, KeyEvent.KEYCODE_MEDIA_PLAY));
        }

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {

            notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build());
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {

        NotificationManager notificationManager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        CharSequence channelNameDisplayedToUser = "Notification Bar Controls";

        int importance = NotificationManager.IMPORTANCE_LOW;

        NotificationChannel newChannel = new NotificationChannel(
                mNotificationChannelId, channelNameDisplayedToUser, importance);

        newChannel.setDescription("All notifications");

        newChannel.setShowBadge(false);

        newChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        if (notificationManager != null) {

            notificationManager.createNotificationChannel(newChannel);
        }
    }

    private void cleanPlayerNotification() {
        NotificationManager notificationManager = (NotificationManager)
                getContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (notificationManager != null) {

            notificationManager.cancel(NOTIFICATION_ID);
        }
    }

    private void doBindMediaNotificationManagerService() {

        Intent service = new Intent(this.context,
                MediaNotificationManagerService.class);

        this.context.bindService(service, mMediaNotificationManagerServiceConnection, Context.BIND_AUTO_CREATE);

        mIsBoundMediaNotificationManagerService = true;

        this.context.startService(service);
    }

    private void doUnbindMediaNotificationManagerService() {

        if (mIsBoundMediaNotificationManagerService) {

            this.context.unbindService(mMediaNotificationManagerServiceConnection);

            mIsBoundMediaNotificationManagerService = false;
        }
    }

    public void pause() {
        if (mPlayerView != null && mPlayerView.isPlaying()) {
            mPlayerView.setPlayWhenReady(false);
        }
    }

    public void play() {
        if (mPlayerView != null && !mPlayerView.isPlaying()) {
            mPlayerView.setPlayWhenReady(true);
        }
    }

    /* onTime listener */
    private void listenForPlayerTimeChange() {
        final Handler handler = new Handler();
        final boolean[] isStarted = {false};
        TinyDB tinyDB=new TinyDB(context);
        String uid=tinyDB.getString("userId");
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if(eventSink!=null&&!isStarted[0]){
                        isStarted[0] =true;
                        try {
                            JSONObject message = new JSONObject();
                            if(downloadTracker.isDownloaded(Uri.parse(url))&&downloadList!=null&&downloadList.size()!=0){

                                if(downloadList.contains(url+"?status=completed&"+uid)){
                                    message.put("name", "onDownloadStatus");
                                    message.put("download_status",true);
                                    eventSink.success(message);
                                }else if(downloadList.contains(url+"?status=downloading&"+uid)){
                                    message.put("name", "onDownloading");
                                    message.put("onDownloading",true);
                                    eventSink.success(message);
                                }
                            }else if(downloadTracker.isDownloaded(Uri.parse(url))){
                                if(downloadTracker.downloadManager.isIdle()) {
                                    DownloadApplication application = new DownloadApplication(activity, context,userId);
                                    useExtensionRenderers = application.useExtensionRenderers();
                                    downloadTracker = application.getDownloadTracker(userId);
                                    downloadTracker.removeMediaUrl(Uri.parse(url));
                                    downloadTracker.addListener(this::run);
                                    message.put("download_status", false);
                                    eventSink.success(message);
                                }else{
                                    JSONObject message1 = new JSONObject();
                                    message1.put("name", "onDownloading");
                                    message1.put("onDownloading", true);
                                    eventSink.success(message1);
                                }
                            }else{
                                message.put("download_status",false);
                                eventSink.success(message);
                            }


                        } catch (JSONException e) {
                            Log.e(TAG, "onDownloadStatus: " + e.getMessage(), e);
                        }
                    }
                    if (mPlayerView.isPlaying()) {

                        JSONObject message = new JSONObject();

                        message.put("name", "onTime");

                        message.put("time", mPlayerView.getCurrentPosition() / 1000);

                        Log.d(TAG, "onTime: [time=" + mPlayerView.getCurrentPosition() / 1000 + "]");
                        eventSink.success(message);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "onTime: ", e);
                }

                onDuration();

                if (isBound) {

                    /* keep running if player view is still active */
                    handler.postDelayed(this, 1000);
                }
            }
        };

        handler.post(runnable);
    }
    @TargetApi(19)
    private void hideVirtualButtons() {
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }
    public HttpDataSource.Factory buildHttpDataSourceFactory() {
        return new DefaultHttpDataSourceFactory("shivyog");
    }
    @TargetApi(19)
    private void showVirtualButtons() {
        activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }
    private DataSource.Factory buildDataSourceFactory() {
        return new DownloadApplication(activity,context,userId).buildDataSourceFactory(userId);
    }
    private void updateMediaSource() {
        if(downloadTracker!=null) {
            hideVirtualButtons();
        }
        DownloadApplication application =new DownloadApplication(activity,context,userId);
        useExtensionRenderers = application.useExtensionRenderers();
        downloadTracker = application.getDownloadTracker(userId);
        downloadTracker.addListener(this);
        /* Produces DataSource instances through which media data is loaded. */
        DataSource.Factory dataSourceFactory = buildDataSourceFactory();

        /* This is the MediaSource representing the media to be played. */
        /*
         * Check for HLS playlist file extension ( .m3u8 or .m3u )
         * https://tools.ietf.org/html/rfc8216
         */
        if(downloadTracker.isDownloaded(Uri.parse(this.url))){

            mPlayerView.prepare(getMediaSource(Uri.parse(url)),true,false);

        }else
        if(this.url.contains(".m3u8") || this.url.contains(".m3u")) {
            MediaSource videoSource;
            videoSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(this.url));
            if(mPlayerView!=null) {
                mPlayerView.prepare(videoSource);
            }
        } else {
            MediaSource videoSource;
            videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(this.url));
            mPlayerView.prepare(videoSource);
        }


    }
    private MediaSource getMediaSource(Uri uri) {
        DownloadRequest downloadRequest;
        try{
            downloadRequest = downloadTracker
                    .getDownloadRequest(uri);
            if (downloadRequest != null) {
                Log.e("dgfhbdsfjnfd",downloadRequest.uri.toString());
                return DownloadHelper.createMediaSource(downloadRequest, buildDataSourceFactory());
            }else {
                Log.e("dgfhbdsfjnfd","dfds");
                return null;
            }
        }catch(Exception e){
            Log.e("xzfjhdsfkjnds",e.getMessage().toString());
        }
        return  null;

    }
    public void onMediaChanged(Object arguments) {

        try {

            java.util.HashMap<String, String> args = (java.util.HashMap<String, String>) arguments;

            this.url = args.get("url");

            this.title = args.get("title");

            this.subtitle = args.get("description");

            updateMediaSource();

        } catch (Exception e) { /* ignore */ }
    }
    public  void removeMedia(){
        DownloadApplication application =new DownloadApplication(activity,context,userId);
        useExtensionRenderers = application.useExtensionRenderers();
        downloadTracker = application.getDownloadTracker(userId);
        downloadTracker.addListener(this);
        ProgressDialog downloadProgressDialog;
        downloadProgressDialog = new ProgressDialog(activity);
        downloadProgressDialog.setMessage("Deleting...");
        downloadProgressDialog.setCancelable(false);
        downloadProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        downloadProgressDialog.setIndeterminate(true);
        downloadProgressDialog.show();
        downloadTracker.removeMedia(Uri.parse(url),downloadProgressDialog);
        hideVirtualButtons();
    }
    //    public  void onDownloadList(){
//        Log.e("dsfhjdsjf","dgskjhdsng");
//        try{
//            JSONObject message = new JSONObject();
//
//            message.put("download_list", downloadList);
//            eventSink.success(message);
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
//
//    }
    public void onTrackSelectionPressed() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            hideVirtualButtons();
//        }

        try {

            if( !isShowingTrackSelectionDialog
                    && TrackSelectionDialog.willHaveContent(trackSelector)) {
                isShowingTrackSelectionDialog = true;
                Log.e("testjdbfnd", "fdgfdgh");
                TrackSelectionDialog trackSelectionDialog =
                        TrackSelectionDialog.createForTrackSelector(
                                trackSelector,
                                dismissedDialog-> {
                                    isShowingTrackSelectionDialog = false;
                                    hideVirtualButtons();


                                });
                trackSelectionDialog.show(activity.getFragmentManager(), /* tag= */ null);
            }

        } catch (Exception e) { /* ignore */ }
    }
    public BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getStringExtra("status")){
                    case "download_finished":
                        try {
                            JSONObject message = new JSONObject();
                            message.put("name", "onDownloadStatus");
                            message.put("download_status", true);
                            eventSink.success(message);
                            JSONObject message1 = new JSONObject();
                            message1.put("name", "onDownloading");
                            message1.put("onDownloading", false);
                            eventSink.success(message1);
                            JSONObject message2= new JSONObject();
                            message2.put("name", "onDownloadComplete");
                            message2.put("onDownloadComplete", downloadTracker == null);
                            eventSink.success(message2);
                            Toast.makeText(context, "Download Finished", Toast.LENGTH_SHORT).show();
                            updateMediaSource();
                        } catch (JSONException e) {
                            android.util.Log.e(TAG, "onDownloadStatus: " + e.getMessage(), e);
                        }
                        break;
                    case "download_deleted":
                        try {
                            JSONObject message = new JSONObject();
                            message.put("name", "onDownloadStatus");
                            message.put("download_status", false);
                            eventSink.success(message);
                            JSONObject message1 = new JSONObject();
                            message1.put("name", "onDownloading");
                            message1.put("onDownloading", false);
                            eventSink.success(message1);
                            Toast.makeText(context, "Successfully Deleted", Toast.LENGTH_SHORT).show();
                            updateMediaSource();
                        } catch (JSONException e) {
                            android.util.Log.e(TAG, "onDownloadStatus: " + e.getMessage(), e);
                        }
                        break;
                    case "download_failed":
                        try {
                            JSONObject message = new JSONObject();
                            message.put("name", "onDownloadStatus");
                            message.put("download_status", false);
                            eventSink.success(message);
                            JSONObject message1 = new JSONObject();
                            message1.put("name", "onDownloading");
                            message1.put("onDownloading", false);
                            eventSink.success(message1);
                            Toast.makeText(context, "Download Failed!", Toast.LENGTH_SHORT).show();
                            updateMediaSource();
                        } catch (JSONException e) {
                            android.util.Log.e(TAG, "onDownloadStatus: " + e.getMessage(), e);
                        }
                        break;

                    case "downloading":
                        try {
                            JSONObject message = new JSONObject();
                            message.put("name", "onDownloading");
                            message.put("onDownloading", true);
                            eventSink.success(message);
                        } catch (JSONException e) {
                            android.util.Log.e(TAG, "onDownloadStatus: " + e.getMessage(), e);
                        }
                        break;
                }
            }
        }
    };
    public void onDownloadTrackSelectionPressed() {
        Log.e("djhfkjdsf","dsgmhnfdkjg");
        DownloadApplication application =new DownloadApplication(activity,context,userId);
        useExtensionRenderers = application.useExtensionRenderers();
        downloadTracker = application.getDownloadTracker(userId);
        downloadTracker.addListener(this);
        if(!isShowingDownloadTrackSelectionDialog) {
            isShowingDownloadTrackSelectionDialog=true;
            ProgressDialog progress = new ProgressDialog(activity);
            progress.setMessage("Getting Video Tracks...");
            progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progress.setIndeterminate(true);
            progress.show();
            RenderersFactory renderersFactory = new DownloadApplication(activity, context,userId)
                    .buildRenderersFactory(true);
            downloadTracker.toggleDownload(
                    activity,
                    progress,
                    activity.getFragmentManager(),
                    title,
                    Uri.parse(url),
                    "hls",
                    renderersFactory,
                    dialogInterface ->{    isShowingDownloadTrackSelectionDialog=false;
                        hideVirtualButtons();
                    }
            );

        }
    }
    public void onShowControlsFlagChanged(Object arguments) {

        try {

            if (arguments instanceof HashMap) {

                HashMap<String, Object> args = (HashMap<String, Object>) arguments;

                boolean sc = Boolean.parseBoolean(args.get("showControls").toString());

                setUseController(sc);
            }

        } catch (Exception e) { /* ignore */ }
    }

    /**
     * set audio language for player - language must be one of available in HLS manifest
     * currently playing
     *
     * @param arguments
     */
    public void setPreferredAudioLanguage(Object arguments) {
        try {

            java.util.HashMap<String, String> args = (java.util.HashMap<String, String>) arguments;

            String languageCode = args.get("code");

            this.preferredAudioLanguage = languageCode;

            if (mPlayerView != null && trackSelector != null && mPlayerView.isPlaying()) {

                trackSelector.setParameters(
                        trackSelector.buildUponParameters()
                                .setPreferredAudioLanguage(languageCode));
            }

        } catch (Exception e) { /* ignore */ }
    }
    public void setVideoScale(Object arguments) {
        try {
                if(Boolean.parseBoolean(arguments.toString())) {
                    Toast.makeText(activity, "Switched to fullscreen mode", Toast.LENGTH_SHORT).show();

                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                    activePlayer.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
                }else{
                    Toast.makeText(activity, "Switched to normal mode", Toast.LENGTH_SHORT).show();
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                }



        } catch (Exception e) { /* ignore */ }
    }
    public void seekTo(Object arguments) {
        try {

            java.util.HashMap<String, Double> args = (java.util.HashMap<String, Double>) arguments;

            Double pos = args.get("position");

            if (pos >= 0) {

                this.position = pos.intValue();

                if (mPlayerView != null) {

                    mPlayerView.seekTo(this.position);
                }
            }

        } catch (Exception e) { /* ignore */ }
    }

    void onDuration() {

        try {
            long newDuration = mPlayerView.getDuration();

            if (newDuration != mediaDuration && eventSink != null) {

                mediaDuration = newDuration;

                JSONObject message = new JSONObject();

                message.put("name", "onDuration");

                message.put("duration", mediaDuration);

                Log.d(TAG, "onDuration: [duration=" + mediaDuration + "]");
                eventSink.success(message);
            }

        } catch (Exception e) {
            Log.e(TAG, "onDuration: " + e.getMessage(), e);
        }
    }

    @Override
    public void onDestroy() {
        try {
            Log.e("dfgsdfghfh","Fgfd");
            // showVirtualButtons();
            isBound = false;
            mPlayerView.stop(true);

            mPlayerView.release();
            downloadTracker=null;

            doUnbindMediaNotificationManagerService();

            cleanPlayerNotification();

            activePlayer = null;
            // application.clearCache();

        } catch (Exception e) { /* ignore */ }
    }

    @Override
    public void preparePlayback() {
        activePlayer.retry();
    }

    @Override
    public void onVisibilityChange(int visibility) {


    }

    @Override
    public void onDownloadsChanged() {

    }


    /**
     * A {@link android.support.v4.media.session.MediaSessionCompat.Callback} implementation for MediaPlayer.
     */
    private final class MediaSessionCallback extends MediaSessionCompat.Callback {

        @Override
        public void onPause() {

            pause();
        }

        @Override
        public void onPlay() {
            play();


        }

        @Override
        public void onSeekTo(long pos) {
            mPlayerView.seekTo(pos);
        }

        @Override
        public void onStop() {
            pause();
        }
    }

    /**
     * Player events listener for analytics
     */
    class PlayerAnalyticsEventsListener implements AnalyticsListener {

        /* used with onSeek callback to Flutter code */
        long beforeSeek = 0;

        @Override
        public void onSeekProcessed(EventTime eventTime) {

            try {
                JSONObject message = new JSONObject();

                message.put("name", "onSeek");

                message.put("position", beforeSeek);

                message.put("offset", eventTime.currentPlaybackPositionMs / 1000);

                Log.d(TAG, "onSeek: [position=" + beforeSeek + "] [offset=" +
                        eventTime.currentPlaybackPositionMs / 1000 + "]");
                eventSink.success(message);

            } catch (Exception e) {
                Log.e(TAG, "onSeek: ", e);
            }
        }

        @Override
        public void onSeekStarted(EventTime eventTime) {

            beforeSeek = eventTime.currentPlaybackPositionMs / 1000;
        }

        @Override
        public void onPlayerError(EventTime eventTime, ExoPlaybackException error) {

            try {

                final String errorMessage = "ExoPlaybackException Type [" + error.type + "] " +
                        error.getSourceException().getCause().getMessage();

                JSONObject message = new JSONObject();

                message.put("name", "onError");

                message.put("error", errorMessage);

                Log.d(TAG, "onError: [errorMessage=" + errorMessage + "]");
                eventSink.success(message);

            } catch (Exception e) {
                Log.e(TAG, "onError: ", e);
            }
        }

        @Override
        public void onPlayerStateChanged(EventTime eventTime, boolean playWhenReady, int playbackState) {

            if (playbackState == Player.STATE_READY) {

                if (playWhenReady) {

                    try {

                        updatePlaybackState(PlayerState.PLAYING);

                        JSONObject message = new JSONObject();

                        message.put("name", "onPlay");

                        Log.d(TAG, "onPlay: []");
                        eventSink.success(message);

                    } catch (Exception e) {
                        Log.e(TAG, "onPlay: ", e);
                    }

                } else {

                    try {

                        updatePlaybackState(PlayerState.PAUSED);

                        JSONObject message = new JSONObject();

                        message.put("name", "onPause");

                        Log.d(TAG, "onPause: []");
                        eventSink.success(message);

                    } catch (Exception e) {
                        Log.e(TAG, "onPause: ", e);
                    }

                }

                onDuration();

            } else if (playbackState == Player.STATE_ENDED) {

                try {

                    updatePlaybackState(PlayerState.COMPLETE);

                    JSONObject message = new JSONObject();

                    message.put("name", "onComplete");

                    Log.d(TAG, "onComplete: []");
                    eventSink.success(message);

                } catch (Exception e) {
                    Log.e(TAG, "onComplete: ", e);
                }

            }
        }
    }
}
