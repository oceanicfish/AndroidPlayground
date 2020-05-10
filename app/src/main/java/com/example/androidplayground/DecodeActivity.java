package com.example.androidplayground;

import androidx.appcompat.app.AppCompatActivity;

import android.icu.util.LocaleData;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;

public class DecodeActivity extends AppCompatActivity {

    /**
     * the waiting time for MediaCodec's process.
     * 1) - if the waiting time is 0, return immediately.
     * 2) - if the waiting time is -1, waiting indefinitely until mediacodec return a index.
     * 3) - if the waiting time is greater than 0, waiting for specified time.
     *      after the waiting time. if there is no any response comes from MediaCodec,
     *      that would be treated as timeout.
     */
//    private static final long WAITING_TIME = 10000;
    private static final long WAITING_TIME = -1;
    private String TAG = "DecodeActivity";
    private static final String SAMPLE = "/sdcard/Download/sample.mp4";
    private PlayerThread mediaCodecPlayer = null;
    private AudioThread audioPlayer = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_decode);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.decode_surface_view);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
//                if (mediaCodecPlayer == null) {
//                    mediaCodecPlayer = new PlayerThread(holder.getSurface());
//                    mediaCodecPlayer.start();
//                }

                if (audioPlayer == null) {
                    audioPlayer = new AudioThread();
                    audioPlayer.start();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
//                if (mediaCodecPlayer != null) {
//                    mediaCodecPlayer.interrupt();
//                }
                if (audioPlayer != null) {
                    audioPlayer.interrupt();
                }
            }
        });

    }

    private class PlayerThread extends Thread {
        private MediaExtractor mediaExtractor;
        private MediaCodec mediaCodec;
        private Surface surface;

        public PlayerThread(Surface surface) {
            this.surface = surface;
        }

        @Override
        public void run() {
            mediaExtractor = new MediaExtractor();
            try {
                mediaExtractor.setDataSource(SAMPLE);

                /**
                 * setup media codec with the media format of given media.
                 */
                for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                    MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
                    String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("video/")) {
                        mediaExtractor.selectTrack(i);
                        mediaCodec = MediaCodec.createDecoderByType(mime);
                        mediaCodec.configure(mediaFormat, this.surface, null, 0);
                        break;
                    }
                }

            } catch (IOException e) {
                Log.e(TAG, "audio recording failed.");
                e.printStackTrace();
            }

            /**
             * if decoder prepared, get started.
             */
            if (mediaCodec == null) {
                Log.e(TAG, "Can't find video info");
                return;
            }

            mediaCodec.start();

            /**
             * Buffer handling starts
             */
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            boolean isEOS = false;
            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    /**
                     *  get an index of data loading area
                     *  (a data loading area would be a ByteBuffer object)
                     */
                    int inIndex =
                            mediaCodec.dequeueInputBuffer(WAITING_TIME);
                    ByteBuffer bufferIn = inputBuffers[inIndex];

                    /**
                     *  if the index of data loading area is greater than 0, start loading
                     */
                    if (inIndex >= 0) {
                        /**
                         * the bufferSize is meaning how much buffer has been read by
                         * mediaExtractor. if the bufferSize is less than 0, that means
                         * the media has reached the end.
                         */
                        int bufferSize = mediaExtractor.readSampleData(bufferIn, 0);
                        if (bufferSize < 0) {
                            Log.e(TAG, "<BUFFER_FLAG_END_OF_STREAM> " +
                                    "input buffer has reached the end.");
                            mediaCodec.queueInputBuffer(inIndex, 0, 0,
                                    0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }else {
                            /**
                             * There're 2 issues need to be cleared later on...
                             * 1) - what is the use of parameter <presentationTimeUs>
                             *      that is the forth parameter of queueInputBuffer.
                             * 2) - the parameter <flags>, which constant(flag) of
                             *      MediaCodec use [0] as its value.
                             */
                            mediaCodec.queueInputBuffer(inIndex, 0, bufferSize,
                                    mediaExtractor.getSampleTime(), 0);
                            mediaExtractor.advance();
                        }
                    }

                }

                /**
                 * we start the output process here
                 */
                int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo,
                        WAITING_TIME);
                switch (outIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED :
                        Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                        outputBuffers = mediaCodec.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED :
                        Log.d(TAG,
                                "INFO_OUTPUT_FORMAT_CHANGED, new format : " +
                                        mediaCodec.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER :
                        Log.d(TAG, "dequeueOutputBuffer time out, please try " +
                                "later");
                        break;
                    default :
                        ByteBuffer bufferOut = outputBuffers[outIndex];
                        Log.v(TAG, "Buffer for rendering : " + bufferOut);

                        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
                            try {
                                sleep(10);
                            }catch (InterruptedException e) {
                                Log.v(TAG,
                                        "playback speed control failed.");
                                e.printStackTrace();
                                break;
                            }
                        }

                        mediaCodec.releaseOutputBuffer(outIndex,true);
                        break;
                }

                if ((bufferInfo.flags & mediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }
            }

            mediaCodec.stop();
            mediaCodec.release();
            mediaExtractor.release();

        }
    }

    private class AudioThread extends Thread {
        private MediaExtractor audioExtractor;
        private MediaCodec audioCodec;
        private AudioTrack audioTrack;


        @Override
        public void run() {
            audioExtractor = new MediaExtractor();

            try {

                audioExtractor.setDataSource(SAMPLE);

                for (int i = 0; i < audioExtractor.getTrackCount(); i++) {
                    MediaFormat mediaFormat = audioExtractor.getTrackFormat(i);
                    String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
                    if (mime.startsWith("audio/")) {
                        audioExtractor.selectTrack(i);
                        int mChannel = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int audioChannel;
                        if (mChannel == 1) {
                            audioChannel = AudioFormat.CHANNEL_OUT_MONO;
                        }else {
                            audioChannel = AudioFormat.CHANNEL_OUT_STEREO;
                        }
                        int audioSampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int audioOutFormat;
                        if (mediaFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                            audioOutFormat = mediaFormat.getInteger(MediaFormat.KEY_PCM_ENCODING);
                        }else {
                            audioOutFormat = AudioFormat.ENCODING_PCM_16BIT;
                        }
                        int minBufferSize = AudioTrack.getMinBufferSize(
                                audioSampleRate,
                                audioChannel,
                                audioOutFormat);
//                        int maxInputSize = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
//                        int audioInputBufferSize = minBufferSize > 0 ? minBufferSize * 4 :
//                                maxInputSize;
                        audioTrack = new AudioTrack(
                                AudioManager.STREAM_MUSIC,
                                audioSampleRate,
                                audioChannel,
                                audioOutFormat,
                                minBufferSize,
                                AudioTrack.MODE_STREAM);
                        audioTrack.play();
                        audioCodec = MediaCodec.createDecoderByType(mime);
                        audioCodec.configure(mediaFormat,null, null, 0);
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (audioCodec == null) {
                Log.e(TAG, "Can't find audio info");
                return;
            }

            audioCodec.start();

            ByteBuffer[] audioInputBuffers = audioCodec.getInputBuffers();
            ByteBuffer[] audioOutputBuffers = audioCodec.getOutputBuffers();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

            boolean isEOS = false;
            long startMs = System.currentTimeMillis();

            while (!Thread.interrupted()) {
                if (!isEOS) {
                    /**
                     * audio data loading starts here.
                     */
                    int audioInIndex =
                            audioCodec.dequeueInputBuffer(WAITING_TIME);
                    ByteBuffer audioBufferIn = audioInputBuffers[audioInIndex];
                    if (audioInIndex >= 0) {
                        int bufferSize = audioExtractor.readSampleData(audioBufferIn, 0);
                        if (bufferSize < 0) {
                            Log.e(TAG, " audio BUFFER_FLAG_END_OF_STREAM ");
                            audioCodec.queueInputBuffer(audioInIndex, 0, 0,
                                    0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }else {
                            audioCodec.queueInputBuffer(audioInIndex, 0, bufferSize,
                                    audioExtractor.getSampleTime(), 0);
                            audioExtractor.advance();
                        }
                    }
                }

                /**
                 * we start audio output process here
                 */
                int audioOutIndex = audioCodec.dequeueOutputBuffer(audioBufferInfo,
                        WAITING_TIME);
                switch (audioOutIndex) {
                    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED :
                        Log.d(TAG, "Audio INFO_OUTPUT_BUFFERS_CHANGED");
                        audioOutputBuffers = audioCodec.getOutputBuffers();
                        break;
                    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED :
                        Log.d(TAG,
                                "Audio INFO_OUTPUT_FORMAT_CHANGED, new format : " +
                                        audioCodec.getOutputFormat());
                        break;
                    case MediaCodec.INFO_TRY_AGAIN_LATER :
                        Log.d(TAG, "Audio dequeueOutputBuffer time out, please try " +
                                "later");
                        break;
                    default :
                        ByteBuffer audioBufferOut = audioOutputBuffers[audioOutIndex];
                        Log.v(TAG, "Audio Buffer for output : " + audioBufferOut);

                        byte[] tempBuffer = new byte[audioBufferOut.limit()];
                        audioBufferOut.position(0);
                        audioBufferOut.get(tempBuffer, 0, audioBufferOut.limit());
                        audioBufferOut.clear();
                        if (audioTrack != null) {
                            audioTrack.write(tempBuffer, 0, audioBufferInfo.size);
                        }

//                        while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs) {
//                            try {
//                                sleep(10);
//                            }catch (InterruptedException e) {
//                                Log.v(TAG,
//                                        "playback speed control failed.");
//                                e.printStackTrace();
//                                break;
//                            }
//                        }

                        /* There is no need to render since the media is audio.  */
                        audioCodec.releaseOutputBuffer(audioOutIndex,false);
                        break;
                }

                if ((audioBufferInfo.flags & audioCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    Log.d(TAG, "Audio OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                    break;
                }

                audioCodec.stop();
                audioCodec.release();
                audioExtractor.release();
            }
        }
    }
}
