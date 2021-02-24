/*
 * Copyright (C) 2017 The Android Open Source Project
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


import android.app.Notification;
import android.content.Context;
import android.util.Log;

import com.google.android.exoplayer2.offline.Download;
import com.google.android.exoplayer2.offline.DownloadManager;
import com.google.android.exoplayer2.scheduler.PlatformScheduler;
import com.google.android.exoplayer2.ui.DownloadNotificationHelper;
import com.google.android.exoplayer2.util.NotificationUtil;
import com.google.android.exoplayer2.util.Util;

import java.util.ArrayList;
import java.util.List;

import com.flutter_zplayer.R;
import com.flutter_zplayer.TinyDB;

import static com.flutter_zplayer.video.DownloadApplication.DOWNLOAD_NOTIFICATION_CHANNEL_ID;

/** A service for downloading media. */
public class DownldService extends com.google.android.exoplayer2.offline.DownloadService {

  private static final int JOB_ID = 1;
  private static final int FOREGROUND_NOTIFICATION_ID = 1;

  public DownldService() {
    super(
        FOREGROUND_NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        DOWNLOAD_NOTIFICATION_CHANNEL_ID,
        R.string.exo_download_notification_channel_name,
        /* channelDescriptionResourceId= */ 0);
  }

  @Override
  protected DownloadManager getDownloadManager() {
    // This will only happen once, because getDownloadManager is guaranteed to be called only once
    // in the life cycle of the process.
    DownloadApplication application =new DownloadApplication(this);
    DownloadManager downloadManager = application.getDownloadManager();
    DownloadNotificationHelper downloadNotificationHelper =
        application.getDownloadNotificationHelper();
    downloadManager.addListener(
        new TerminalStateNotificationHelper(
            this, downloadNotificationHelper, FOREGROUND_NOTIFICATION_ID + 1));
    return downloadManager;
  }

  @Override
  protected PlatformScheduler getScheduler() {
    return Util.SDK_INT >= 21 ? new PlatformScheduler(this, JOB_ID) : null;
  }

  @Override
  protected Notification getForegroundNotification(List<Download> downloads) {
    return (new DownloadApplication(this))
        .getDownloadNotificationHelper()
        .buildProgressNotification(
            R.drawable.ic_download, /* contentIntent= */ null, /* message= */ null, downloads);
  }

  /**
   * Creates and displays notifications for downloads when they complete or fail.
   *
   * <p>This helper will outlive the lifespan of a single instance of {@link DownldService}.
   * It is static to avoid leaking the first {@link DownldService} instance.
   */
  private static final class TerminalStateNotificationHelper implements DownloadManager.Listener {

    private final Context context;
    private final DownloadNotificationHelper notificationHelper;

    private int nextNotificationId;

    public TerminalStateNotificationHelper(
        Context context, DownloadNotificationHelper notificationHelper, int firstNotificationId) {
      this.context = context.getApplicationContext();
      this.notificationHelper = notificationHelper;
      nextNotificationId = firstNotificationId;
    }

    @Override
    public void onDownloadChanged(DownloadManager manager, Download download) {
      Notification notification;
      TinyDB tinyDB=new TinyDB(context);
      String uid=tinyDB.getString("userId");
      Log.e("sadKFJHsdaf ",uid);
      if (download.state == Download.STATE_COMPLETED) {
        ArrayList<String> data=tinyDB.getListString("urls");
        if(data!=null&&data.size()!=0&&data.contains(download.request.uri.toString()+"?status=downloading&"+uid)) {
          if (data.remove(download.request.uri.toString()+"?status=downloading&"+uid)) {
            tinyDB.putListString("urls", data);
          }
        }
        if(data!=null&&data.size()!=0&&data.contains(download.request.uri.toString()+"?status=completed&"+uid)) {
          if (data.remove(download.request.uri.toString()+"?status=completed&"+uid)) {
            tinyDB.putListString("urls", data);
          }
        }
        ArrayList<String> urlList = tinyDB.getListString("urls");
        urlList.add(download.request.uri.toString()+"?status=completed&"+uid);
        tinyDB.putListString("urls", urlList);
        notification =
            notificationHelper.buildDownloadCompletedNotification(
                R.drawable.ic_download_done,
                /* contentIntent= */ null,
                Util.fromUtf8Bytes(download.request.data));
        NotificationUtil.setNotification(context, nextNotificationId++, notification);
      }
      else if(download.state == Download.STATE_DOWNLOADING){
          ArrayList<String> data=tinyDB.getListString("urls");
        if(data!=null&&data.size()!=0&&data.contains(download.request.uri.toString()+"?status=completed&"+uid)) {
          if (data.remove(download.request.uri.toString()+"?status=completed&"+uid)) {
            tinyDB.putListString("urls", data);
          }
        }
          if(data!=null&&data.size()!=0&&data.contains(download.request.uri.toString()+"?status=downloading&"+uid)) {
            if (data.remove(download.request.uri.toString()+"?status=downloading&"+uid)) {
              tinyDB.putListString("urls", data);
            }
        }
        ArrayList<String> urlList = tinyDB.getListString("urls");
        urlList.add(download.request.uri.toString()+"?status=downloading&"+uid);
        tinyDB.putListString("urls", urlList);

      }
      else if (download.state == Download.STATE_FAILED) {

        ArrayList<String> data = tinyDB.getListString("urls");
        if (data != null && data.size() != 0 && data.contains(download.request.uri.toString() + "?status=downloading&" + uid)) {
          if (data.remove(download.request.uri.toString() + "?status=downloading&" + uid)) {
            tinyDB.putListString("urls", data);
          }
        }
          if (data != null && data.size() != 0 && data.contains(download.request.uri.toString() + "?status=failed&" + uid)) {
            if (data.remove(download.request.uri.toString() + "?status=failed&" + uid)) {
              tinyDB.putListString("urls", data);
            }
          }
          ArrayList<String> urlList = tinyDB.getListString("urls");
          urlList.add(download.request.uri.toString() + "?status=failed&" + uid);
          tinyDB.putListString("urls", urlList);
          notification =
                  notificationHelper.buildDownloadCompletedNotification(
                          R.drawable.ic_download_done,
                          /* contentIntent= */ null,
                          Util.fromUtf8Bytes(download.request.data));
          NotificationUtil.setNotification(context, nextNotificationId++, notification);



      }
      else if (download.state == Download.STATE_STOPPED) {
        ArrayList<String> data=tinyDB.getListString("urls");
        if(data!=null&&data.size()!=0&&data.contains(download.request.uri.toString()+"?status=downloading&"+uid)) {
          if (data.remove(download.request.uri.toString()+"?status=downloading&"+uid)) {
            tinyDB.putListString("urls", data);
          }
        }

      }
      else if (download.state == Download.STATE_RESTARTING) {
        ArrayList<String> data=tinyDB.getListString("urls");
        if(data!=null&&data.size()!=0&&data.contains(download.request.uri.toString()+"?status=downloading&"+uid)) {
          if (data.remove(download.request.uri.toString()+"?status=downloading&"+uid)) {
            tinyDB.putListString("urls", data);
          }
        }
        TinyDB tinydb = new TinyDB(context);
        ArrayList<String> urlList = tinydb.getListString("urls");
        urlList.add(download.request.uri.toString());
        tinydb.putListString("urls", urlList);

      }
      else {
        return;
      }
    }
  }
}
