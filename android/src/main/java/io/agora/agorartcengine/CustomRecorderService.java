package io.agora.agorartcengine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class CustomRecorderService extends Service {
    private static final String TAG = CustomRecorderService.class.getSimpleName();
    private static final int AMPLITUDE_THRESHOLD = 1500;
    private static final int SPEECH_TIMEOUT_MILLIS = 2000;
    private static final int MAX_SPEECH_LENGTH_MILLIS = 30 * 1000;

    private RecordThread mThread;
    private volatile boolean mStopped;
    private Callback mCallback;

    private final CustomRecorderBinder mBinder = new CustomRecorderBinder();

    public static abstract class Callback {

        /**
         * Called when the recorder starts hearing voice.
         */
        public void onVoiceStart(int sampleRates) {
        }

        /**
         * Called when the recorder is hearing voice.
         *
         * @param data The audio data in {@link AudioFormat#ENCODING_PCM_16BIT}.
         * @param size The size of the actual data in {@code data}.
         */
        public void onVoice(byte[] data, int size) {
        }

        /**
         * Called when the recorder stops hearing voice.
         */
        public void onVoiceEnd() {
        }
    }

    /** The timestamp of the last time that voice is heard. */
    private long mLastVoiceHeardMillis = Long.MAX_VALUE;

    /** The timestamp when the current voice is started. */
    private long mVoiceStartedMillis;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground();
        startRecording();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground();
        startRecording();
        return Service.START_STICKY;
    }

    public void setVoiceCallback(Callback callback) {
        mCallback = callback;
    }

    public void removeVoiceCallback() {
        mCallback = null;
    }

    private void startForeground() {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, TAG)
                .setContentTitle(TAG)
                .build();

        startForeground(1, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    TAG, TAG, NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void startRecording() {
        mThread = new RecordThread();
        mThread.start();
    }

    private void stopRecording() {
        mStopped = true;
    }

    // Speech to text
    /**
     * Dismisses the currently ongoing utterance.
     */
    public void dismiss() {
        if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }
    }


    @Override
    public void onDestroy() {
        stopRecording();
        super.onDestroy();
    }

    private class RecordThread extends Thread {
        private CustomRecorder mRecorder;
        private byte[] mBuffer;
        CustomRecorderConfig mConfig;

        RecordThread() {
            mRecorder = new CustomRecorder();
            mConfig = mRecorder.getConfig();
            mBuffer = new byte[mConfig.getBufferSize()];
        }

        @Override
        public void run() {
            mRecorder.start();
            while (!mStopped) {
                int result = mRecorder.read(mBuffer, 0, mBuffer.length);
                final long now = System.currentTimeMillis();
                if (result >= 0) {
                    AgoraRtcEnginePlugin.getRtcEngine().pushExternalAudioFrame(
                            mBuffer, System.currentTimeMillis());
                } else {
                    logRecordError(result);
                }
                if (isHearingVoice(mBuffer, result)) {
                    if (mLastVoiceHeardMillis == Long.MAX_VALUE) {
                        mVoiceStartedMillis = now;
                        mCallback.onVoiceStart(mConfig.getSampleRate());
                    }
                    mCallback.onVoice(mBuffer, result);
                    mLastVoiceHeardMillis = now;
                    if (now - mVoiceStartedMillis > MAX_SPEECH_LENGTH_MILLIS) {
                        end();
                    }
                } else if (mLastVoiceHeardMillis != Long.MAX_VALUE) {
                    mCallback.onVoice(mBuffer, result);
                    if (now - mLastVoiceHeardMillis > SPEECH_TIMEOUT_MILLIS) {
                        end();
                    }
                }
            }
            release();
        }

        private void logRecordError(int error) {
            String message = "";
            switch (error) {
                case AudioRecord.ERROR:
                    message = "generic operation failure";
                    break;
                case AudioRecord.ERROR_BAD_VALUE:
                    message = "failure due to the use of an invalid value";
                    break;
                case AudioRecord.ERROR_DEAD_OBJECT:
                    message = "object is no longer valid and needs to be recreated";
                    break;
                case AudioRecord.ERROR_INVALID_OPERATION:
                    message = "failure due to the improper use of method";
                    break;
            }
            Log.e(TAG, message);
        }

        private void release() {
            if (mRecorder != null) {
                mRecorder.stop();
                mBuffer = null;
            }
        }

        private void end() {
            mLastVoiceHeardMillis = Long.MAX_VALUE;
            mCallback.onVoiceEnd();
        }

        private boolean isHearingVoice(byte[] buffer, int size) {
            for (int i = 0; i < size - 1; i += 2) {
                // The buffer has LINEAR16 in little endian.
                int s = buffer[i + 1];
                if (s < 0) s *= -1;
                s <<= 8;
                s += Math.abs(buffer[i]);
                if (s > AMPLITUDE_THRESHOLD) {
                    return true;
                }
            }
            return false;
        }
    }

    public static CustomRecorderService from(IBinder binder) {
        return ((CustomRecorderBinder) binder).getService();
    }

    private class CustomRecorderBinder extends Binder {

        CustomRecorderService getService() {
            return CustomRecorderService.this;
        }
    }
}
