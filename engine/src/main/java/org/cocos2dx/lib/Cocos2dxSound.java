package org.cocos2dx.lib;

import android.content.Context;
import android.media.SoundPool;
import android.util.Log;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Cocos2dxSound {
    private static final int INVALID_SOUND_ID = -1;
    private static final int LOAD_TIME_OUT = 500;
    private static final float SOUND_RATE = 1.0f;
    private static final String TAG = "Cocos2dxSound";

    private final Context mContext;
    private float mLeftVolume;
    private float mRightVolume;
    private SoundPool mSoundPool;
    private final HashMap<String, ArrayList<Integer>> mPathStreamIDsMap = new HashMap<>();
    private final HashMap<String, Integer> mPathSoundIDMap = new HashMap<>();
    private final ConcurrentHashMap<Integer, SoundInfoForLoadedCompleted> mPlayWhenLoadedEffects = new ConcurrentHashMap<>();

    public class OnLoadCompletedListener implements SoundPool.OnLoadCompleteListener {
        public OnLoadCompletedListener() { }
        @Override public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
            SoundInfoForLoadedCompleted info;
            if (status != 0 || (info = mPlayWhenLoadedEffects.get(Integer.valueOf(sampleId))) == null) return;
            info.effectID = doPlayEffect(info.path, sampleId, info.isLoop, info.pitch, info.pan, info.gain);
            synchronized (info) { info.notifyAll(); }
        }
    }

    public class SoundInfoForLoadedCompleted {
        public int effectID = -1;
        public float gain;
        public boolean isLoop;
        public float pan;
        public String path;
        public float pitch;
        public SoundInfoForLoadedCompleted(String path, boolean loop, float pitch, float pan, float gain) {
            this.path = path;
            this.isLoop = loop;
            this.pitch = pitch;
            this.pan = pan;
            this.gain = gain;
        }
    }

    public Cocos2dxSound(Context context) {
        this.mContext = context;
        initData();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }

    private int doPlayEffect(String path, int soundId, boolean loop, float pitch, float pan, float gain) {
        float left = (SOUND_RATE - clamp(pan, 0.0f, SOUND_RATE)) * this.mLeftVolume * gain;
        float right = (SOUND_RATE - clamp(-pan, 0.0f, SOUND_RATE)) * this.mRightVolume * gain;
        int streamId = this.mSoundPool.play(soundId, clamp(left, 0.0f, SOUND_RATE), clamp(right, 0.0f, SOUND_RATE), 1, loop ? -1 : 0, clamp(pitch * SOUND_RATE, 0.5f, 2.0f));
        ArrayList<Integer> streamIds = this.mPathStreamIDsMap.get(path);
        if (streamIds == null) {
            streamIds = new ArrayList<>();
            this.mPathStreamIDsMap.put(path, streamIds);
        }
        streamIds.add(Integer.valueOf(streamId));
        return streamId;
    }

    private void initData() {
        this.mSoundPool = new SoundPool(5, 3, 5);
        this.mSoundPool.setOnLoadCompleteListener(new OnLoadCompletedListener());
        this.mLeftVolume = 0.5f;
        this.mRightVolume = 0.5f;
    }

    public int createSoundIDFromAsset(String path) {
        int loaded;
        try {
            loaded = path.startsWith("/") ? this.mSoundPool.load(path, 0) : this.mSoundPool.load(this.mContext.getAssets().openFd(path), 0);
        } catch (Exception e) {
            Log.e(TAG, "error: " + e.getMessage(), e);
            loaded = -1;
        }
        return loaded == 0 ? -1 : loaded;
    }

    public void end() {
        this.mSoundPool.release();
        this.mPathStreamIDsMap.clear();
        this.mPathSoundIDMap.clear();
        this.mPlayWhenLoadedEffects.clear();
        this.mLeftVolume = 0.5f;
        this.mRightVolume = 0.5f;
        initData();
    }

    public float getEffectsVolume() { return (this.mLeftVolume + this.mRightVolume) / 2.0f; }
    public void onEnterBackground() { this.mSoundPool.autoPause(); }
    public void onEnterForeground() { this.mSoundPool.autoResume(); }

    public void pauseAllEffects() {
        if (this.mPathStreamIDsMap.isEmpty()) return;
        for (Map.Entry<String, ArrayList<Integer>> entry : this.mPathStreamIDsMap.entrySet()) {
            for (Integer streamId : entry.getValue()) this.mSoundPool.pause(streamId.intValue());
        }
    }

    public void pauseEffect(int streamId) { this.mSoundPool.pause(streamId); }

    public int playEffect(String path, boolean loop, float pitch, float pan, float gain) {
        Integer soundId = this.mPathSoundIDMap.get(path);
        if (soundId != null) return doPlayEffect(path, soundId.intValue(), loop, pitch, pan, gain);
        int preload = preloadEffect(path);
        Integer preloadId = Integer.valueOf(preload);
        if (preload == -1) return -1;
        SoundInfoForLoadedCompleted info = new SoundInfoForLoadedCompleted(path, loop, pitch, pan, gain);
        this.mPlayWhenLoadedEffects.putIfAbsent(preloadId, info);
        synchronized (info) {
            try { info.wait(LOAD_TIME_OUT); } catch (Exception e) { e.printStackTrace(); }
        }
        int effectID = info.effectID;
        this.mPlayWhenLoadedEffects.remove(preloadId);
        return effectID;
    }

    public int preloadEffect(String path) {
        Integer soundId = this.mPathSoundIDMap.get(path);
        if (soundId == null) {
            int loaded = createSoundIDFromAsset(path);
            Integer id = Integer.valueOf(loaded);
            if (loaded != -1) this.mPathSoundIDMap.put(path, id);
            soundId = id;
        }
        return soundId.intValue();
    }

    public void resumeAllEffects() {
        if (this.mPathStreamIDsMap.isEmpty()) return;
        for (Map.Entry<String, ArrayList<Integer>> entry : this.mPathStreamIDsMap.entrySet()) {
            for (Integer streamId : entry.getValue()) this.mSoundPool.resume(streamId.intValue());
        }
    }

    public void resumeEffect(int streamId) { this.mSoundPool.resume(streamId); }

    public void setEffectsVolume(float volume) {
        if (volume < 0.0f) volume = 0.0f;
        if (volume > SOUND_RATE) volume = SOUND_RATE;
        this.mRightVolume = volume;
        this.mLeftVolume = volume;
        if (this.mPathStreamIDsMap.isEmpty()) return;
        for (Map.Entry<String, ArrayList<Integer>> entry : this.mPathStreamIDsMap.entrySet()) {
            for (Integer streamId : entry.getValue()) this.mSoundPool.setVolume(streamId.intValue(), this.mLeftVolume, this.mRightVolume);
        }
    }

    public void stopAllEffects() {
        if (!this.mPathStreamIDsMap.isEmpty()) {
            for (Map.Entry<String, ArrayList<Integer>> entry : this.mPathStreamIDsMap.entrySet()) {
                for (Integer streamId : entry.getValue()) this.mSoundPool.stop(streamId.intValue());
            }
        }
        this.mPathStreamIDsMap.clear();
    }

    public void stopEffect(int streamId) {
        this.mSoundPool.stop(streamId);
        for (String path : this.mPathStreamIDsMap.keySet()) {
            if (this.mPathStreamIDsMap.get(path).contains(Integer.valueOf(streamId))) {
                this.mPathStreamIDsMap.get(path).remove(this.mPathStreamIDsMap.get(path).indexOf(Integer.valueOf(streamId)));
                return;
            }
        }
    }

    public void unloadEffect(String path) {
        ArrayList<Integer> streamIds = this.mPathStreamIDsMap.get(path);
        if (streamIds != null) {
            Iterator<Integer> it = streamIds.iterator();
            while (it.hasNext()) this.mSoundPool.stop(it.next().intValue());
        }
        this.mPathStreamIDsMap.remove(path);
        Integer soundId = this.mPathSoundIDMap.get(path);
        if (soundId != null) {
            this.mSoundPool.unload(soundId.intValue());
            this.mPathSoundIDMap.remove(path);
        }
    }
}