package com.bmapleaf.audio.component;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.support.annotation.IntDef;
import android.util.Log;

import com.bmapleaf.utils.ObjectPool;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by ZhangMing on 2017/06/05.
 */

public class AudioPlayer implements IPlayer, Callable<Integer> {
    private static final String TAG = "AudioPlayer";
    private static final int streamType = AudioManager.STREAM_MUSIC;
    private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private static final int mode = AudioTrack.MODE_STREAM;
    private static final int channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
    private static final int bitPerSample = 16;
    private static final String stateName[] = {
            "Idle",
            "Initialized",
            "Preparing",
            "Prepared",
            "Started",
            "Paused",
            "Stopped",
            "Completed ",
            "End",
            "Error",
    };
    @State
    private int state;
    private AudioTrack audioTrack;
    private boolean isLooping;
    private float volume;
    private long positionUs;
    private long positionStartUs;
    private long positionEndUs;
    private int sampleRateInHz;
    private int channels;
    private long durationUs;
    /*listeners*/
    private OnCompletionListener onCompletionListener;
    private OnPreparedListener onPreparedListener;
    private OnErrorListener onErrorListener;
    private OnDataProcessListener onDataProcessListener;
    private AudioDecoder audioDecoder;
    private ExecutorService executorService;
    private FutureTask<Integer> futureTask;

    public AudioPlayer() {
        init();
        setState(State.Idle);
        executorService = Executors.newFixedThreadPool(2);
    }

    private void init() {
        isLooping = false;
        volume = 1f;
        positionUs = positionStartUs = positionEndUs = 0;
    }

    @Override
    public void setDataSource(String audioPath, boolean isEncoded) throws IllegalStateException, IOException {
        synchronized (this) {
            if (getState() == State.Idle) {
                audioDecoder = new AudioDecoder();
                audioDecoder.setDataSource(audioPath, isEncoded);
            } else {
                throw new IllegalStateException("setDataSource() must called after reset()");
            }
            setState(State.Initialized);
        }
    }

    @Override
    public float getVolume() {
        return volume;
    }

    @Override
    public void setVolume(float volume) {
        this.volume = volume;
    }

    @Override
    public boolean isLooping() {
        return isLooping;
    }

    @Override
    public void setLooping(boolean looping) {
        this.isLooping = looping;
    }

    @Override
    public boolean isPlaying() throws IllegalStateException {
        if (getState() == State.Idle
                || getState() == State.Error
                || getState() == State.End) {
            throw new IllegalStateException("isPlaying() must called after setDataSource() before release() and there is no Error.");
        }
        return getState() == State.Started;
    }

    @Override
    public int getDuration() {
        return us2msI(durationUs);
    }

    @Override
    public int getCurrentPosition() {
        return us2msI(positionUs);
    }

    @Override
    public void start() throws IllegalStateException {
        synchronized (this) {
            boolean startDecode = false;
            if (getState() == State.Paused ||
                    (startDecode = (getState() == State.Prepared || getState() == State.Completed))) {
                if (startDecode) {
                    startDecodeThread();
                }
                startPlayThread();
            } else {
                throw new IllegalStateException("start() must called after prepare(), pause() or onPrepared() and there is no Error.");
            }
        }
    }

    @Override
    public void stop() throws IllegalStateException {
        synchronized (this) {
            if (getState() == State.Prepared
                    || getState() == State.Paused
                    || getState() == State.Started
                    || getState() == State.Completed) {
                setState(State.Stopped);
                stopPlayThread(true);
                stopDecodeThread(false);
            } else if (getState() == State.Stopped) {
                //do nothing
            } else {
                throw new IllegalStateException("stop() must called after prepare(), onPrepared(), start(), paused(), or onCompletion() and there is no Error.");
            }
        }
    }

    @Override
    public void pause() throws IllegalStateException {
        synchronized (this) {
            if (getState() == State.Started) {
                setState(State.Paused);
                stopPlayThread(false);
            } else {
                throw new IllegalStateException("pause() must called after start().");
            }
        }
    }

    @Override
    public void reStart() throws IllegalStateException, IOException {
        stop();
        prepare();
        start();
    }

    @Override
    public void prepare() throws IllegalStateException, IOException {
        synchronized (this) {
            if (getState() == State.Initialized
                    || getState() == State.Stopped) {

                if (positionEndUs == 0 || positionEndUs > durationUs) {
                    positionEndUs = durationUs;
                }
                int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);
                audioTrack = new AudioTrack(streamType, sampleRateInHz, channelConfig, audioFormat, bufferSizeInBytes, mode);
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
                    audioTrack.release();
                    audioTrack = null;
                    onError(PLAYER_ERROR_INITIALIZE, PLAYER_ERROR_AUDIOTRACK_INITIALIZE_FAILED);
                }
            } else {
                throw new IllegalStateException("prepare() must called after setDateSource() or stop()");
            }
            setState(State.Prepared);
        }
    }

    @Override
    public void prepareAsync() throws IllegalStateException {
        synchronized (this) {
            if (getState() == State.Initialized || getState() == State.Stopped) {
                setState(State.Preparing);
                executorService.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            prepare();
                            onPrepared();
                        } catch (IOException e) {
                            e.printStackTrace();
                            onError(PLAYER_ERROR_INITIALIZE, PLAYER_ERROR_AUDIOTRACK_INITIALIZE_FAILED);
                        }
                    }
                });

            } else {
                throw new IllegalStateException("prepareAsync() must called after setDateSource() or stop()");
            }
        }
    }

    @Override
    public void release() {
        synchronized (this) {
            setState(State.End);
            stopPlayThread(true);
            stopDecodeThread(true);
        }
        executorService.shutdown();
    }

    @Override
    public void reset() {
        synchronized (this) {
            setState(State.Idle);
            stopPlayThread(true);
            stopDecodeThread(true);
        }
    }

    @Override
    public void seekTo(int mSec) throws IllegalStateException {
        if (getState() == State.Prepared
                || getState() == State.Started
                || getState() == State.Paused
                || getState() == State.Completed) {
            audioDecoder.seekTo(mSec);
        } else {
            throw new IllegalStateException("seekTo() must called after prepared, started  paused.");
        }
    }

    @Override
    public void setPlayRange(int start, int end) {
        if (start > 0 && start < end) {
            positionStartUs = ms2usL(start);
        }
        if (end > 0 && start < end) {
            positionEndUs = ms2usL(end);
        }
        if (getState() == State.Prepared
                || getState() == State.Started
                || getState() == State.Paused
                || getState() == State.Completed) {
            if (positionEndUs > durationUs) {
                positionEndUs = durationUs;
            }
            if (positionStartUs > durationUs) {
                positionStartUs = 0;
            }
        }
    }

    @Override
    public void setOnDataProcessListener(OnDataProcessListener l) {
        this.onDataProcessListener = l;
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener l) {
        this.onCompletionListener = l;
    }

    @Override
    public void setOnErrorListener(OnErrorListener l) {
        this.onErrorListener = l;
    }

    @Override
    public void setOnPreparedListener(OnPreparedListener l) {
        this.onPreparedListener = l;
    }

    @Override
    public Integer call() throws Exception {
        boolean onCompletion = false;
        audioTrack.play();
        while (isPlaying()) {
            AudioBuffer audioBuffer = audioDecoder.getBuffer();
            if (null != audioBuffer) {
                //Log.d(TAG, "call: " + positionUs + "/" + positionEndUs);
                /*process audio*/
                onDataProcess(audioBuffer.buffer, audioBuffer.size / channels);
                /*volume*/
                for (int i = 0, data; i < audioBuffer.size; i++) {
                    data = (int) (audioBuffer.buffer[i] * volume);
                    if (data > 32767)
                        data = 32767;
                    else if (data < -32768)
                        data = -32768;
                    audioBuffer.buffer[i] = (short) data;
                }
                audioTrack.write(audioBuffer.buffer, 0, audioBuffer.size);
                positionUs = audioBuffer.presentationTimeUs;
                audioBuffer.recycle();
            } else {
                //Log.d(TAG, "call: null == audioBuffer");
                if (onCompletion = !isLooping() && audioDecoder.isEos) {
                    break;
                }
            }
        }
        audioTrack.stop();
        if (onCompletion) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    onCompletion();
                }
            });
        }
        return 0;
    }

    private void startDecodeThread() {
        audioDecoder.stop();
        audioDecoder.seekTo(positionUs = positionStartUs);
        audioDecoder.start();
    }

    private void stopDecodeThread(boolean release) {
        if (null == audioDecoder) {
            return;
        }
        audioDecoder.stop();
        if (release) {
            audioDecoder.release();
            audioDecoder = null;
        }
    }

    private void startPlayThread() {
        setState(State.Started);
        executorService.execute(futureTask = new FutureTask<>(this));
    }

    private void stopPlayThread(boolean release) {
        if (null != futureTask && !futureTask.isDone()) {
            try {
                futureTask.get(2, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                Log.e(TAG, "stop: TimeoutException");
                futureTask.cancel(true);
                e.printStackTrace();
            }
        }
        futureTask = null;
        if (release && null != audioTrack) {
            audioTrack.release();
            audioTrack = null;
        }
    }

    private void onError(int what, int extra) {
        setState(State.Error);
        if (null != onErrorListener) {
            onErrorListener.onError(this, what, extra);
        }
    }

    private void onPrepared() {
        setState(State.Prepared);
        if (null != onPreparedListener) {
            onPreparedListener.onPrepared(this);
        }
    }

    private void onCompletion() {
        setState(State.Completed);
        if (null != onCompletionListener) {
            onCompletionListener.onCompletion(this);
        }
    }

    private void onDataProcess(short[] data, int samples) {
        if (null != onDataProcessListener) {
            onDataProcessListener.onProcess(data, samples);
        }
    }

    private float getBufferDuration(int bufferSize) {
        return bufferSize * 1000 * 8 * 2.0f / sampleRateInHz / bitPerSample / channels;
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

    private int us2msI(long us) {
        return (int) (us / 1000);
    }

    private long ms2usL(int ms) {
        return ms * 1000;
    }


    @IntDef({State.Idle, State.Initialized, State.Preparing, State.Prepared,
            State.Started, State.Paused, State.Stopped, State.Completed,
            State.End, State.Error})
    @Retention(RetentionPolicy.SOURCE)
    @interface State {
        int Idle = 0;
        int Initialized = 1;
        int Preparing = 2;
        int Prepared = 3;
        int Started = 4;
        int Paused = 5;
        int Stopped = 6;
        int Completed = 7;
        int End = 8;
        int Error = 9;
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

    private class AudioDecoder implements Callable<Integer>, MediaCodecWrapper.OutputSampleListener {
        private static final int maxPoolSize = 300;
        private Queue<AudioBuffer> audioBuffers = new ArrayDeque<>();
        private int bufferIndex;
        private int writePtr;
        private AudioBuffer writeAudio;
        private boolean isEos;
        private boolean isSampleEos;
        private boolean isRunning;
        private MediaCodecWrapper mMediaCodecWrapper;
        private MediaExtractor mMediaExtractor;
        private MediaCodec.BufferInfo out_bufferInfo = new MediaCodec.BufferInfo();
        private Lock lock = new ReentrantLock();
        private Condition condition = lock.newCondition();
        private FutureTask<Integer> futureTask;

        void setDataSource(String audioPath, boolean isEncoded) throws IOException {
            mMediaExtractor = new MediaExtractor();
            mMediaExtractor.setDataSource(audioPath);
            for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
                MediaFormat trackFormat = mMediaExtractor.getTrackFormat(i);
                String mMediaType = trackFormat.getString(MediaFormat.KEY_MIME);
                if (mMediaType.startsWith("audio/")) {
                    Log.d(TAG, "MediaFormat: " + trackFormat);
                    mMediaExtractor.selectTrack(i);
                    sampleRateInHz = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    channels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    durationUs = trackFormat.getLong(MediaFormat.KEY_DURATION);
                    mMediaCodecWrapper = MediaCodecWrapper.fromAudioFormat(trackFormat);
                    mMediaCodecWrapper.setOutputSampleListener(audioDecoder);
                    break;
                }
            }
        }

        AudioBuffer getBuffer() {
//            AudioBuffer audio;
//            while (true) {
//                audio = audioBuffers.poll();
//                if (null != audio && (audio.presentationTimeUs < positionStartUs)) {
//                    Log.d(TAG, "skip: " + audio.presentationTimeUs + " , " + positionEndUs + "/" + positionStartUs);
//                    audio.recycle();
//                } else {
//                    break;
//                }
//            }
            AudioBuffer audio = audioBuffers.poll();
            if (audioBuffers.size() <= maxPoolSize / 2) {
                lock.lock();
                condition.signalAll();
                lock.unlock();
            }
            return audio;
        }

        void start() {
            synchronized (this) {
                isRunning = true;
                isSampleEos = isEos = false;
                executorService.execute(futureTask = new FutureTask<>(this));
            }
        }

        void seekTo(long timeUs) {
            if (null != mMediaExtractor) {
                mMediaExtractor.seekTo(timeUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            }
        }

        void stop() {
            synchronized (this) {
                isRunning = false;
                lock.lock();
                condition.signalAll();
                lock.unlock();
                if (null != futureTask && !futureTask.isDone()) {
                    try {
                        futureTask.get(2, TimeUnit.SECONDS);
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    } catch (TimeoutException e) {
                        futureTask.cancel(true);
                        e.printStackTrace();
                    }
                }
                futureTask = null;
                for (AudioBuffer audioBuffer : audioBuffers) {
                    audioBuffer.recycle();
                }
                audioBuffers.clear();
            }
        }

        void release() {
            synchronized (this) {
                mMediaCodecWrapper.stopAndRelease();
                mMediaExtractor.release();
                mMediaExtractor = null;
            }
        }

        @Override
        public Integer call() throws Exception {
            while (isRunning && !isEos) {
                if (audioBuffers.size() >= maxPoolSize) {
                    lock.lock();
                    try {
                        //long t1 = System.currentTimeMillis();
                        condition.await();
                        if (isEos) {
                            return -2;
                        }
                        //Log.d(TAG, "decoder run interval: " + (System.currentTimeMillis() - t1));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return -1;
                    } finally {
                        lock.unlock();
                    }
                }
                if (!(isSampleEos = MediaCodec.BUFFER_FLAG_END_OF_STREAM == (mMediaExtractor.getSampleFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) || isSampleEos)
                        && mMediaCodecWrapper.writeSample(mMediaExtractor, false, mMediaExtractor.getSampleTime(), mMediaExtractor.getSampleFlags())) {
                    mMediaExtractor.advance();
                }
                if (mMediaExtractor.getSampleTime() >= positionEndUs || isSampleEos) {
                    if (!(isSampleEos = !isLooping())) {
                        seekTo(positionStartUs);
                    }
                }
                out_bufferInfo.set(0, 0, 0, 0);
                mMediaCodecWrapper.peekSample(out_bufferInfo);
                if (!(isEos = (out_bufferInfo.size <= 0 && isSampleEos))) {
                    mMediaCodecWrapper.popSample();
                }
            }
            isRunning = false;
            return 0;
        }

        @Override
        public void outputSample(MediaCodecWrapper sender, MediaCodec.BufferInfo info, ByteBuffer buffer) {
            if (null == buffer || null == info) {
                return;
            }
            buffer.position(info.offset);
            buffer.limit(info.offset + info.size);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < info.size; i += 2) {
                if (null == writeAudio) {
                    writeAudio = AudioBuffer.obtain();
                    writeAudio.index = bufferIndex++;
                    writeAudio.presentationTimeUs = info.presentationTimeUs;
                }
                if (channels == 2) {
                    writeAudio.buffer[writePtr] = buffer.getShort(i);
                    writePtr = (writePtr + 1) % writeAudio.size;
                } else {
                    writeAudio.buffer[writePtr + 1] = writeAudio.buffer[writePtr] = buffer.getShort(i);
                    writePtr = (writePtr + 2) % writeAudio.size;
                }
                if (0 == writePtr && null != writeAudio) {
                    writeAudio.duration = getBufferDuration(writeAudio.size);
                    audioBuffers.add(writeAudio);
                    //Log.d(TAG, "outputSample: " + writeAudio.presentationTimeUs);
                    writeAudio = null;
                }
            }
            buffer.clear();
        }
    }
}