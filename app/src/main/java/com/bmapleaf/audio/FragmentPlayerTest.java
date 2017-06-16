package com.bmapleaf.audio;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bmapleaf.audio.component.AudioPlayer;
import com.bmapleaf.audio.component.IPlayer;

import java.io.IOException;

/**
 * Created by ZhangMing on 2017/06/12.
 */

public class FragmentPlayerTest extends Fragment implements View.OnClickListener, IPlayer.OnCompletionListener {
    private Button btPlay, btPause, btStop, btPrepare, btRestart;
    private TextView textCurrent, textTotal;
    private ProgressBar progress;
    private IPlayer player;
    private Handler handler = new Handler();
    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            int currentPosition = player.getCurrentPosition();
            textCurrent.setText(formatTime(currentPosition));
            progress.setProgress(currentPosition);
            handler.postDelayed(runnable, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        //return super.onCreateView(inflater, container, savedInstanceState);
        View rootView = inflater.inflate(R.layout.layout_player, null);

        btPrepare = (Button) rootView.findViewById(R.id.btPrepare);
        btPlay = (Button) rootView.findViewById(R.id.btPlay);
        btPause = (Button) rootView.findViewById(R.id.btPause);
        btStop = (Button) rootView.findViewById(R.id.btStop);
        btRestart = (Button) rootView.findViewById(R.id.btRestart);

        textCurrent = (TextView) rootView.findViewById(R.id.textCurrent);
        textTotal = (TextView) rootView.findViewById(R.id.textTotal);
        progress = (ProgressBar) rootView.findViewById(R.id.progress);

        btPrepare.setOnClickListener(this);
        btPlay.setOnClickListener(this);
        btPause.setOnClickListener(this);
        btStop.setOnClickListener(this);
        btRestart.setOnClickListener(this);

        btPrepare.setEnabled(true);
        btPlay.setEnabled(false);
        btPause.setEnabled(false);
        btStop.setEnabled(false);
        btRestart.setEnabled(false);

        player = new AudioPlayer();
        try {
            player.setDataSource(Environment.getExternalStorageDirectory().getAbsolutePath() + "/1.mp3", true);
            player.setOnCompletionListener(this);
            player.setLooping(false);

            int start = 0, end = player.getDuration();
            player.setPlayRange(start, end);
            int duration = player.getDuration();
            progress.setMax(duration);
            progress.setSecondaryProgress(end);
            progress.setProgress(start);

            textCurrent.setText(formatTime(start));
            textTotal.setText(formatTime(duration));
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }

        return rootView;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btPrepare:
                Prepare();
                break;
            case R.id.btPlay:
                play();
                break;
            case R.id.btPause:
                pause();
                break;
            case R.id.btStop:
                stop();
                break;
            case R.id.btRestart:
                reStart();
                break;
        }
    }

    private void reStart() {
        try {
            handler.removeCallbacks(runnable);
            player.reStart();
            btPlay.setEnabled(false);
            handler.post(runnable);
            btPrepare.setEnabled(false);
            btPause.setEnabled(true);
            btStop.setEnabled(true);
            btRestart.setEnabled(true);
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }
    }

    private void Prepare() {
        try {
            btPrepare.setEnabled(false);
            btPlay.setEnabled(true);
            btPause.setEnabled(false);
            btStop.setEnabled(true);
            btRestart.setEnabled(true);
            player.prepare();
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace();
        }
    }

    private void stop() {
        handler.removeCallbacks(runnable);
        btPause.setEnabled(false);
        btStop.setEnabled(false);
        btPlay.setEnabled(false);
        player.stop();
        btPrepare.setEnabled(true);
        btRestart.setEnabled(true);
    }

    private void pause() {
        handler.removeCallbacks(runnable);
        btPause.setEnabled(false);
        player.pause();
        btPrepare.setEnabled(false);
        btPlay.setEnabled(true);
        btStop.setEnabled(true);
        btRestart.setEnabled(true);
    }

    private void play() {
        btPlay.setEnabled(false);
        player.start();
        handler.post(runnable);
        btPrepare.setEnabled(false);
        btPause.setEnabled(true);
        btStop.setEnabled(true);
        btRestart.setEnabled(true);
    }

    @SuppressLint("DefaultLocale")
    private String formatTime(int time) {
        return String.format("%02d:%02d", time / 1000 / 60, time / 1000 % 60);
    }

    @Override
    public void onCompletion(IPlayer p) {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                play();
            }
        });
    }
}
