package fm.jiecao.jcvideoplayer_lib.tencent;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer.extractor.mp4.FragmentedMp4Extractor;
import com.google.android.exoplayer.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer.extractor.ts.AdtsExtractor;
import com.google.android.exoplayer.extractor.ts.TsExtractor;
import com.google.android.exoplayer.extractor.webm.WebmExtractor;
import com.google.android.exoplayer.util.Util;
import com.tencent.qcload.playersdk.player.ExtractorRendererBuilder;
import com.tencent.qcload.playersdk.player.HlsRendererBuilder;
import com.tencent.qcload.playersdk.player.TencentExoPlayer;
import com.tencent.qcload.playersdk.util.VideoInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import fm.jiecao.jcvideoplayer_lib.*;


/**
 * Created by Max on 16/5/31.
 */
public class DJVideoPlayer extends FrameLayout implements View.OnClickListener, View.OnTouchListener, SeekBar.OnSeekBarChangeListener, DJMediaManager.JCMediaPlayerListener, TextureView.SurfaceTextureListener {

    public static final String TAG = "DJVideoPlayer";

    protected int mCurrentState = -1;//-1相当于null
    protected static final int CURRENT_STATE_NORMAL = 0;
    protected static final int CURRENT_STATE_PREPAREING = 1;
    protected static final int CURRENT_STATE_PLAYING = 2;
    protected static final int CURRENT_STATE_PLAYING_BUFFERING_START = 3;
    protected static final int CURRENT_STATE_PAUSE = 5;
    protected static final int CURRENT_STATE_AUTO_COMPLETE = 6;
    protected static final int CURRENT_STATE_ERROR = 7;
    protected static int BACKUP_PLAYING_BUFFERING_STATE = -1;

    protected boolean mTouchingProgressBar = false;
    protected boolean mIfCurrentIsFullscreen = false;
    protected boolean mIfFullscreenIsDirectly = false;//mIfCurrentIsFullscreen should be true first
    protected static boolean IF_FULLSCREEN_FROM_NORMAL = false;//to prevent infinite looping
    public static boolean IF_RELEASE_WHEN_ON_PAUSE = true;
    protected static long CLICK_QUIT_FULLSCREEN_TIME = 0;
    public static final int FULL_SCREEN_NORMAL_DELAY = 2000;

    public ImageView startButton;
    public SeekBar progressBar;
    public ImageView fullscreenButton;
    public TextView currentTimeTextView, totalTimeTextView;
    public ViewGroup textureViewContainer;
    public ViewGroup topContainer, bottomContainer;
    public JCResizeTextureView textureView;
    public Surface mSurface;

    protected String mUrl;
    protected Object[] mObjects;
    protected Map<String, String> mMapHeadData = new HashMap<>();
    protected boolean mLooping = false;

    protected static Timer UPDATE_PROGRESS_TIMER;
    protected ProgressTimerTask mProgressTimerTask;

    protected static JCBuriedPoint JC_BURIED_POINT;
    protected int mScreenWidth;
    protected int mScreenHeight;
    protected AudioManager mAudioManager;

    protected int mThreshold = 80;
    protected float mDownX;
    protected float mDownY;
    protected boolean mChangeVolume = false;
    protected boolean mChangePosition = false;
    protected long mDownPosition;
    protected int mGestureDownVolume;

    protected long mSeekTimePosition;//change postion when finger up

    public static boolean WIFI_TIP_DIALOG_SHOWED = false;

    protected Handler mHandler = new Handler();

    private boolean MEMORY_PLAYER_IS_PLAYING = false;

    private long MEMORY_POSITION = -1;

    public ImageView mBottomStart;
    public ImageView mBackButton;
    public ProgressBar mBottomProgressBar, mLoadingProgressBar;
    public TextView mTitleTextView;
    public ImageView mThumbImageView;
    public ImageView mCoverImageView;

    public boolean DEBUG = true;

    private VideoStateChangeListener mVideoStateChangeListener;

    protected static Timer DISSMISS_CONTROL_VIEW_TIMER;
    protected static JCBuriedPointStandard JC_BURIED_POINT_STANDARD;

    private Context mContext;

    private VideoInfo.VideoType contentType;

    private AudioCapabilities audioCapabilities;

    public DJVideoPlayer(Context context) {
        super(context);
        mContext = context;
        init(context);
    }

    public DJVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        init(context);
    }

    public void setCurrentState(int mCurrentState) {
        this.mCurrentState = mCurrentState;
    }

    public int getLayoutId() {
        return R.layout.jc_layout_standard;
    }

    protected void init(Context context) {
        View.inflate(context, getLayoutId(), this);
        startButton = (ImageView) findViewById(R.id.start);
        fullscreenButton = (ImageView) findViewById(R.id.fullscreen);
        progressBar = (SeekBar) findViewById(R.id.progress);
        currentTimeTextView = (TextView) findViewById(R.id.current);
        totalTimeTextView = (TextView) findViewById(R.id.total);
        bottomContainer = (ViewGroup) findViewById(R.id.layout_bottom);
        textureViewContainer = (RelativeLayout) findViewById(R.id.surface_container);
        topContainer = (ViewGroup) findViewById(R.id.layout_top);

        startButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        progressBar.setOnSeekBarChangeListener(this);
        bottomContainer.setOnClickListener(this);
        textureViewContainer.setOnClickListener(this);
        progressBar.setOnTouchListener(this);

        textureViewContainer.setOnTouchListener(this);
        mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);


        mBottomStart = (ImageView) findViewById(R.id.start);
        mBottomProgressBar = (ProgressBar) findViewById(R.id.bottom_progressbar);
        mTitleTextView = (TextView) findViewById(R.id.title);
        mBackButton = (ImageView) findViewById(R.id.back);
        mThumbImageView = (ImageView) findViewById(R.id.thumb);
        mCoverImageView = (ImageView) findViewById(R.id.cover);
        mLoadingProgressBar = (ProgressBar) findViewById(R.id.loading);

        mBackButton.setOnClickListener(this);
        mBottomStart.setOnClickListener(this);
        mThumbImageView.setOnClickListener(this);
    }

    protected static void setJcBuriedPoint(JCBuriedPoint jcBuriedPoint) {
        JC_BURIED_POINT = jcBuriedPoint;
    }

    public boolean setUp(String url, Object... objects) {
        return this.setUp(url, VideoInfo.VideoType.HLS, objects);
    }

    public boolean setUp(String url, VideoInfo.VideoType videoType, Object... objects) {
        if (DJMediaManager.instance().listener == this &&
                (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) < FULL_SCREEN_NORMAL_DELAY)
            return false;
        if (mIfCurrentIsFullscreen) {
//            fullscreenButton.setImageResource(R.drawable.shrink_video);
        } else {
//            fullscreenButton.setImageResource(R.drawable.enlarge_video);
            mBackButton.setVisibility(GONE);
        }
        mCurrentState = CURRENT_STATE_NORMAL;
        this.mUrl = url;
        this.mObjects = objects;
        this.contentType = videoType;
        setStateAndUi(CURRENT_STATE_NORMAL);
        return true;
    }

    public void setLoop(boolean looping) {
        this.mLooping = looping;
    }

    protected void setStateAndUi(int state) {
        if (DEBUG) Log.i(TAG, "setStateAndUI, [" + this + "]");

        mCurrentState = state;
        switch (mCurrentState) {
            case CURRENT_STATE_NORMAL:
                if (DJMediaManager.instance().listener == this) {
                    cancelProgressTimer();
                    DJMediaManager.instance().releaseMediaPlayer();
                }
                changeUiToNormal();
                break;
            case CURRENT_STATE_PREPAREING:
                resetProgressAndTime();
                changeUiToShowUiPrepareing();
                startDismissControlViewTimer();
                break;
            case CURRENT_STATE_PLAYING:
                startProgressTimer();
                if (mVideoStateChangeListener != null) mVideoStateChangeListener.onPlaying();
                changeUiToShowUiPlaying();
                startDismissControlViewTimer();
                break;
            case CURRENT_STATE_PAUSE:
                startProgressTimer();
                if (mVideoStateChangeListener != null) mVideoStateChangeListener.onPause();
                changeUiToShowUiPause();
                cancelDismissControlViewTimer();
                break;
            case CURRENT_STATE_ERROR:
                if (DJMediaManager.instance().listener == this) {
                    DJMediaManager.instance().releaseMediaPlayer();
                }
                changeUiToError();
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                cancelProgressTimer();
                progressBar.setProgress(100);
                currentTimeTextView.setText(totalTimeTextView.getText());
                changeUiToNormal();
                break;
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    break;
                case MotionEvent.ACTION_MOVE:
                    break;
                case MotionEvent.ACTION_UP:
                    startDismissControlViewTimer();
                    if (mChangePosition) {
                        long duration = getDuration();
                        long progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        progressBar.setProgress((int) progress);
                    }
                    if (!mChangePosition && !mChangeVolume) {
                        onClickUiToggle();
                    }
                    break;
            }
        } else if (id == R.id.progress) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    cancelDismissControlViewTimer();
                    break;
                case MotionEvent.ACTION_UP:
                    startDismissControlViewTimer();
                    break;
            }
        }
        float x = event.getX();
        float y = event.getY();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "onTouch surfaceContainer actionDown [" + this.hashCode() + "] ");
                    mTouchingProgressBar = true;

                    mDownX = x;
                    mDownY = y;
                    mChangeVolume = false;
                    mChangePosition = false;
                    /////////////////////
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "onTouch surfaceContainer actionMove [" + this.hashCode() + "] ");
                    float deltaX = x - mDownX;
                    float deltaY = y - mDownY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);
                    if (mIfCurrentIsFullscreen) {
                        if (!mChangePosition && !mChangeVolume) {
                            if (absDeltaX > mThreshold || absDeltaY > mThreshold) {
                                cancelProgressTimer();
                                if (absDeltaX >= mThreshold) {
                                    mChangePosition = true;
                                    mDownPosition = getCurrentPositionWhenPlaying();
                                    if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
                                        JC_BURIED_POINT.onTouchScreenSeekPosition(mUrl, mObjects);
                                    }
                                } else {
                                    mChangeVolume = true;
                                    mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                    if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
                                        JC_BURIED_POINT.onTouchScreenSeekVolume(mUrl, mObjects);
                                    }
                                }
                            }
                        }
                    }
                    if (mChangePosition) {
                        long totalTimeDuration = getDuration();
                        mSeekTimePosition = (int) (mDownPosition + deltaX * totalTimeDuration / mScreenWidth);
                        if (mSeekTimePosition > totalTimeDuration)
                            mSeekTimePosition = totalTimeDuration;
                        String seekTime = JCUtils.stringForTime((int) mSeekTimePosition);
                        String totalTime = JCUtils.stringForTime((int) totalTimeDuration);

                        showProgressDialog(deltaX, seekTime, (int) mSeekTimePosition, totalTime, (int) totalTimeDuration);
                    }
                    if (mChangeVolume) {
                        deltaY = -deltaY;
                        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
                        int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);

                        showVolumDialog(-deltaY, volumePercent);
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "onTouch surfaceContainer actionUp [" + this.hashCode() + "] ");
                    mTouchingProgressBar = false;
                    dismissProgressDialog();
                    dismissVolumDialog();
                    if (mChangePosition) {
                        DJMediaManager.instance().mediaPlayer.seekTo(mSeekTimePosition);
                        long duration = getDuration();
                        long progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        progressBar.setProgress((int) progress);
                    }
                    /////////////////////
                    startProgressTimer();
                    if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
                        if (mIfCurrentIsFullscreen) {
                            JC_BURIED_POINT.onClickSeekbarFullscreen(mUrl, mObjects);
                        } else {
                            JC_BURIED_POINT.onClickSeekbar(mUrl, mObjects);
                        }
                    }
                    break;
            }
        } else if (id == R.id.progress) {//if I am seeking bar,no mater whoever can not intercept my event
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "onTouch bottomProgress actionUp [" + this.hashCode() + "] ");
                    cancelProgressTimer();
                    ViewParent vpdown = getParent();
                    while (vpdown != null) {
                        vpdown.requestDisallowInterceptTouchEvent(true);
                        vpdown = vpdown.getParent();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "onTouch bottomProgress actionDown [" + this.hashCode() + "] ");
                    startProgressTimer();
                    ViewParent vpup = getParent();
                    while (vpup != null) {
                        vpup.requestDisallowInterceptTouchEvent(false);
                        vpup = vpup.getParent();
                    }
                    break;
            }
        }

        return false;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        //先判断地址是否为空 为空直接返toast 并返回
        if (i == R.id.start
                || i == R.id.thumb || i == R.id.start) {
            if (TextUtils.isEmpty(mUrl)) {
                Toast.makeText(getContext(), "视频地址为空", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (i == R.id.start) {
            Log.i(TAG, "onClick start [" + this.hashCode() + "] ");
            if (TextUtils.isEmpty(mUrl)) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (mCurrentState == CURRENT_STATE_NORMAL || mCurrentState == CURRENT_STATE_ERROR) {
                if (!JCUtils.isWifiConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setMessage(getResources().getString(R.string.tips_not_wifi));
                    builder.setPositiveButton(getResources().getString(R.string.tips_not_wifi_confirm), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                            startButtonLogic();
                            WIFI_TIP_DIALOG_SHOWED = true;
                        }
                    });
                    builder.setNegativeButton(getResources().getString(R.string.tips_not_wifi_cancel), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                    builder.create().show();
                    return;
                }
                startButtonLogic();
            } else if (mCurrentState == CURRENT_STATE_PLAYING) {
                Log.d(TAG, "pauseVideo [" + this.hashCode() + "] ");
                DJMediaManager.instance().mediaPlayer.getPlayerControl().pause();
                setStateAndUi(CURRENT_STATE_PAUSE);
                if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
                    if (mIfCurrentIsFullscreen) {
                        JC_BURIED_POINT.onClickStopFullscreen(mUrl, mObjects);
                    } else {
                        JC_BURIED_POINT.onClickStop(mUrl, mObjects);
                    }
                }
            } else if (mCurrentState == CURRENT_STATE_PAUSE) {
                if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
                    if (mIfCurrentIsFullscreen) {
                        JC_BURIED_POINT.onClickResumeFullscreen(mUrl, mObjects);
                    } else {
                        JC_BURIED_POINT.onClickResume(mUrl, mObjects);
                    }
                }
                DJMediaManager.instance().mediaPlayer.getPlayerControl().start();
                setStateAndUi(CURRENT_STATE_PLAYING);
            } else if (mCurrentState == CURRENT_STATE_AUTO_COMPLETE) {
                startButtonLogic();
            }
        } else if (i == R.id.fullscreen) {
            Log.i(TAG, "onClick fullscreen [" + this.hashCode() + "] ");
            if (mCurrentState == CURRENT_STATE_AUTO_COMPLETE) return;
            if (mIfCurrentIsFullscreen) {
                //quit fullscreen
                backFullscreen();
            } else {
                Log.d(TAG, "toFullscreenActivity [" + this.hashCode() + "] ");
                if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
                    JC_BURIED_POINT.onEnterFullscreen(mUrl, mObjects);
                }
                //to fullscreen
                DJMediaManager.instance().setDisplay(null);
                DJMediaManager.instance().lastListener = this;
                DJMediaManager.instance().listener = null;
                IF_FULLSCREEN_FROM_NORMAL = true;
                IF_RELEASE_WHEN_ON_PAUSE = false;
//                DJFullScreenActivity.startActivityFromNormal(getContext(), mCurrentState, mUrl, DJVideoPlayer.this.getClass(), this.mObjects);
            }
        } else if (i == R.id.surface_container && mCurrentState == CURRENT_STATE_ERROR) {
            Log.i(TAG, "onClick surfaceContainer State=Error [" + this.hashCode() + "] ");
            if (JC_BURIED_POINT != null) {
                JC_BURIED_POINT.onClickStartError(mUrl, mObjects);
            }
            prepareVideo();
        } else if (i == R.id.start) {
            // 直接复用开始按钮点击事件
            startButton.performClick();
        } else if (i == R.id.thumb) {
            if (mCurrentState == CURRENT_STATE_NORMAL) {
                if (JC_BURIED_POINT_STANDARD != null) {
                    JC_BURIED_POINT_STANDARD.onClickStartThumb(mUrl, mObjects);
                }
                prepareVideo();
                startDismissControlViewTimer();
            }
        } else if (i == R.id.surface_container) {
            if (JC_BURIED_POINT_STANDARD != null && DJMediaManager.instance().listener == this) {
                if (mIfCurrentIsFullscreen) {
                    JC_BURIED_POINT_STANDARD.onClickBlankFullscreen(mUrl, mObjects);
                } else {
                    JC_BURIED_POINT_STANDARD.onClickBlank(mUrl, mObjects);
                }
            }
            startDismissControlViewTimer();
        } else if (i == R.id.back) {
            backFullscreen();
        }
    }

    private void startButtonLogic() {
        if (JC_BURIED_POINT != null && mCurrentState == CURRENT_STATE_NORMAL) {
            JC_BURIED_POINT.onClickStartIcon(mUrl, mObjects);
        } else if (JC_BURIED_POINT != null) {
            JC_BURIED_POINT.onClickStartError(mUrl, mObjects);
        }
        prepareVideo();
    }

    public TencentExoPlayer.RendererBuilder getRendererBuilder(String url) {
        Uri contentUri = Uri.parse(url);
        String userAgent = Util.getUserAgent(mContext, "ExoPlayerDemo");
        switch (this.contentType) {
            case HLS:
                return new HlsRendererBuilder(mContext, userAgent, contentUri.toString(), this.audioCapabilities);
            case MP4:
                return new ExtractorRendererBuilder(mContext, userAgent, contentUri, new Mp4Extractor());
            case MP3:
                return new ExtractorRendererBuilder(mContext, userAgent, contentUri, new Mp3Extractor());
            case AAC:
                return new ExtractorRendererBuilder(mContext, userAgent, contentUri, new TsExtractor());
            case FMP4:
                return new ExtractorRendererBuilder(mContext, userAgent, contentUri, new AdtsExtractor());
            case WEBM:
                return new ExtractorRendererBuilder(mContext, userAgent, contentUri, new FragmentedMp4Extractor());
            case MKV:
            case TS:
                return new ExtractorRendererBuilder(mContext, userAgent, contentUri, new WebmExtractor());
            default:
                throw new IllegalStateException("Unsupported type: " + this.contentType);
        }
    }

    public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        boolean audioCapabilitiesChanged = !audioCapabilities.equals(this.audioCapabilities);
        if (DJMediaManager.instance().mediaPlayer != null && !audioCapabilitiesChanged) {
            DJMediaManager.instance().mediaPlayer.setBackgrounded(false);
        } else {
            this.audioCapabilities = audioCapabilities;
            DJMediaManager.instance().releaseMediaPlayer();
            prepareVideo();
        }

    }

    protected void prepareVideo() {
        Log.d(TAG, "prepareVideo [" + this.hashCode() + "] ");
        if (DJMediaManager.instance().listener != null) {
            DJMediaManager.instance().listener.onCompletion();
        }
        DJMediaManager.instance().listener = this;
        addTextureView();
        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        ((Activity) getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        DJMediaManager.instance().prepare(mUrl, mMapHeadData, mLooping, getRendererBuilder(mUrl));
        setStateAndUi(CURRENT_STATE_PREPAREING);
    }

    private static AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    releaseAllVideos();
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (DJMediaManager.instance().mediaPlayer.getPlayerControl().isPlaying()) {
                        DJMediaManager.instance().mediaPlayer.getPlayerControl().pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };

    protected void addTextureView() {
        Log.d(TAG, "addTextureView [" + this.hashCode() + "] ");
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
//        if(mCurrentState == CURRENT_STATE_PLAYING) {
//            if(DJMediaManager.instance().mediaPlayer != null) {
//                DJMediaManager.instance().mediaPlayer.getPlayerControl().pause();
//            }
//        }
        textureView = null;
        textureView = new JCResizeTextureView(getContext());
        textureView.setSurfaceTextureListener(this);

        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        textureViewContainer.addView(textureView, layoutParams);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable [" + this.hashCode() + "] ");
        mSurface = new Surface(surface);
        DJMediaManager.instance().setDisplay(mSurface);
//        if(mCurrentState == CURRENT_STATE_PLAYING ) {
//            if(DJMediaManager.instance().mediaPlayer != null) {
//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        DJMediaManager.instance().mediaPlayer.getPlayerControl().start();
//                    }
//                },3000);
//
//            }
//        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        surface.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    protected void showProgressDialog(float deltaX,
                                      String seekTime, int seekTimePosition,
                                      String totalTime, int totalTimeDuration) {

    }

    protected void dismissProgressDialog() {

    }

    protected void showVolumDialog(float deltaY, int volumePercent) {

    }

    protected void dismissVolumDialog() {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (mCurrentState != CURRENT_STATE_PLAYING &&
                mCurrentState != CURRENT_STATE_PAUSE) return;
        if (fromUser) {
            long time = progress * getDuration() / 100;
            DJMediaManager.instance().mediaPlayer.seekTo(time);
            Log.i(TAG, "seekTo " + time + " [" + this.hashCode() + "] ");
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    private void onClickUiToggle() {
        if (mCurrentState == CURRENT_STATE_PREPAREING) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToClearUiPrepareing();
            } else {
                changeUiToShowUiPrepareing();
            }
        } else if (mCurrentState == CURRENT_STATE_PLAYING) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToClearUiPlaying();
            } else {
                changeUiToShowUiPlaying();
            }
        } else if (mCurrentState == CURRENT_STATE_PAUSE) {
            if (bottomContainer.getVisibility() == View.VISIBLE) {
                changeUiToClearUiPause();
            } else {
                changeUiToShowUiPause();
            }
        }
    }

    protected void setProgressAndTime(long progress, long secProgress, long currentTime, long totalTime) {
        if (!mTouchingProgressBar) {
            if (progress != 0) progressBar.setProgress((int) progress);
        }
        if (secProgress != 0) progressBar.setSecondaryProgress((int) secProgress);
        currentTimeTextView.setText(JCUtils.stringForTime((int) currentTime));
        totalTimeTextView.setText(JCUtils.stringForTime((int) totalTime));
        if (progress != 0) mBottomProgressBar.setProgress((int) progress);
        if (secProgress != 0) mBottomProgressBar.setSecondaryProgress((int) secProgress);
    }

    protected void resetProgressAndTime() {
        progressBar.setProgress(0);
        progressBar.setSecondaryProgress(0);
        currentTimeTextView.setText(JCUtils.stringForTime(0));
        totalTimeTextView.setText(JCUtils.stringForTime(0));
        mBottomProgressBar.setProgress(0);
        mBottomProgressBar.setSecondaryProgress(0);
    }

    //Unified management Ui
    private void changeUiToNormal() {
        topContainer.setVisibility(View.VISIBLE);
        bottomContainer.setVisibility(View.INVISIBLE);
        startButton.setVisibility(View.VISIBLE);
        mLoadingProgressBar.setVisibility(View.INVISIBLE);
        mThumbImageView.setVisibility(View.VISIBLE);
        mCoverImageView.setVisibility(View.VISIBLE);
        mBottomProgressBar.setVisibility(View.INVISIBLE);
        updateStartImage();
    }

    private void changeUiToShowUiPrepareing() {
        topContainer.setVisibility(View.VISIBLE);
        bottomContainer.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.INVISIBLE);
        mLoadingProgressBar.setVisibility(View.VISIBLE);
        mThumbImageView.setVisibility(View.INVISIBLE);
        mCoverImageView.setVisibility(View.VISIBLE);
        mBottomProgressBar.setVisibility(View.INVISIBLE);
    }

    private void changeUiToClearUiPrepareing() {
//        changeUiToClearUi();
        topContainer.setVisibility(View.INVISIBLE);
        bottomContainer.setVisibility(View.INVISIBLE);
        startButton.setVisibility(View.INVISIBLE);
        mThumbImageView.setVisibility(View.INVISIBLE);
        mBottomProgressBar.setVisibility(View.INVISIBLE);
//        mLoadingProgressBar.setVisibility(View.VISIBLE);
        mCoverImageView.setVisibility(View.VISIBLE);
    }

    private void changeUiToShowUiPlaying() {
        topContainer.setVisibility(View.VISIBLE);
        bottomContainer.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.VISIBLE);
        mLoadingProgressBar.setVisibility(View.INVISIBLE);
        mThumbImageView.setVisibility(View.INVISIBLE);
        mCoverImageView.setVisibility(View.INVISIBLE);
        mBottomProgressBar.setVisibility(View.INVISIBLE);
        updateStartImage();
    }

    private void changeUiToClearUiPlaying() {
        changeUiToClearUi();
        mBottomProgressBar.setVisibility(View.VISIBLE);
    }

    private void changeUiToShowUiPause() {
        topContainer.setVisibility(View.VISIBLE);
        bottomContainer.setVisibility(View.VISIBLE);
        startButton.setVisibility(View.VISIBLE);
        mLoadingProgressBar.setVisibility(View.INVISIBLE);
        mThumbImageView.setVisibility(View.INVISIBLE);
        mCoverImageView.setVisibility(View.INVISIBLE);
        mBottomProgressBar.setVisibility(View.INVISIBLE);
        updateStartImage();
    }

    private void changeUiToClearUiPause() {
        changeUiToClearUi();
        mBottomProgressBar.setVisibility(View.VISIBLE);
    }

    private void changeUiToClearUi() {
        topContainer.setVisibility(View.INVISIBLE);
        bottomContainer.setVisibility(View.INVISIBLE);
        startButton.setVisibility(View.INVISIBLE);
        mLoadingProgressBar.setVisibility(View.INVISIBLE);
        mThumbImageView.setVisibility(View.INVISIBLE);
        mCoverImageView.setVisibility(View.INVISIBLE);
        mBottomProgressBar.setVisibility(View.INVISIBLE);
    }

    private void changeUiToError() {
        topContainer.setVisibility(View.INVISIBLE);
        bottomContainer.setVisibility(View.INVISIBLE);
        startButton.setVisibility(View.VISIBLE);
        mLoadingProgressBar.setVisibility(View.INVISIBLE);
        mThumbImageView.setVisibility(View.INVISIBLE);
        mCoverImageView.setVisibility(View.VISIBLE);
        mBottomProgressBar.setVisibility(View.INVISIBLE);
        updateStartImage();
    }

    private void updateStartImage() {
//        if (mCurrentState == CURRENT_STATE_PLAYING) {
//            startButton.setImageResource(R.drawable.click_video_pause_selector);
//            mBottomStart.setImageResource(R.drawable.video_player_pause);
//        } else if (mCurrentState == CURRENT_STATE_ERROR) {
//            startButton.setImageResource(R.drawable.click_video_error_selector);
//            mBottomStart.setImageResource(R.drawable.video_player_play);
//        } else {
//            startButton.setImageResource(R.drawable.click_video_play_selector);
//            mBottomStart.setImageResource(R.drawable.video_player_play);
//        }
    }

    private void startDismissControlViewTimer() {
        cancelDismissControlViewTimer();
        DISSMISS_CONTROL_VIEW_TIMER = new Timer();
        DISSMISS_CONTROL_VIEW_TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                if (getContext() != null && getContext() instanceof Activity) {
                    ((Activity) getContext()).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mCurrentState != CURRENT_STATE_NORMAL
                                    && mCurrentState != CURRENT_STATE_ERROR) {
                                bottomContainer.setVisibility(View.INVISIBLE);
                                topContainer.setVisibility(View.INVISIBLE);
                                mBottomProgressBar.setVisibility(View.VISIBLE);
                                startButton.setVisibility(View.INVISIBLE);
                            }
                        }
                    });
                }
            }
        }, 2500);
    }

    private void cancelDismissControlViewTimer() {
        if (DISSMISS_CONTROL_VIEW_TIMER != null) {
            DISSMISS_CONTROL_VIEW_TIMER.cancel();
        }
    }

    public static void setJcBuriedPointStandard(JCBuriedPointStandard jcBuriedPointStandard) {
        JC_BURIED_POINT_STANDARD = jcBuriedPointStandard;
        DJVideoPlayer.setJcBuriedPoint(jcBuriedPointStandard);
    }

    @Override
    public void onAutoCompletion() {
        //make me normal first
        if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
            if (mIfCurrentIsFullscreen) {
                JC_BURIED_POINT.onAutoCompleteFullscreen(mUrl, mObjects);
            } else {
                JC_BURIED_POINT.onAutoComplete(mUrl, mObjects);
            }
        }
        setStateAndUi(CURRENT_STATE_AUTO_COMPLETE);
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        finishFullscreenActivity();
        if (IF_FULLSCREEN_FROM_NORMAL) {//如果在进入全屏后播放完就初始化自己非全屏的控件
            IF_FULLSCREEN_FROM_NORMAL = false;
            DJMediaManager.instance().lastListener.onAutoCompletion();
        }
        DJMediaManager.instance().lastListener = null;
        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onCompletion() {
        //make me normal first
        setStateAndUi(CURRENT_STATE_NORMAL);
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        //if fullscreen finish activity what ever the activity is directly or click fullscreen
        finishFullscreenActivity();

        if (IF_FULLSCREEN_FROM_NORMAL) {//如果在进入全屏后播放完就初始化自己非全屏的控件
            IF_FULLSCREEN_FROM_NORMAL = false;
            DJMediaManager.instance().lastListener.onCompletion();
        }
        DJMediaManager.instance().listener = null;
        DJMediaManager.instance().lastListener = null;
        DJMediaManager.instance().currentVideoWidth = 0;
        DJMediaManager.instance().currentVideoHeight = 0;

        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        cancelDismissControlViewTimer();
    }

    @Override
    public void onBufferingUpdate(int percent) {
        if (mCurrentState != CURRENT_STATE_NORMAL && mCurrentState != CURRENT_STATE_PREPAREING) {
            Log.v(TAG, "onBufferingUpdate " + percent + " [" + this.hashCode() + "] ");
            setTextAndProgress(percent);
        }
    }

    @Override
    public void onSeekComplete() {

    }

    @Override
    public void onError(int what, int extra) {
        Log.e(TAG, "onError " + what + " - " + extra + " [" + this.hashCode() + "] ");
        if (what != 38 && what != -38) {
            setStateAndUi(CURRENT_STATE_ERROR);
        }
    }

    @Override
    public void onInfo(int what, int extra) {
        Log.d(TAG, "onInfo what - " + what + " extra - " + extra);
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            BACKUP_PLAYING_BUFFERING_STATE = mCurrentState;
            setStateAndUi(CURRENT_STATE_PLAYING_BUFFERING_START);
            Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            if (BACKUP_PLAYING_BUFFERING_STATE != -1) {
                setStateAndUi(BACKUP_PLAYING_BUFFERING_STATE);
                BACKUP_PLAYING_BUFFERING_STATE = -1;
            }
            Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
        }
    }

    @Override
    public void onVideoSizeChanged() {
        int mVideoWidth = DJMediaManager.instance().currentVideoWidth;
        int mVideoHeight = DJMediaManager.instance().currentVideoHeight;
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            textureView.requestLayout();
        }
    }

    @Override
    public void onBackFullscreen() {
        mCurrentState = DJMediaManager.instance().lastState;
        setStateAndUi(mCurrentState);
        addTextureView();
    }

    protected void startProgressTimer() {
        cancelProgressTimer();
        UPDATE_PROGRESS_TIMER = new Timer();
        mProgressTimerTask = new ProgressTimerTask();
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 300);
    }

    protected void cancelProgressTimer() {
        if (UPDATE_PROGRESS_TIMER != null) {
            UPDATE_PROGRESS_TIMER.cancel();
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask.cancel();
        }

    }

    protected class ProgressTimerTask extends TimerTask {
        @Override
        public void run() {
            if (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE) {
                long position = getCurrentPositionWhenPlaying();
                long duration = getDuration();
                Log.v(TAG, "onProgressUpdate " + position + "/" + duration + " [" + this.hashCode() + "] ");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setTextAndProgress(0);
                    }
                });
            }
        }
    }

    protected long getCurrentPositionWhenPlaying() {
        long position = 0;
        if (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE) {
            try {
                if (DJMediaManager.instance().mediaPlayer != null) {
                    position = DJMediaManager.instance().mediaPlayer.getCurrentPosition();
                }
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return position;
            }
        }
        return position;
    }

    protected long getDuration() {
        long duration = 0;
        try {
            duration = DJMediaManager.instance().mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    protected void setTextAndProgress(int secProgress) {
        long position = getCurrentPositionWhenPlaying();
        long duration = getDuration();
        // if duration == 0 (e.g. in HLS streams) avoids ArithmeticException
        long progress = position * 100 / (duration == 0 ? 1 : duration);
        setProgressAndTime(progress, secProgress, position, duration);
    }

    protected void quitFullScreenGoToNormal() {
        Log.d(TAG, "quitFullScreenGoToNormal [" + this.hashCode() + "] ");
        if (JC_BURIED_POINT != null && DJMediaManager.instance().listener == this) {
            JC_BURIED_POINT.onQuitFullscreen(mUrl, mObjects);
        }
        DJMediaManager.instance().setDisplay(null);
        DJMediaManager.instance().listener = DJMediaManager.instance().lastListener;
        DJMediaManager.instance().lastListener = null;
        DJMediaManager.instance().lastState = mCurrentState;//save state
        DJMediaManager.instance().listener.onBackFullscreen();
        if (mCurrentState == CURRENT_STATE_PAUSE) {
            DJMediaManager.instance().mediaPlayer.seekTo(DJMediaManager.instance().mediaPlayer.getCurrentPosition());
        }
        if (mCurrentState == CURRENT_STATE_PLAYING) {
            mCurrentState = CURRENT_STATE_PREPAREING;
            DJMediaManager.instance().prepare(mUrl, mMapHeadData, mLooping, getRendererBuilder(mUrl), DJMediaManager.instance().mediaPlayer.getCurrentPosition());
        }
        finishFullscreenActivity();
    }

    public void backFullscreen() {
        Log.d(TAG, "quitFullscreen [" + this.hashCode() + "] ");
        IF_FULLSCREEN_FROM_NORMAL = false;
        if (mIfFullscreenIsDirectly) {
            DJMediaManager.instance().mediaPlayer.getPlayerControl().pause();
            finishFullscreenActivity();
        } else {
            CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
            IF_RELEASE_WHEN_ON_PAUSE = false;
            quitFullScreenGoToNormal();
        }
    }

    public static void releaseAllVideos() {
        if (IF_RELEASE_WHEN_ON_PAUSE) {
            Log.d(TAG, "releaseAllVideos");
            if (DJMediaManager.instance().listener != null) {
                DJMediaManager.instance().listener.onCompletion();
            }
            DJMediaManager.instance().releaseMediaPlayer();
        } else {
            IF_RELEASE_WHEN_ON_PAUSE = true;
        }
    }

    /**
     * !!! important
     * 如果需要记住上次播放的position，在release时请调用这个接口，不要调用JCVideoPlayer.releaseAllVideos
     */
    public void release() {
        if (mCurrentState != CURRENT_STATE_NORMAL) {
            if (DEBUG) Log.i(TAG, "release [" + this + "]" + mUrl);
            if (IF_FULLSCREEN_FROM_NORMAL) {
                if (DEBUG) Log.i(TAG, " enter full screen,reset memory state [" + this + "]");
                resetMemoryPlayerState();
            } else {
                if (DEBUG) Log.i(TAG, " save current memory state [" + this + "]");
                if (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE) {
                    try {
                        MEMORY_POSITION = DJMediaManager.instance().mediaPlayer.getCurrentPosition();
                    } catch (Exception e) {
                        MEMORY_POSITION = -1;
                    }
                }
                MEMORY_PLAYER_IS_PLAYING = (mCurrentState == CURRENT_STATE_PLAYING);
            }

            releaseAllVideos();
        }
    }

    public long getCurrentPosition() {
        return getCurrentPositionWhenPlaying();
    }

    public int getCurrentState() {
        return mCurrentState;
    }

    public static int getStatePrepareing() {
        return CURRENT_STATE_PREPAREING;
    }

    public static int getStatePause() {
        return CURRENT_STATE_PAUSE;
    }

    public static int getStatePlaying() {
        return CURRENT_STATE_PLAYING;
    }

    public static int getStateAutoComplete() {
        return CURRENT_STATE_AUTO_COMPLETE;
    }

    public static int getStateNormal() {
        return CURRENT_STATE_NORMAL;
    }

    public static int getStateError() {
        return CURRENT_STATE_ERROR;
    }

    @Override
    public void onPrepared() {
        if (mCurrentState != CURRENT_STATE_PREPAREING) return;
        DJMediaManager.instance().mediaPlayer.getPlayerControl().start();
        if (MEMORY_POSITION > 0) {
            DJMediaManager.instance().mediaPlayer.seekTo(MEMORY_POSITION);
        }
        resetMemoryPlayerState();
        startProgressTimer();
        setStateAndUi(CURRENT_STATE_PLAYING);
    }

    public void tryMemoryPlay() {
        if (MEMORY_PLAYER_IS_PLAYING) {
            Log.i(TAG, " use memory state [" + this + "]");
            startButton.performClick();
        }
    }

    private void resetMemoryPlayerState() {
        MEMORY_POSITION = -1;
        MEMORY_PLAYER_IS_PLAYING = false;
    }

    public void setVideoStateChangeListener(VideoStateChangeListener listener) {
        this.mVideoStateChangeListener = listener;
    }

    public boolean getIfCurrentIsFullScreen() {
        return mIfCurrentIsFullscreen;
    }

    public boolean getIfFullscreenIsDirectly() {
        return mIfFullscreenIsDirectly;
    }

    protected void setIfCurrentIsFullScreen(boolean isFullScreen) {
        mIfCurrentIsFullscreen = isFullScreen;
    }

    protected void setIfFullscreenIsDirectly(boolean isFullScreenDirectly) {
        mIfFullscreenIsDirectly = isFullScreenDirectly;
    }

    protected void finishFullscreenActivity() {
//        if (getContext() instanceof DJFullScreenActivity) {
//            Log.i(TAG, "finishFullscreenActivity [" + this.hashCode() + "] ");
//            ((DJFullScreenActivity) getContext()).finish();
//        }
    }

    public interface VideoStateChangeListener {
        void onPlaying();

        void onPause();
    }

    public boolean canFullScreen() {
        return mCurrentState == CURRENT_STATE_PREPAREING ||
                mCurrentState == CURRENT_STATE_PLAYING ||
                mCurrentState == CURRENT_STATE_PLAYING_BUFFERING_START ||
                mCurrentState == CURRENT_STATE_PAUSE;
    }

    public void invisibleThat() {
        mThumbImageView.setVisibility(View.INVISIBLE);
        mCoverImageView.setVisibility(View.INVISIBLE);
    }

    public void setHolder() {
        DJMediaManager.instance().setDisplay(mSurface);
    }
}
