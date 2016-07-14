package fm.jiecao.jcvideoplayer_lib.tencent;

import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.view.Surface;

import com.google.android.exoplayer.text.Cue;
import com.tencent.qcload.playersdk.player.TencentExoPlayer;

import java.util.List;
import java.util.Map;

/**
 * Created by Max on 16/7/11.
 */
public class DJMediaManager implements MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnVideoSizeChangedListener, MediaPlayer.OnInfoListener {
    public static String TAG = DJVideoPlayer.TAG;

    //public MediaPlayer mediaPlayer;
    public TencentExoPlayer mediaPlayer;
    private static DJMediaManager JCMediaManager;
    public int currentVideoWidth = 0;
    public int currentVideoHeight = 0;
    public JCMediaPlayerListener listener;
    public JCMediaPlayerListener lastListener;
    public int lastState;

    public static final int HANDLER_PREPARE = 0;
    public static final int HANDLER_SETDISPLAY = 1;
    public static final int HANDLER_RELEASE = 2;
    HandlerThread mMediaHandlerThread;
    MediaHandler mMediaHandler;
    Handler mainThreadHandler;

    public static DJMediaManager instance() {
        if (JCMediaManager == null) {
            JCMediaManager = new DJMediaManager();
        }
        return JCMediaManager;
    }

    public DJMediaManager() {
        mMediaHandlerThread = new HandlerThread(TAG);
        mMediaHandlerThread.start();
        mMediaHandler = new MediaHandler((mMediaHandlerThread.getLooper()));
        mainThreadHandler = new Handler();
    }

    public class MediaHandler extends Handler {
        public MediaHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_PREPARE:
                    try {
                        currentVideoWidth = 0;
                        currentVideoHeight = 0;
                        if(mediaPlayer != null) {
                            mediaPlayer.release();
                            mediaPlayer = null;
                        }
                        mediaPlayer = new TencentExoPlayer(((FuckBean) msg.obj).rendererBuilder);
//                        mediaPlayer.reset();
//                        mediaPlayer.setDataSource(mContext,Uri.parse("http://clips.vorwaerts-gmbh.de/big_buck_bunny.mp4"));
//                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
//                        mediaPlayer.setLooping(((FuckBean) msg.obj).looping);
//                        mediaPlayer.setOnPreparedListener(DJMediaManager.this);
//                        mediaPlayer.setOnCompletionListener(DJMediaManager.this);
//                        mediaPlayer.setOnBufferingUpdateListener(DJMediaManager.this);
//                        mediaPlayer.setScreenOnWhilePlaying(true);
//                        mediaPlayer.setOnSeekCompleteListener(DJMediaManager.this);
//                        mediaPlayer.setOnErrorListener(DJMediaManager.this);
//                        mediaPlayer.setOnInfoListener(DJMediaManager.this);
//                        mediaPlayer.setOnVideoSizeChangedListener(DJMediaManager.this);
//                        mediaPlayer.prepareAsync();
                        mediaPlayer.setPlayWhenReady(true);
                        mediaPlayer.seekTo(((FuckBean) msg.obj).position);
                        mediaPlayer.addListener(new TencentExoPlayer.Listener() {
                            @Override
                            public void onStateChanged(boolean playWhenReady, int playbackState) {
                                switch (playbackState) {
                                    case TencentExoPlayer.STATE_READY:
                                        onPrepared(null);
                                        onSeekComplete(null);
                                        break;
                                    case TencentExoPlayer.STATE_BUFFERING:
                                        //onBufferingUpdate(null, mediaPlayer.getBufferedPercentage());
                                        break;
                                    case TencentExoPlayer.STATE_ENDED:
                                        onCompletion(null);
                                        break;
                                }
                            }

                            @Override
                            public void onError(Exception e) {
                                DJMediaManager.this.onError(null, -1, -1);
                            }

                            @Override
                            public void onVideoSizeChanged(int width, int height, float pixelWidthAspectRatio) {
                                DJMediaManager.this.onVideoSizeChanged(null, width, height);
                            }
                        });

                        mediaPlayer.setMetadataListener(new TencentExoPlayer.Id3MetadataListener() {
                            @Override
                            public void onId3Metadata(Map<String, Object> map) {

                            }
                        });

                        mediaPlayer.setCaptionListener(new TencentExoPlayer.CaptionListener() {
                            @Override
                            public void onCues(List<Cue> list) {

                            }
                        });


                        mediaPlayer.prepare();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case HANDLER_SETDISPLAY:
                    if (msg.obj == null) {
                        instance().mediaPlayer.setSurface(null);
                    } else {
                        Surface holder = (Surface) msg.obj;
                        if (holder.isValid()) {
                            instance().mediaPlayer.setSurface(holder);
                        }
                    }
                    break;
                case HANDLER_RELEASE:
                    mediaPlayer.release();
                    break;
            }
        }
    }


    public void prepare(final String url, final Map<String, String> mapHeadData, boolean loop, TencentExoPlayer.RendererBuilder rendererBuilder) {
        prepare(url, mapHeadData, loop, rendererBuilder, 0);
    }

    public void prepare(final String url, final Map<String, String> mapHeadData, boolean loop, TencentExoPlayer.RendererBuilder rendererBuilder, long currentPostion) {
        if (TextUtils.isEmpty(url)) return;
        Message msg = new Message();
        msg.what = HANDLER_PREPARE;
        FuckBean fb = new FuckBean(url, mapHeadData, loop, rendererBuilder, currentPostion);
        msg.obj = fb;
        mMediaHandler.sendMessage(msg);
    }

    public void releaseMediaPlayer() {
        Message msg = new Message();
        msg.what = HANDLER_RELEASE;
        mMediaHandler.sendMessage(msg);
    }

    public void setDisplay(Surface holder) {
        Message msg = new Message();
        msg.what = HANDLER_SETDISPLAY;
        msg.obj = holder;
        mMediaHandler.sendMessage(msg);
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onPrepared();
                }
            }
        });
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onAutoCompletion();
                }
            }
        });
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, final int percent) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onBufferingUpdate(percent);
                }
            }
        });
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onSeekComplete();
                }
            }
        });
    }

    @Override
    public boolean onError(MediaPlayer mp, final int what, final int extra) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onError(what, extra);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, final int what, final int extra) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onInfo(what, extra);
                }
            }
        });
        return false;
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if(mp != null) {
            currentVideoWidth = mp.getVideoWidth();
            currentVideoHeight = mp.getVideoHeight();
        } else {
            currentVideoHeight = height;
            currentVideoWidth = width;
        }
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (listener != null) {
                    listener.onVideoSizeChanged();
                }
            }
        });
    }

    public interface JCMediaPlayerListener {
        void onPrepared();

        void onAutoCompletion();

        void onCompletion();

        void onBufferingUpdate(int percent);

        void onSeekComplete();

        void onError(int what, int extra);

        void onInfo(int what, int extra);

        void onVideoSizeChanged();

        void onBackFullscreen();
    }

    private class FuckBean {
        String url;
        Map<String, String> mapHeadData;
        boolean looping;
        TencentExoPlayer.RendererBuilder rendererBuilder;
        long position = 0;


        FuckBean(String url, Map<String, String> mapHeadData, boolean loop, TencentExoPlayer.RendererBuilder rendererBuilder) {
            this(url, mapHeadData, loop, rendererBuilder, 0);

        }

        public FuckBean(String url, Map<String, String> mapHeadData, boolean looping, TencentExoPlayer.RendererBuilder rendererBuilder, long position) {
            this.url = url;
            this.mapHeadData = mapHeadData;
            this.looping = looping;
            this.rendererBuilder = rendererBuilder;
            this.position = position;
        }
    }
}