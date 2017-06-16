package com.bmapleaf.audio.component;

import android.support.annotation.IntDef;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Created by ZhangMing on 2017/05/09.
 */

public interface IRecorder {
    /**
     * Unknown error,default
     */
    int RECORDER_ERROR_UNKNOWN = -100;
    /**
     * Error occurs while prepare
     */
    int RECORDER_ERROR_INITIALIZED = -200;
    /**
     * The channel set by setAudioChannels is not supported
     */
    int RECORDER_ERROR_CHANNEL_NOT_SUPPORT = -201;
    /**
     * The sample rate set by setAudioSamplingRate is not supported
     */
    int RECORDER_ERROR_SAMPLE_RATE_NOT_SUPPORT = -202;

    /**
     * Pauses recording.Call resume() to resume.
     *
     * @throws IllegalStateException if it is called before start() or after stop()
     */
    void pause() throws IllegalStateException;

    /**
     * Prepares the Recorder to begin capturing (and encoding) data.
     *
     * @throws IllegalStateException if it is called after start().
     * @throws IOException           if prepare fails otherwise.
     */
    void prepare() throws IllegalStateException, IOException;

    /**
     * Releases resources associated with this Recorder object.
     */
    void release();

    /**
     * Restarts the Recorder to its idle state.
     */
    void reset();

    /**
     * Resumes recording.
     *
     * @throws IllegalStateException if it is called not after pause().
     */
    void resume() throws IllegalStateException;

    /**
     * Stops recording.
     *
     * @throws IllegalStateException if it is called before start().
     */
    void stop() throws IllegalStateException;

    /**
     * Begins capturing (and encoding) data to the file specified with setOutputFile().
     *
     * @throws IllegalStateException if it is called before prepare().
     */
    void start() throws IllegalStateException;

    /**
     * restart recording.
     */
    void reStart();

    /**
     * Sets the number of audio channels for recording.
     *
     * @param numChannels the number of audio channels. Usually it is either 1 (mono) or 2 (stereo).
     */
    void setAudioChannels(@Channel int numChannels);

    /**
     * Sets the audio sampling rate for recording.
     *
     * @param samplingRate the sampling rate for audio in samples per second.
     */
    void setAudioSamplingRate(int samplingRate);

    /**
     * Sets the path of the output file to be produced. Call this before prepare().
     *
     * @param file   The pathname to use.
     * @param encode whether to encode data while save record or not(aac or pcm)
     * @throws IllegalStateException if it is called after prepare().
     * @throws IOException           if the file path is invalid.
     */
    void setOutputFile(String file, boolean encode) throws IllegalStateException, IOException;

    /**
     * Register a callback to be invoked when an error occurs while recording.
     *
     * @param l the callback that will be run
     */
    void setOnErrorListener(OnErrorListener l);

    /**
     * Register a callback to be invoked when the record data is ready for process(eg.GetSampleInfo,VocalEffects).
     *
     * @param l the callback that will be run
     */
    void setOnDataProcessListener(OnDataProcessListener l);

    /**
     * Interface definition for a callback to be invoked when an error occurs while recording.
     */
    interface OnErrorListener {
        /**
         * Called when an error occurs while recording.
         *
         * @param r     the Recorder that encountered the error
         * @param what  the type of error that has occurred:
         *              RECORDER_ERROR_UNKNOWN
         *              RECORDER_ERROR_INITIALIZE
         * @param extra an extra code, specific to the error type
         *              RECORDER_ERROR_CHANNEL_NOT_SUPPORT
         *              RECORDER_ERROR_SAMPLE_RATE_NOT_SUPPORT
         *              RECORDER_ERROR_INVALID_OUTPUT_FILE
         */
        void onError(IRecorder r, int what, int extra);
    }

    /**
     * Interface definition for a callback to be invoked when the record data is ready for process(eg.GetSampleInfo,VocalEffects).
     */
    interface OnDataProcessListener {
        /**
         * Called to process the data(eg.GetSampleInfo,VocalEffects).
         *
         * @param data    the data to be process
         * @param samples number of samples.
         */
        void onProcess(short[] data, int samples);
    }

    @IntDef({Channel.MONO, Channel.STEREO})
    @Retention(RetentionPolicy.SOURCE)
    @interface Channel {
        int MONO = 1;
        int STEREO = 2;
    }
}
