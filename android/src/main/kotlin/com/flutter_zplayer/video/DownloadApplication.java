/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.flutter_zplayer.video;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import com.google.android.exoplayer2.BuildConfig;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.database.DatabaseProvider;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.offline.ActionFileUpgradeUtil;
import com.google.android.exoplayer2.offline.DefaultDownloadIndex;
import com.google.android.exoplayer2.offline.DefaultDownloaderFactory;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.offline.DownloaderConstructorHelper;
import com.google.android.exoplayer2.ui.DownloadNotificationHelper;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.io.File;
import java.io.IOException;
import java.util.function.Function;

import com.flutter_zplayer.TinyDB;

/**
 * Placeholder application to facilitate overriding Application methods for debugging and testing.
 */
public class DownloadApplication extends Application {
  Activity activity;
  private static SimpleCache sDownloadCache;
  Context context;
  String userId;
  Function downloadStatusCallBack;
  DownloadApplication(Activity activity, Context context,String userId){
    this.activity=activity;
    this.context=context;
    this.userId=userId;
  }
  public  SimpleCache getInstance(Context context,String userId) {
    if (sDownloadCache == null) {
      Log.e("dsjkhnfds","ldkfjnkds");
      TinyDB tinyDB=new TinyDB(context);
      String uid=tinyDB.getString("userId");
      sDownloadCache = new SimpleCache(new File(getDownloadDirectory(), DOWNLOAD_CONTENT_DIRECTORY + "/" + uid), new NoOpCacheEvictor(), new ExoDatabaseProvider(context));
    }
      return sDownloadCache;
  }
  public void clearCache(){
    //sDownloadCache.release();
    sDownloadCache=null;
  }
  DownloadApplication(Context context){
    this.context=context;
    this.userId=userId;
  }
  public static final String DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel";

  private static final String TAG = "DownloadApplication";
  private static final String DOWNLOAD_ACTION_FILE = "actions";
  private static final String DOWNLOAD_TRACKER_ACTION_FILE = "tracked_actions";
  private static final String DOWNLOAD_CONTENT_DIRECTORY = "downloads";

  protected String userAgent;

  private DatabaseProvider databaseProvider;
  private File downloadDirectory;
  private Cache downloadCache;
  private DownloadManager downloadManager;
  private DownloadTracker downloadTracker;
  private DownloadNotificationHelper downloadNotificationHelper;

  @Override
  public void onCreate() {
    super.onCreate();
    userAgent = Util.getUserAgent(context, "ExoPlayerDemo");
  }

  /** Returns a {@link DataSource.Factory}. */
  public DataSource.Factory buildDataSourceFactory(String userId) {
    DefaultDataSourceFactory upstreamFactory =
            new DefaultDataSourceFactory(context, buildHttpDataSourceFactory());
    return buildReadOnlyCacheDataSource(upstreamFactory, getDownloadCache(userId));
  }

  /** Returns a {@link HttpDataSource.Factory}. */
  public HttpDataSource.Factory buildHttpDataSourceFactory() {
    return new DefaultHttpDataSourceFactory("shivyog");
  }

  /** Returns whether extension renderers should be used. */
  public boolean useExtensionRenderers() {
    return "withExtensions".equals(BuildConfig.FLAVOR);
  }

  public RenderersFactory buildRenderersFactory(boolean preferExtensionRenderer) {
    @DefaultRenderersFactory.ExtensionRendererMode
    int extensionRendererMode =
            useExtensionRenderers()
                    ? (preferExtensionRenderer
                    ? DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    : DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF;
    return new DefaultRenderersFactory(/* context= */ context)
            .setExtensionRendererMode(extensionRendererMode);
  }

  public DownloadNotificationHelper getDownloadNotificationHelper() {
    if (downloadNotificationHelper == null) {
      downloadNotificationHelper =
              new DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID);
    }
    return downloadNotificationHelper;
  }

  public DownloadManager getDownloadManager() {
    initDownloadManager(userId);
    return downloadManager;
  }

  public DownloadTracker getDownloadTracker(String userId) {
    initDownloadManager(userId);
    return downloadTracker;
  }

  protected synchronized Cache getDownloadCache(String user) {
    return getInstance(context,user);
  }

  private synchronized void initDownloadManager(String userId) {
    if (downloadManager == null) {
      TinyDB tinyDB=new TinyDB(context);
      userId=tinyDB.getString("userId");
      Log.e("jhndsjfds",userId);
      DefaultDownloadIndex downloadIndex = new DefaultDownloadIndex(getDatabaseProvider());
      upgradeActionFile(
              DOWNLOAD_ACTION_FILE, downloadIndex, /* addNewDownloadsAsCompleted= */ false,userId);
      upgradeActionFile(
              DOWNLOAD_TRACKER_ACTION_FILE, downloadIndex, /* addNewDownloadsAsCompleted= */ true,userId);
      DownloaderConstructorHelper downloaderConstructorHelper =
              new DownloaderConstructorHelper(getDownloadCache(userId), buildHttpDataSourceFactory());
      downloadManager =
              new DownloadManager(
                      context, downloadIndex, new DefaultDownloaderFactory(downloaderConstructorHelper));
      downloadTracker =
              new DownloadTracker(/* context= */ context, buildDataSourceFactory(userId), downloadManager,activity);
    }
  }

  private void upgradeActionFile(
          String fileName, DefaultDownloadIndex downloadIndex, boolean addNewDownloadsAsCompleted,String userId) {
    try {
      ActionFileUpgradeUtil.upgradeAndDelete(
              new File(getDownloadDirectory(), fileName),
              /* downloadIdProvider= */ null,
              downloadIndex,
              /* deleteOnFailure= */ true,
              addNewDownloadsAsCompleted);
    } catch (IOException e) {
      Log.e(TAG, "Failed to upgrade action file: " + fileName, e);
    }
  }

  private DatabaseProvider getDatabaseProvider() {
    if (databaseProvider == null) {
      databaseProvider = new ExoDatabaseProvider(context);
    }
    return databaseProvider;
  }

  private File getDownloadDirectory() {
    if (downloadDirectory == null) {
      downloadDirectory = context.getExternalFilesDir(null);
      if (downloadDirectory == null) {
        downloadDirectory = getFilesDir();
      }
    }
    return downloadDirectory;
  }

  protected static CacheDataSourceFactory buildReadOnlyCacheDataSource(
          DataSource.Factory upstreamFactory, Cache cache) {
    return new CacheDataSourceFactory(
            cache,
            upstreamFactory,
            new FileDataSource.Factory(),
            /* cacheWriteDataSinkFactory= */ null,
            CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
            /* eventListener= */ null);
  }
}
