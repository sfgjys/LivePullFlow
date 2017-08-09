package com.myself.livepullflow.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Handler;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.alivc.player.AliVcMediaPlayer;
import com.alivc.player.MediaPlayer;
import com.alivc.player.NDKCallback;
import com.myself.livepullflow.R;

public class ImportUrlPlayerActivity extends AppCompatActivity {

    private String mURL;

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
    //  显示解码类型
    private TextView mTextViewDecoderType;

    private void initView() {
        mTextViewDecoderType = (TextView) findViewById(R.id.tv_decoder_type);


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

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);

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

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

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
            mPlayer.setInfoListener(new VideoInfolistener());
            // seek结束事件（备注：直播无seek操作）
            mPlayer.setSeekCompleteListener(new VideoSeekCompletelistener());
            // 播放结束事件
            mPlayer.setCompletedListener(new VideoCompletelistener());
            // 画面大小变化事件
            mPlayer.setVideoSizeChangeListener(new VideoSizeChangelistener());
            // 缓冲信息更新事件
            mPlayer.setBufferingUpdateListener(new VideoBufferUpdatelistener());
            // 停止事件
            mPlayer.setStopedListener(new VideoStoppedListener());

            // 解码器类型。0代表硬件解码器；1代表软件解码器。
            // 备注：默认为软件解码。由于android手机硬件适配性的问题，很多android手机的硬件解码会有问题，所以，我们建议尽量使用软件解码。
            mPlayer.setDefaultDecoder(1);

            // 重点: 在调试阶段可以使用以下方法打开native log
            mPlayer.enableNativeLog();
        }

        // 准备开始播放
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
                // 设置播放器渲染时的缩放模式，目前有两种模式，VIDEO_ SCALING_ MODE_ SCALE_ TO_ FIT：等比例缩放显示，如果视频长宽比和屏幕长宽比不一致时，会存在黑边；VIDEO_ SCALING_ MODE_ SCALE_ TO_ FIT_ WITH_ CROPPING：带裁边的等比例缩放，如果视频长宽比和屏幕长宽比不一致时，会进行裁边处理以保持全屏显示。
                mPlayer.setVideoScalingMode(MediaPlayer.VideoScalingMode.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                // 根据视频时长设置UI
                update_total_duration(mPlayer.getDuration());// getDuration 获取视频时长，单位为毫秒。
                mTimerHandler.postDelayed(mRunnable, 1000);//  开启内循环进行更新进度条进度
                // 展示进度条
                show_progress_ui(true);
                mTimerHandler.postDelayed(mUIRunnable, 3000);// mUIRunnable的run方法内容没有用处
            }
        }
    }


    /**
     * 错误处理监听器
     */
    private class VideoErrorListener implements MediaPlayer.MediaPlayerErrorListener {

        public void onError(int what, int extra) {
            int errCode;

            if (mPlayer == null) {
                return;
            }

            errCode = mPlayer.getErrorCode();
            switch (errCode) {
                case MediaPlayer.ALIVC_ERR_LOADING_TIMEOUT:
                    report_error("缓冲超时,请确认网络连接正常后重试", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_INPUTFILE:
                    report_error("no input file", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_VIEW:
                    report_error("no surface", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_INVALID_INPUTFILE:
                    report_error("视频资源或者网络不可用", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_SUPPORT_CODEC:
                    report_error("no codec", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_FUNCTION_DENIED:
                    report_error("no priority", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_UNKNOWN:
                    report_error("unknown error", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_NO_NETWORK:
                    report_error("视频资源或者网络不可用", true);
                    mPlayer.reset();
                    break;
                case MediaPlayer.ALIVC_ERR_ILLEGALSTATUS:
                    report_error("illegal call", true);
                    break;
                case MediaPlayer.ALIVC_ERR_NOTAUTH:
                    report_error("auth failed", true);
                    break;
                case MediaPlayer.ALIVC_ERR_READD:
                    report_error("资源访问失败,请重试", true);
                    mPlayer.reset();
                    break;
                default:
                    break;

            }
        }
    }

    /**
     * 信息通知监听器:重点是缓存开始/结束
     */
    private class VideoInfolistener implements MediaPlayer.MediaPlayerInfoListener {

        public void onInfo(int what, int extra) {
            Log.d(TAG, "onInfo what = " + what + " extra = " + extra);
            System.out.println();
            switch (what) {
                case MediaPlayer.MEDIA_INFO_UNKNOW:
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                    //pause();
                    show_buffering_ui(true);
                    break;
                case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                    //start();
                    show_buffering_ui(false);
                    break;
                case MediaPlayer.MEDIA_INFO_TRACKING_LAGGING:
                    break;
                case MediaPlayer.MEDIA_INFO_NETWORK_ERROR:
                    report_error("�������!", true);
                    break;
                case MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START:
                    if (mPlayer != null)
                        Log.d(TAG, "on Info first render start : " + ((long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_1st_VFRAME_SHOW_TIME, -1) - (long) mPlayer.getPropertyDouble(AliVcMediaPlayer.FFP_PROP_DOUBLE_OPEN_STREAM_TIME, -1)));
                    break;
            }
        }
    }

    /**
     * 快进完成监听器
     */
    private class VideoSeekCompletelistener implements MediaPlayer.MediaPlayerSeekCompleteListener {

        public void onSeekCompleted() {
            mEnableUpdateProgress = true;
        }
    }

    /**
     * 视频播完监听器
     */
    private class VideoCompletelistener implements MediaPlayer.MediaPlayerCompletedListener {

        public void onCompleted() {
            Log.d(TAG, "onCompleted.");

            AlertDialog.Builder builder = new AlertDialog.Builder(PlayerActivity.this);
            builder.setMessage("播放结束");

            builder.setTitle("提示");


            builder.setNegativeButton("退出", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    PlayerActivity.this.finish();
                }
            });

            builder.create().show();
        }
    }

    /**
     * 视频大小变化监听器
     */
    private class VideoSizeChangelistener implements MediaPlayer.MediaPlayerVideoSizeChangeListener {

        public void onVideoSizeChange(int width, int height) {
            Log.d(TAG, "onVideoSizeChange width = " + width + " height = " + height);
        }
    }

    /**
     * 视频缓存变化监听器: percent 为 0~100之间的数字】
     */
    private class VideoBufferUpdatelistener implements MediaPlayer.MediaPlayerBufferingUpdateListener {

        public void onBufferingUpdateListener(int percent) {

        }
    }

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

}
