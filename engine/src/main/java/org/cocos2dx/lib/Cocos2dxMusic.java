package org.cocos2dx.lib;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.FileInputStream;

public class Cocos2dxMusic {
    private static final String TAG = "Cocos2dxMusic";
    private MediaPlayer mBackgroundMediaPlayer;
    private final Context mContext;
    private String mCurrentPath;
    private float mLeftVolume;
    private boolean mPaused;
    private float mRightVolume;
    private boolean mIsLoop = false;
    private boolean mManualPaused = false;

    public Cocos2dxMusic(Context context) {
        this.mContext = context;
        initData();
    }

    private MediaPlayer createMediaplayer(String path) {
        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            if (path.startsWith("/")) {
                FileInputStream fileInputStream = new FileInputStream(path);
                mediaPlayer.setDataSource(fileInputStream.getFD());
                fileInputStream.close();
            } else {
                AssetFileDescriptor afd = this.mContext.getAssets().openFd(path);
                mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            }
            mediaPlayer.prepare();
            mediaPlayer.setVolume(this.mLeftVolume, this.mRightVolume);
            return mediaPlayer;
        } catch (Exception e) {
            Log.e(TAG, "error: " + e.getMessage(), e);
            return null;
        }
    }

    private void initData() {
        this.mLeftVolume = 0.5f;
        this.mRightVolume = 0.5f;
        this.mBackgroundMediaPlayer = null;
        this.mPaused = false;
        this.mCurrentPath = null;
    }

    public void end() {
        MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
        if (mediaPlayer != null) mediaPlayer.release();
        initData();
    }

    public float getBackgroundVolume() {
        if (this.mBackgroundMediaPlayer != null) return (this.mLeftVolume + this.mRightVolume) / 2.0f;
        return 0.0f;
    }

    public boolean isBackgroundMusicPlaying() {
        MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void onEnterBackground() {
        MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
        if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;
        this.mBackgroundMediaPlayer.pause();
        this.mPaused = true;
    }

    public void onEnterForeground() {
        MediaPlayer mediaPlayer;
        if (this.mManualPaused || (mediaPlayer = this.mBackgroundMediaPlayer) == null || !this.mPaused) return;
        mediaPlayer.start();
        this.mPaused = false;
    }

    public void pauseBackgroundMusic() {
        MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
        if (mediaPlayer == null || !mediaPlayer.isPlaying()) return;
        this.mBackgroundMediaPlayer.pause();
        this.mPaused = true;
        this.mManualPaused = true;
    }

    public void playBackgroundMusic(String path, boolean loop) {
        String currentPath = this.mCurrentPath;
        if (currentPath == null) {
            this.mBackgroundMediaPlayer = createMediaplayer(path);
            this.mCurrentPath = path;
        } else if (!currentPath.equals(path)) {
            MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
            if (mediaPlayer != null) mediaPlayer.release();
            this.mBackgroundMediaPlayer = createMediaplayer(path);
            this.mCurrentPath = path;
        }
        MediaPlayer player = this.mBackgroundMediaPlayer;
        if (player == null) {
            Log.e(TAG, "playBackgroundMusic: background media player is null");
            return;
        }
        try {
            if (this.mPaused) {
                player.seekTo(0);
                this.mBackgroundMediaPlayer.start();
            } else if (player.isPlaying()) {
                this.mBackgroundMediaPlayer.seekTo(0);
            } else {
                this.mBackgroundMediaPlayer.start();
            }
            this.mBackgroundMediaPlayer.setLooping(loop);
            this.mPaused = false;
            this.mIsLoop = loop;
        } catch (Exception unused) {
            Log.e(TAG, "playBackgroundMusic: error state");
        }
    }

    public void preloadBackgroundMusic(String path) {
        String currentPath = this.mCurrentPath;
        if (currentPath == null || !currentPath.equals(path)) {
            MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
            if (mediaPlayer != null) mediaPlayer.release();
            this.mBackgroundMediaPlayer = createMediaplayer(path);
            this.mCurrentPath = path;
        }
    }

    public void resumeBackgroundMusic() {
        MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
        if (mediaPlayer == null || !this.mPaused) return;
        mediaPlayer.start();
        this.mPaused = false;
        this.mManualPaused = false;
    }

    public void rewindBackgroundMusic() {
        if (this.mBackgroundMediaPlayer != null) playBackgroundMusic(this.mCurrentPath, this.mIsLoop);
    }

    public void setBackgroundVolume(float volume) {
        if (volume < 0.0f) volume = 0.0f;
        if (volume > 1.0f) volume = 1.0f;
        this.mRightVolume = volume;
        this.mLeftVolume = volume;
        MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
        if (mediaPlayer != null) mediaPlayer.setVolume(volume, volume);
    }

    public void stopBackgroundMusic() {
        MediaPlayer mediaPlayer = this.mBackgroundMediaPlayer;
        if (mediaPlayer != null) {
            mediaPlayer.release();
            this.mBackgroundMediaPlayer = createMediaplayer(this.mCurrentPath);
            this.mPaused = false;
        }
    }
}