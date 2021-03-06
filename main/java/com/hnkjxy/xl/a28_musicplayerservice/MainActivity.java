package com.hnkjxy.xl.a28_musicplayerservice;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.hnkjxy.xl.a28_musicplayerservice.entity.Music;
import com.hnkjxy.xl.a28_musicplayerservice.interfac.MusicInterface;
import com.hnkjxy.xl.a28_musicplayerservice.service.MusicService;
import com.hnkjxy.xl.a28_musicplayerservice.tool.MusicAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements
        View.OnClickListener, SeekBar.OnSeekBarChangeListener,
        AdapterView.OnItemClickListener {
    private static final int MY_PERMISSION_REQUEST_CODE = 10000;
    private ArrayList<Music> musics;
    public static Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            int currentPosition = msg.arg1;
            int duration = msg.arg2;
            if (flag) {
                sbMusicProgress.setProgress(currentPosition);
                sbMusicProgress.setMax(duration);
                tvMusicCurrentPosition.setText(getFormattedTime(currentPosition));
                tvMusicDuration.setText(getFormattedTime(duration));
            }
        }
    };
    /**
     * ??????????????????
     */
    private ListView lvMusics;
    private ImageButton ibPlayPause;//????????????
    private ImageButton ibNext;//?????????
    private static SeekBar sbMusicProgress;
//    private TextView tv

    private static boolean flag = true;//????????????SeekBar

    //??????binder(?????????)
    MusicInterface mi;
    private int currentMusicIndex;
    private MusicAdapter adapter;
    private static TextView tvMusicCurrentPosition;
    private static TextView tvMusicDuration;
    private ImageButton ibPlayMode;//????????????

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //?????????????????????
        if (getSupportActionBar() != null){
            getSupportActionBar().hide();
        }
        //????????????????????????API23???????????????????????????????????????
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            boolean isAllGranted;
            //1. ????????????
            isAllGranted = checkPermissionAllGranted(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
            // ????????????????????????????????????????????????????????????loadData???
            if (isAllGranted){
                //????????????
                musics = loadData();
            }
            //2. ????????????
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, MY_PERMISSION_REQUEST_CODE);

            //3. ?????????????????????????????????, ??????onRequestPermissionsResult
        }else {
            musics = loadData();
        }

        //???????????????
        initViews();
        //????????????
        setListener();

        //??????????????????
        initMusicList();

        //????????????
        if (musics != null) {
            bindSer();
        }

        //?????????????????????
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.hnkjxy.xl.music.position");
        registerReceiver(new MusiceReceiver(), intentFilter);
    }
    //------------??????????????????-----------
    private void initMusicList() {
        //??????MusicAdapter
        adapter = new MusicAdapter(this, musics);
        //???ListView??????adapter???????????????????????????ListView??????????????????????????????
        lvMusics.setAdapter(adapter);
    }

    //----------???????????????------
    private void initViews() {
        lvMusics = findViewById(R.id.lv_musics);
        ibPlayPause = findViewById(R.id.ib_play_or_pause);
        ibNext = findViewById(R.id.ib_next);
        ibPlayMode = findViewById(R.id.ib_play_mode);
        sbMusicProgress = findViewById(R.id.sb_music_progress);
        tvMusicCurrentPosition = findViewById(R.id.tv_music_current_position);
        tvMusicDuration = (TextView)findViewById(R.id.tv_music_duration);
    }

    //---------????????????-----
    private void setListener(){
        ibPlayPause.setOnClickListener(this);
        ibNext.setOnClickListener(this);
        ibPlayMode.setOnClickListener(this);
        sbMusicProgress.setOnSeekBarChangeListener(this);
        lvMusics.setOnItemClickListener(this);//??????????????????
    }

    //???????????????????????????
    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.ib_play_or_pause:
                //??????????????????MediaPlayer??????start()
                //????????????????????????????????????????????????????????????
                if (mi.isPlay()){
                    //???????????????????????????????????????
                    mi.pause();
                    ibPlayPause.setImageResource(R.drawable.selector_button_play);
                    return;
                }else {
                    mi.play();
//                    ibPlayPause.setImageResource(R.drawable.selector_button_pause);
                }

                break;
            case R.id.ib_next:
                mi.next();
                break;
            case R.id.ib_play_mode:
                switchPlayMode();//??????????????????
                break;
        }
        setPlayMode(currentMusicIndex);
    }

    private int playMode = 0;
    private static final int PLAY_REPEAT = 0;//????????????
    private static final int PLAY_SINGLE = 1;//????????????
    private static final int PLAY_RANDOM = 2;//??????

    private static final int[] playModeRes = {
            R.drawable.selector_button_mode_repeat,
            R.drawable.selector_button_mode_single,
            R.drawable.selector_button_mode_random
    };
    //??????????????????
    private void switchPlayMode() {
        playMode++;
        playMode %= playModeRes.length;
        ibPlayMode.setImageResource(playModeRes[playMode]);
        mi.changeMode(playMode);
    }

    //------??????????????????----
    public void setPlayMode(int currentPosition){
        ibPlayPause.setImageResource(R.drawable.selector_button_pause);
        for (Music m: musics) {
            m.setPlaying(false);
        }
        musics.get(currentPosition).setPlaying(true);
        adapter.notifyDataSetChanged();
    }

    //????????????
    private void bindSer(){
        Intent intent = new Intent(this, MusicService.class);
        Bundle bundle = new Bundle();
        bundle.putSerializable("musics", musics);
        intent.putExtras(bundle);
        bindService(intent, new ServiceConnection() {
                @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                mi = (MusicInterface) iBinder;//???????????????
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {

            }
        }, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSION_REQUEST_CODE){
            boolean isAllGranted = true;
            //???????????????????????????????????????
            for (int grant : grantResults){
                if (grant != PackageManager.PERMISSION_GRANTED){
                    isAllGranted = false;
                    break;
                }
                if (isAllGranted){
                    musics = loadData();
                }else {
                    Toast.makeText(this, "????????????", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    //????????????
    private boolean checkPermissionAllGranted(String[] permissions){
        boolean isAllGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED){
                //????????????
                isAllGranted = false;
                return isAllGranted;
            }
        }
        return isAllGranted;
    }

    private ArrayList<Music> loadData() {
        // ???????????????????????????
        ArrayList<Music> list = new ArrayList<>();

        Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.DISPLAY_NAME,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.YEAR,
                        MediaStore.Audio.Media.MIME_TYPE,
                        MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.DATA},
                MediaStore.Audio.Media.MIME_TYPE + "=? or "
                        + MediaStore.Audio.Media.MIME_TYPE + "=?",
                new String[]{"audio/mpeg", "audio/x-ms-wma"}, null
        );
        if (cursor.moveToFirst()) {
            Music music = null;
            do {
                music = new Music();
                // ?????????
                music.setFileName(cursor.getString(1));
                // ?????????
                music.setTitle(cursor.getString(2));
                // ??????
                music.setDuration(cursor.getInt(3));
                // ?????????
                music.setSinger(cursor.getString(4));
                // ?????????
                music.setAlbum(cursor.getString(5));
                // ??????
                if (cursor.getString(6) != null) {
                    music.setYear(cursor.getString(6));
                } else {
                    music.setYear("??????");
                }
                // ????????????
                if ("audio/mpeg".equals(cursor.getString(7).trim())) {
                    music.setType("mp3");
                } else if ("audio/x-ms-wma".equals(cursor.getString(7).trim())) {
                    music.setType("wma");
                }
                // ????????????
                if (cursor.getString(8) != null) {
                    float size = cursor.getInt(8) / 1024f / 1024f;
                    music.setSize((size + "").substring(0, 4) + "M");
                } else {
                    music.setSize("??????");
                }
                // ????????????
                if (cursor.getString(9) != null) {
                    music.setFileUrl(cursor.getString(9));
                }
                Log.i("xl_hsj", music.toString());
                list.add(music);
            } while (cursor.moveToNext());

            cursor.close();

        }
        return list;
    }

//TODO seekBar??????
    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        flag = !flag;
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        flag = !flag;
        //???????????????????????????
        mi.seekTo(seekBar.getProgress());
    }
//------------????????????------------
    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
        mi.play(i);//???????????????????????????
    }


    class MusiceReceiver extends BroadcastReceiver{

        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.hnkjxy.xl.music.position".equals(intent.getAction())){
                //?????????????????????????????????????????????
                setPlayMode(intent.getIntExtra("POSITION", 0));
            }
        }
    }


    //--------------???????????????
    private static SimpleDateFormat sdf =
            new SimpleDateFormat("mm:ss",Locale.CHINA);

    private static Date date = new Date();
    private static String getFormattedTime(long timeMillis){
        date.setTime(timeMillis);
        return sdf.format(date);
    }
}
