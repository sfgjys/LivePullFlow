package com.myself.livepullflow.ui;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.alivc.player.AliVcMediaPlayer;
import com.alivc.player.MediaPlayer;
import com.alivc.player.NDKCallback;
import com.myself.livepullflow.R;

import java.util.List;

public class ImportUrlPlayerActivity extends AppCompatActivity {

    public static final String TAG = "ImportUrlPlayerActivity";

    private String mURL;

    // true:正在前台工作  false:后台工作   初始是在前台工作
    private boolean isCurrentRunningForeground = true;

    // 标记播放器是否已经暂停
    private boolean isPausePlayer = false;

    // 播放器是否是用户暂停的
    private boolean isPausedByUser = false;
    ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_import_url_player);

        // 获取锁来让屏幕常量
        acquireWakeLock();

        initView();
    }

    // ***************************************************************************************************************************
    private PowerManager.WakeLock mWakeLock;

    // 获取锁来让屏幕常量
    private void acquireWakeLock() {
        if (mWakeLock == null) {
            // PowerManager用来控制电源状态的.
            PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
            // PowerManager.SCREEN_BRIGHT_WAKE_LOCK在Android3.2(API 13)后被废弃，使用FLAG_KEEP_SCREEN_ON代替。持锁将保持屏幕背光为最大亮度，而键盘背光可以熄灭。按下Power键后，此锁将会被系统自动释放，释放后屏幕与CPU均关闭。
            mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "SmsSyncService.sync() wakelock.");
        }
        mWakeLock.acquire();
    }

    // 释放锁，解除屏幕常量
    private void releaseWakeLock() {
        mWakeLock.release();
        mWakeLock = null;
    }
    // -----------------------------------------------------------------------------------------------------------------------------

    // **********************************************************初始化控件************************************************************
    // 显示解码类型
    private TextView mTextViewDecoderType;
    // 显示错误内容
    private TextView mTextViewPlayerError;
    // 显示信息通知
    private TextView mTextViewInfoMessage;

    private void initView() {
        mTextViewDecoderType = (TextView) findViewById(R.id.tv_decoder_type);
        mTextViewPlayerError = (TextView) findViewById(R.id.tv_player_error);
        mTextViewInfoMessage = (TextView) findViewById(R.id.tv_info_message);

        initSurface();
    }
    // ------------------------------------------------------------------------------------------------------------------------------------

    // ******************************************************初始化SurfaceView********************************************************
    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;

    private void initSurface() {
        // 获取控件
        FrameLayout flAddSurfaceView = (FrameLayout) findViewById(R.id.fl_add_surface_view);
        // 设置背景
        flAddSurfaceView.setBackgroundColor(Color.rgb(0, 0, 0));

        // 创建SurfaceView控件
        mSurfaceView = new SurfaceView(this);

        // 为避免重复添加,事先remove子view
        flAddSurfaceView.removeAllViews();
        flAddSurfaceView.addView(mSurfaceView);

        mSurfaceView.setZOrderOnTop(false);// 设置SurfaceView不在Activity最顶层

        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder surfaceHolder) {
                // TODO ???????????????????????????
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_GPU);
                // 当surface被显示的时候是否启用或禁用屏幕保持打开状态，默认是禁用，允许屏幕关闭，启用选项有效时，可以安全的调用任何线程。
                surfaceHolder.setKeepScreenOn(true);

                // 重点:
                if (mPlayer != null) {
                    // 对于从后台切换到前台,需要重设surface;部分手机锁屏也会做前后台切换的处理  在初始化播放器的时候，已经传入了 surface，所以在释放以前的 surface 之前，是不允许再次设置新的 surface 的。也就是说请先 releaseVideoSurface 再 setVideoSurface。
                    mPlayer.setVideoSurface(mSurfaceView.getHolder().getSurface());
                } else {
                    // 创建并启动播放器
                    startToPlay();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
                Log.d(TAG, "onSurfaceChanged is valid ? " + surfaceHolder.getSurface().isValid());
                if (mPlayer != null)
                    // 在播放暂停或卡顿时，这个时候旋转手机屏幕，会发生渲染错位。为了解决这一问题，请在surfaceChanged发生时，调用此方法。如果播放界面关闭了自动旋转功能，无须调用此方法。
                    mPlayer.setSurfaceChanged();// TODO
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
                if (mPlayer != null) {
                    // 通过releaseVideoSurface 释放当前的 surface，但是一旦释放之后，就不能再次调用，否则就会出现黑屏。
                    mPlayer.releaseVideoSurface();// TODO
                }
            }
        });

        mURL = getIntent().getExtras().getString("URI", "");
    }
    // ---------------------------------------------------------------------------------------------------------------

    // *************************************************初始化播放器*****************************************************
    private AliVcMediaPlayer mPlayer;

    private void startToPlay() {

        if (mPlayer == null) {
            //  ★★★★★★★★★★★★★★★★★★初始化播放器★★★★★★★★★★★★★★★★★★★★
            // 创建player对象,并将mSurfaceView放入
            mPlayer = new AliVcMediaPlayer(this, mSurfaceView);
            // 播放器就绪事件
            mPlayer.setPreparedListener(new VideoPreparedListener());
            // 异常错误事件
            mPlayer.setErrorListener(new VideoErrorListener());
            // 信息状态监听事件
            mPlayer.setInfoListener(new VideoInfoListener());
            // seek结束事件（备注：直播无seek操作）
            mPlayer.setSeekCompleteListener(new VideoSeekCompleteListener());
            // 播放结束事件
            mPlayer.setCompletedListener(new VideoCompleteListener());
            // 画面大小变化事件
            mPlayer.setVideoSizeChangeListener(new VideoSizeChangeListener());
            // 缓冲信息更新事件
            mPlayer.setBufferingUpdateListener(new VideoBufferUpdateListener());
            // 停止事件
            mPlayer.setStopedListener(new VideoStoppedListener());

            // 解码器类型。0代表硬件解码器；1代表软件解码器。
            // 备注：默认为软件解码。由于android手机硬件适配性的问题，很多android手机的硬件解码会有问题，所以，我们建议尽量使用软件解码。
            mPlayer.setDefaultDecoder(1);

            // 重点: 在调试阶段可以使用以下方法打开native log
            mPlayer.enableNativeLog();
        }

        // 准备并开始播放
        mPlayer.prepareAndPlay(mURL);

        //播放加密视频使用如下：？？？？？？？？？？？？？？？？？？？？？
        // VidSource vidSource = new VidSource();
        // vidSource.setVid("视频id");
        // vidSource.setAcId("你的accessKeyId");
        // vidSource.setAcKey("你的accessKeySecret");
        // vidSource.setStsToken("你的STS token");
        // vidSource.setDomainRegion("你的domain");
        // vidSource.setAuthInfo(你的authinfo");
        // mPlayer.prepareAndPlayWithVid(vidSource);

        // 5秒后显示是硬解码还是软解码
        new Handler().postDelayed(new Runnable() {
            public void run() {
                mTextViewDecoderType.setText(NDKCallback.getDecoderType() == 0 ? "硬解码" : "软解码");
            }
        }, 5000);
    }

    //  `````````````````````````````````````````````````````各种监听回调对象`````````````````````````````````````````````````````

    /**
     * 准备完成监听器:调度更新进度， 当SurfaceView走完监听中的surfaceChanged方法后会来到这个监听
     */
    private class VideoPreparedListener implements MediaPlayer.MediaPlayerPreparedListener {
        @Override
        public void onPrepared() {
            Log.d(TAG, "onPrepared");
            if (mPlayer != null) {
                // 设置播放器渲染时的缩放模式，目前有两种模式，VIDEO_SCALING_MODE_SCALE_TO_FIT：等比例缩放显示，如果视频长宽比和屏幕长宽比不一致时，会存在黑边；VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING：带裁边的等比例缩放，如果视频长宽比和屏幕长宽比不一致时，会进行裁边处理以保持全屏显示。
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
            }
        }
    }

    /**
     * 错误处理监听器 当视频播放出现错误后,会发出该事件通知消息，用户需要注册该事件通知，以便在出现错误后给出相关错误提示。
     */
    private class VideoErrorListener implements MediaPlayer.MediaPlayerErrorListener {
        @SuppressLint("SetTextI18n")
        @Override
        public void onError(int what, int extra) {
            switch (what) {// 错误信息的类型.错误信息有：
                case MediaPlayer.ALIVC_ERR_UNKNOWN:
                    mTextViewPlayerError.setText("未知错误");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_LOADING_TIMEOUT://
                    mTextViewPlayerError.setText("缓冲超时");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_INPUTFILE://
                    mTextViewPlayerError.setText("未设置视频源");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_VIEW://
                    mTextViewPlayerError.setText("无效的surface");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_INVALID_INPUTFILE://
                    mTextViewPlayerError.setText("无效的视频源");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_SUPPORT_CODEC://
                    mTextViewPlayerError.setText("无支持的解码器");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_FUNCTION_DENIED://
                    mTextViewPlayerError.setText("操作无权限");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_NETWORK://
                    mTextViewPlayerError.setText("网络不可用");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_READD://
                    mTextViewPlayerError.setText("视频源访问失败");
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_ILLEGALSTATUS://
                    mTextViewPlayerError.setText("非法状态");
                    break;
                case MediaPlayer.ALIVC_ERR_NOTAUTH://
                    mTextViewPlayerError.setText("未鉴权");
                    break;
                default:
                    mTextViewPlayerError.setText("NO");
                    break;
            }
            switch (extra) {// 错误信息的额外描述
                case MediaPlayer.ALIVC_ERR_EXTRA_OPEN_FAILED://
                    mTextViewPlayerError.setText(mTextViewPlayerError.getText().toString() + ",open stream 失败");
                    break;
                case MediaPlayer.ALIVC_ERR_EXTRA_PREPARE_FAILED://
                    mTextViewPlayerError.setText(mTextViewPlayerError.getText().toString() + ",prepare失败");
                    break;
                case MediaPlayer.ALIVC_ERR_EXTRA_DEFAULT://
                    mTextViewPlayerError.setText(mTextViewPlayerError.getText().toString() + ",缺省值");
                    break;
                default:
                    mTextViewPlayerError.setText(mTextViewPlayerError.getText().toString() + ",NO");
                    break;
            }
        }
    }

    /**
     * 信息通知监听器:重点是缓存开始/结束  当视频开始播放，用户需要知道视频的相关信息，可以注册该事件。
     */
    private class VideoInfoListener implements MediaPlayer.MediaPlayerInfoListener {
        @Override
        public void onInfo(int what, int extra) {
            switch (what) {
                case MediaPlayer.MEDIA_INFO_UNKNOW:
                    mTextViewInfoMessage.setText("未知");
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    mTextViewInfoMessage.setText("开始缓冲");
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    mTextViewInfoMessage.setText("结束缓冲");
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    mTextViewInfoMessage.setText("首帧显示时间");
                    if (mPlayer != null)
                        Log.d(TAG, "首帧显示时间 : " + ((long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_1st_VFRAME_SHOW_TIME, -1) - (long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_OPEN_STREAM_TIME, -1)));
                    break;
                case MediaPlayer.MEDIA_INFO_TRACKING_LAGGING:
                    mTextViewInfoMessage.setText("跟踪滞后");
                    break;
                case MediaPlayer.MEDIA_INFO_NETWORK_ERROR:
                    mTextViewInfoMessage.setText("网络异常");
                    break;
            }
        }
    }

    /**
     * 当视频进行seek跳转后，会发出该事件通知消息，用户注册该事件通知后，能收到跳转完成通知。 点播功能时用到
     */
    private class VideoSeekCompleteListener implements MediaPlayer.MediaPlayerSeekCompleteListener {
        @Override
        public void onSeekCompleted() {

        }
    }

    /**
     * 当视频播放完成后，会发出该事件通知消息，用户需要注册该事件，在播放完成后完成相关清理工作。  点播功能时用到
     */
    private class VideoCompleteListener implements MediaPlayer.MediaPlayerCompletedListener {
        @Override
        public void onCompleted() {
            // 点播播放结束后弹出 退出 对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(ImportUrlPlayerActivity.this);
            builder.setMessage("播放结束");
            builder.setTitle("提示");
            builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    ImportUrlPlayerActivity.this.finish();
                }
            });
            builder.create().show();
        }
    }

    /**
     * 当视频播放时视频大小(长宽)改变后，会发出该事件通知。
     */
    private class VideoSizeChangeListener implements MediaPlayer.MediaPlayerVideoSizeChangeListener {
        @Override
        public void onVideoSizeChange(int width, int height) {
            Log.d(TAG, "onVideoSizeChange width = " + width + " height = " + height);
        }
    }

    /**
     * 当网络下载速度较慢来不及播放时，会发送下载缓冲进度通知
     */
    private class VideoBufferUpdateListener implements MediaPlayer.MediaPlayerBufferingUpdateListener {
        // percent 为 0~100之间的数字
        @Override
        public void onBufferingUpdateListener(int percent) {
            System.out.println("缓存：" + percent);
        }
    }

    // 标记播放器是否已经停止
    private boolean isStopPlayer = false;

    /**
     * 视频停止监听器
     */
    private class VideoStoppedListener implements MediaPlayer.MediaPlayerStopedListener {
        @Override
        public void onStopped() {
            Log.d(TAG, "onVideoStopped.");
            isStopPlayer = true;
        }
    }

    // -------------------------------------------------------------------------------------------------------------------------------------


    // 这里是彻底停止播放器，在完全退出播放界面时可用
    private void stop() {
        Log.d(TAG, "AudioRender: stop play");
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer.destroy();
            mPlayer = null;
        }
    }

    // 重点:判定是否在前台工作
    public boolean isRunningForeground() {
        ActivityManager activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcessInfos = activityManager.getRunningAppProcesses();
        // 枚举进程
        for (ActivityManager.RunningAppProcessInfo appProcessInfo : appProcessInfos) {
            if (appProcessInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                if (appProcessInfo.processName.equals(this.getApplicationInfo().processName)) {
                    Log.d(TAG, "EntryActivity isRunningForeGround");
                    return true;
                }
            }
        }
        Log.d(TAG, "EntryActivity isRunningBackGround");
        return false;
    }

    @Override
    protected void onStart() {
        Log.e(TAG, "onStart.");
        super.onStart();
        if (!isCurrentRunningForeground) {
            Log.d(TAG, "是从后台工作切换倒前台工作");
        }
    }

    @Override
    protected void onResume() {
        Log.e(TAG, "onResume");
        super.onResume();

        // 重点:如果播放器是从锁屏/后台切换到前台,那么调用player.stat
        if (mPlayer != null && !isStopPlayer && isPausePlayer) {
            if (!isPausedByUser) {
                isPausePlayer = false;
                mPlayer.play();
            }
        }
    }


    @Override
    protected void onPause() {
        Log.e(TAG, "onPause." + isStopPlayer + " " + isPausePlayer + " " + (mPlayer == null));
        super.onPause();
        // 重点:播放器没有停止,也没有暂停的时候,在activity的pause的时候也需要pause
        if (!isStopPlayer && !isPausePlayer && mPlayer != null) {
            Log.e(TAG, "onPause mpayer.");
            mPlayer.pause();
            isPausePlayer = true;
        }
    }

    @Override
    protected void onStop() {
        Log.e(TAG, "onStop.");
        super.onStop();
        isCurrentRunningForeground = isRunningForeground();
        if (!isCurrentRunningForeground) {
            Log.d(TAG, "从前台工作变为后台工作");
        }
    }

    @Override
    protected void onDestroy() {
        Log.e(TAG, "AudioRender: onDestroy.");

        releaseWakeLock();

//        // 解除注册的网络状态变化监听广播
//        if (connectionReceiver != null) {
//            unregisterReceiver(connectionReceiver);
//        }

        // 重点:在 activity destroy的时候,要停止播放器并释放播放器
        if (mPlayer != null) {
            stop();
        }

        super.onDestroy();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            isStopPlayer = true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
