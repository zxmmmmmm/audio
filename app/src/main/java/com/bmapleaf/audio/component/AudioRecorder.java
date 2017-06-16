package com.bmapleaf.audio.component;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.support.annotation.IntDef;
import android.util.Log;

import com.bmapleaf.utils.ObjectPool;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by ZhangMing on 2017/06/12.
 */

public class AudioRecorder implements IRecorder {
    private static final String stateName[] = {
            "Idle",
            "Initial",
            "Initialized",
            "DataSourceConfigured",
            "Prepared",
            "Recording",
            "Release",
            "Error",
    };
    private static final String TAG = "AudioRecorder";
    private static int audioSource = MediaRecorder.AudioSource.MIC;
    private static int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private static int bitRate = 96000;
    private AudioRecord audioRecord;
    private int sampleRateInHz = 41000;
    private int channels = 2;

    private AudioEncoder audioEncoder;

    /*listeners*/
    private OnErrorListener onErrorListener;
    private OnDataProcessListener onDataProcessListener;
    @State
    private int state;

    @Override
    public void pause() throws IllegalStateException {

    }

    @Override
    public void prepare() throws IllegalStateException, IOException {
        synchronized (this) {
            if (getState() == State.DataSourceConfigured) {
                int channelConfig = channels == Channel.MONO ? AudioFormat.CHANNEL_IN_MONO : AudioFormat.CHANNEL_IN_STEREO;
                int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
                audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes);
                if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                    audioRecord.release();
                    audioRecord = null;
                    onError(RECORDER_ERROR_INITIALIZED, RECORDER_ERROR_CHANNEL_NOT_SUPPORT);
                }
            } else {
                throw new IllegalStateException("prepare() must called after DataSourceConfigured");
            }
        }
    }

    private void onError(int what, int extra) {
        setState(State.Error);
        if (null != onErrorListener) {
            onErrorListener.onError(this, what, extra);
        }
    }

    private void onDataProcess(short[] data, int samples) {
        if (null != onDataProcessListener) {
            onDataProcessListener.onProcess(data, samples);
        }
    }

//    private float getBufferDuration(int bufferSize) {
//        return bufferSize * 1000 * 8 * 2.0f / sampleRateInHz / bitPerSample / channels;
//    }

    @Override
    public void release() {
        setState(State.Release);
    }

    @Override
    public void reset() {
        setState(State.Initial);
    }

    @Override
    public void resume() throws IllegalStateException {

    }

    @Override
    public void stop() throws IllegalStateException {
        if (getState() == State.Recording) {
            setState(State.Initial);
        } else {
            throw new IllegalStateException("stop() must called after start()");
        }
    }

    @Override
    public void start() throws IllegalStateException {
        if (getState() == State.Prepared) {
            setState(State.Recording);
        } else {
            throw new IllegalStateException("start() must called after prepare()");
        }
    }

    @Override
    public void reStart() {

    }

    @Override
    public void setAudioChannels(@Channel int numChannels) {
        channels = numChannels;
    }

    @Override
    public void setAudioSamplingRate(int samplingRate) {
        sampleRateInHz = samplingRate;
    }

    @Override
    public void setOutputFile(String file, boolean encode) throws IllegalStateException, IOException {
        audioEncoder = new AudioEncoder();
        audioEncoder.setOutputFile(file, encode);
    }

    @Override
    public void setOnErrorListener(OnErrorListener l) {
        onErrorListener = l;
    }

    @Override
    public void setOnDataProcessListener(OnDataProcessListener l) {
        onDataProcessListener = l;
    }

    @State
    private int getState() {
        return state;
    }

    private void setState(@State int stateNew) {
        int sO = this.state;
        state = stateNew;
        Log.d(TAG, "setState() called with: [" + getStateName(sO) + "->" + getStateName(state) + "]");
    }

    private String getStateName(@State int state) {
        return state < stateName.length - 1 ? stateName[state] : String.valueOf(state);
    }

    @IntDef({State.Idle, State.Initial, State.Initialized, State.DataSourceConfigured,
            State.Prepared, State.Recording, State.Release, State.Error})
    @Retention(RetentionPolicy.SOURCE)
    @interface State {
        int Idle = 0;
        int Initial = 1;
        int Initialized = 2;
        int DataSourceConfigured = 3;
        int Prepared = 4;
        int Recording = 5;
        int Release = 6;
        int Error = 7;
    }

    private static class AudioBuffer {
        private static final ObjectPool<AudioBuffer> oPool = new ObjectPool<>(new ObjectPool.ObjectFactory<AudioBuffer>() {
            @Override
            public AudioBuffer newObject() {
                return new AudioBuffer();
            }
        }, TAG);
        final int size = 1024;
        short[] buffer;
        float duration;
        long index;
        long presentationTimeUs;

        private AudioBuffer() {
            buffer = new short[size];
        }

        static AudioBuffer obtain() {
            return oPool.acquire();
        }

        void recycle() {
            // Clear state if needed.
            Arrays.fill(buffer, (short) 0);
            duration = 0;
            index = 0;
            presentationTimeUs = 0;
            oPool.release(this);
        }
    }

    private class AudioEncoder {
        private static final int maxPoolSize = 300;
        private Queue<AudioBuffer> audioBuffers = new ArrayDeque<>();
        private int bufferIndex;
        private int writePtr;
        private AudioBuffer writeAudio;
        private boolean isEos;
        private boolean isSampleEos;
        private boolean isRunning;
        private MediaCodecWrapper mMediaCodecWrapper;
        private MediaMuxer mMediaMuxer;
        private MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
        private Lock lock = new ReentrantLock();
        private Condition condition = lock.newCondition();
        private FutureTask<Integer> futureTask;

        void setOutputFile(String audioPath, boolean isEncoded) throws IOException {
            MediaFormat mediaFormat = MediaFormat.createAudioFormat("audio/mp4a-latm", sampleRateInHz, channels);
            mediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);

            mMediaCodecWrapper = MediaCodecWrapper.fromAudioFormat(mediaFormat);
        }
    }
}
