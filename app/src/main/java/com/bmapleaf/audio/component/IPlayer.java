package com.bmapleaf.audio.component;

import java.io.IOException;

/**
 * Created by ZhangMing on 2017/05/09.
 */

public interface IPlayer {
    /**
     * Unknown error,default
     */
    int PLAYER_ERROR_UNKNOWN = -100;
    /**
     * Error occurs while prepare
     */
    int PLAYER_ERROR_INITIALIZE = -200;
    /**
     * The data source path set by setDataSource is invalid
     */
    int PLAYER_ERROR_DATA_SOURCE_INVALID = -201;
    /**
     * The audioTrack initialize failed
     */
    int PLAYER_ERROR_AUDIOTRACK_INITIALIZE_FAILED = -202;

    /**
     * Sets the data source to use.
     *
     * @param audioPath media file path
     * @param isEncoded whether the media file is encoded or not(a pcm file or mp3/mp4...)
     * @throws IllegalStateException if it is called in an invalid state(after prepare()).
     * @throws IOException           the path is invalid.
     */
    void setDataSource(String audioPath, boolean isEncoded) throws IllegalStateException, IOException;

    /**
     * Sets the volume on this player.
     *
     * @param volume volume scalar
     */
    void setVolume(float volume);

    /**
     * get the volume of this player
     *
     * @return volume scalar
     */
    float getVolume();

    /**
     * Checks whether the Player is looping or non-looping.
     *
     * @return true if the Player is currently looping, false otherwise
     */
    boolean isLooping();

    /**
     * Sets the player to be looping or non-looping.
     *
     * @param looping whether to loop or not
     */
    void setLooping(boolean looping);

    /**
     * Checks whether the Player is playing.
     *
     * @return true if currently playing, false otherwise
     * @throws IllegalStateException if the player has not been initialized or has been released.
     */
    boolean isPlaying() throws IllegalStateException;

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds.
     */
    int getDuration();

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    int getCurrentPosition();

    /**
     * Starts or resumes playback.
     *
     * @throws IllegalStateException if it is called in an invalid state(before prepare()).
     */
    void start() throws IllegalStateException;

    /**
     * Stops playback after playback has been stopped or paused.
     *
     * @throws IllegalStateException if the player has not been initialized.
     */
    void stop() throws IllegalStateException;

    /**
     * Pauses playback. Call start() to resume.
     *
     * @throws IllegalStateException if the player has not been initialized.
     */
    void pause() throws IllegalStateException;

    /**
     * restart play.
     */
    void reStart() throws IllegalStateException, IOException;

    /**
     * Prepares the player for playback, synchronously.
     *
     * @throws IllegalStateException if it is called in an invalid state(after start()).
     * @throws IOException           if prepare fails otherwise.
     */
    void prepare() throws IllegalStateException, IOException;

    /**
     * Prepares the player for playback, asynchronously.
     *
     * @throws IllegalStateException if it is called in an invalid state
     */
    void prepareAsync() throws IllegalStateException;

    /**
     * Releases resources associated with this Player object.
     */
    void release();

    /**
     * Resets the Player to its uninitialized state.
     */
    void reset();

    /**
     * Seeks to specified time position.
     *
     * @param mSec the offset in milliseconds from the start to seek to
     * @throws IllegalStateException if the player has not been initialized
     */
    void seekTo(int mSec) throws IllegalStateException;

    /**
     * Set the player play from start to end.
     *
     * @param start the start time in milliseconds, negative will be ignored
     * @param end   the end time in milliseconds, negative will be ignored
     * @throws IllegalStateException if the player has not been prepared
     */
    void setPlayRange(int start, int end) throws IllegalStateException;

    /**
     * Register a callback to be invoked when the record data is ready for process(eg.PitchShift).
     *
     * @param l the callback that will be run
     */
    void setOnDataProcessListener(OnDataProcessListener l);

    /**
     * Register a callback to be invoked when the end of a media source has been reached during playback.
     *
     * @param l the callback that will be run
     */
    void setOnCompletionListener(OnCompletionListener l);

    /**
     * Register a callback to be invoked when an error has happened during an asynchronous operation.
     *
     * @param l the callback that will be run
     */
    void setOnErrorListener(OnErrorListener l);

    /**
     * Register a callback to be invoked when the media source is ready for playback.
     *
     * @param l the callback that will be run
     */
    void setOnPreparedListener(OnPreparedListener l);

    /**
     * Interface definition for a callback to be invoked when playback of a media source has completed.
     */
    interface OnCompletionListener {
        /**
         * Called when the end of a media source is reached during playback.
         *
         * @param p the Player that reached the end of the file
         */
        void onCompletion(IPlayer p);
    }

    /**
     * Interface definition for a callback to be invoked when the media source is ready for playback.
     */
    interface OnPreparedListener {
        /**
         * Called when the media file is ready for playback.
         *
         * @param p the Player that is ready for playback
         */
        void onPrepared(IPlayer p);
    }

    /**
     * Interface definition of a callback to be invoked when there has been an error during an asynchronous operation (other errors will throw exceptions at method call time).
     */
    interface OnErrorListener {
        /**
         * Called to indicate an error.
         *
         * @param p     the Player the error pertains to
         * @param what  the type of error that has occurred:
         *              PLAYER_ERROR_UNKNOWN
         *              PLAYER_ERROR_INITIALIZE
         * @param extra an extra code, specific to the error. Typically implementation dependent.
         *              PLAYER_ERROR_DATA_SOURCE_INVALID
         */
        void onError(IPlayer p, int what, int extra);
    }

    /**
     * Interface definition for a callback to be invoked when the media data is ready for process(eg.PitchShift).
     */
    interface OnDataProcessListener {
        /**
         * Called to process the data(eg.PitchShift).
         *
         * @param data    the data to be process
         * @param samples number of samples.
         */
        void onProcess(short[] data, int samples);
    }
}
