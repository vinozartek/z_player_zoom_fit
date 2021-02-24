package com.flutter_zplayer.video;

import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import com.flutter_zplayer.TinyDB;

public class DownloadsPlayerControl  {
    PluginRegistry.Registrar regitrar;
    private static final String CHANNEL = "flutter.native/exoVideoDownloader";
    public DownloadsPlayerControl(PluginRegistry.Registrar regitrar) {
        this.regitrar = regitrar;
    }
    public  void registerChannel(){
        new MethodChannel(regitrar.messenger(), CHANNEL).setMethodCallHandler(
                new MethodChannel.MethodCallHandler() {
                    @Override
                    public void onMethodCall(MethodCall call, MethodChannel.Result result) {

                        if (call.method.equals("dataList")) {
                            result.success(onDownloadList());
                        }
                        else if (call.method.equals("removeMedia")) {
                            result.success(removeMediaItem(call.argument("url").toString(),"ghyf"));
                        }

                    }});
    }
    private ArrayList<String>  onDownloadList(){
        TinyDB tinyDB=new TinyDB(regitrar.activity());
        ArrayList<String> downloadList=tinyDB.getListString("urls");
        Log.e("dsfhjdsjf","dgskjhdsng");
            return downloadList;

    }
    private boolean removeMediaItem(String url,String userId){
        DownloadApplication application =new DownloadApplication(regitrar.activity(),regitrar.context(),userId);
        DownloadTracker downloadTracker = application.getDownloadTracker("jhdsgf");
        downloadTracker.removeMediaUrl(Uri.parse(url));
       Log.e("jdfhjdsf ",url);
            return true;
    }
}
