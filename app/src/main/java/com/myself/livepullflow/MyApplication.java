package com.myself.livepullflow;

import android.app.Application;

import com.alivc.player.AccessKey;
import com.alivc.player.AccessKeyCallback;
import com.alivc.player.AliVcMediaPlayer;

/**
 * Created by Administrator on 2017/8/9.
 */

public class MyApplication extends Application {

    // 访问Key的ID
    String accessKeyId = "QxJIheGFRL926hFX";
    // 访问Key的密钥
    String accessKeySecret = "hipHJKpt0TdznQG2J4D0EVSavRH7mR";

    @Override
    public void onCreate() {
        super.onCreate();

        // 初始化播放器
        // 参数二 ：业务ID，用户自行设置，用于标识使用播放器sdk的APP。如“淘宝直播”就设置“TaobaoLive”。
        AliVcMediaPlayer.init(getApplicationContext(), "kuku_zhibo", new AccessKeyCallback() {
            public AccessKey getAccessToken() {
                return new AccessKey(accessKeyId, accessKeySecret);
            }
        });
    }
}
