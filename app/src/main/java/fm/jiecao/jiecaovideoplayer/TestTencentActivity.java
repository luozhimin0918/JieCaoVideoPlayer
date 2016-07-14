package fm.jiecao.jiecaovideoplayer;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.SurfaceView;
import android.view.View;

import com.tencent.qcload.playersdk.util.VideoInfo;

import fm.jiecao.jcvideoplayer_lib.tencent.DJMediaManager;
import fm.jiecao.jcvideoplayer_lib.tencent.DJVideoPlayer;

/**
 * Created by Nathen on 16/7/15.
 */
public class TestTencentActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tencent);

        DJVideoPlayer dj1 = (DJVideoPlayer) findViewById(R.id.t1);
        final SurfaceView dj2 = (SurfaceView) findViewById(R.id.t2);

        dj1.setUp("http://2449.vod.myqcloud.com/2449_22ca37a6ea9011e5acaaf51d105342e3.f20.mp4", VideoInfo.VideoType.MP4);
//        dj2.setUp("http://2449.vod.myqcloud.com/2449_22ca37a6ea9011e5acaaf51d105342e3.f20.mp4", VideoInfo.VideoType.MP4);
//        dj2.invisibleThat();

        findViewById(R.id.change).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                DJMediaManager.instance().setDisplay(dj2.getHolder().getSurface());
            }
        });

    }
}
