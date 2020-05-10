/**
 * MediaRecorder cannot be used on emulator.
 * gonna have test on a real device.
 */
package com.example.androidplayground;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class AudioRecordPCMActivity extends AppCompatActivity {

    int recordBufferSize;
    AudioRecord audioRecord;
    AudioTrack audioTrack;
    File path;
    private String TAG = "AudioRecordPCMActivity";
    private int audioSource;
    private int frequency = 44100;
    private int channelConfig;
    private int mAudioFormat;
    private boolean isRecording = false;
    private DataInputStream din;

    private static final int MY_PERMISSIONS_REQUEST_CODE = 123;

    private static final String FILE_PATH = "/sdcard/Download";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record_pcm);
        Button startRecordButton = (Button) findViewById(R.id.start_record_button);
        startRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleStartRecordButtonOnClick();
            }
        });

        Button stopRecordButton = (Button) findViewById(R.id.stop_record_button);
        stopRecordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleStopRecordButtonOnClick();
            }
        });

        Button playWavButton = (Button) findViewById(R.id.play_wav_button);
        playWavButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                playWav();
            }
        });

        Button ConvertToWavButton = (Button) findViewById(R.id.convert_to_wav_button);
        ConvertToWavButton.setOnClickListener(new View.OnClickListener() {
            @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
            @Override
            public void onClick(View v) {
                convertToWav();
            }
        });

        initAudioRecorder();
    }

    private void handleStopRecordButtonOnClick() {
        stopRecording();
    }

    private void handleStartRecordButtonOnClick() {
        checkPermissions();
        startRecord();
    }

    private void initAudioRecorder() {
        audioSource = MediaRecorder.AudioSource.MIC;
        channelConfig = AudioFormat.CHANNEL_IN_MONO;
        mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
        recordBufferSize = AudioRecord.getMinBufferSize(frequency, channelConfig, mAudioFormat);

        audioRecord = new AudioRecord(audioSource, frequency, channelConfig, mAudioFormat, recordBufferSize);
        path = new File(FILE_PATH);
        if (!path.exists()) {
            path.mkdir();
        }
    }

    /**
     * Android Permission Request Sample Code
     */
    private void checkPermissions() {
        final Activity mActivity = this;
        if(ContextCompat.checkSelfPermission(
                this, Manifest.permission.WRITE_EXTERNAL_STORAGE) +
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(
                            this, Manifest.permission.RECORD_AUDIO)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("WRITE_EXTERNAL_STORAGE and RECORD_AUDIO are required.");
                builder.setTitle("Permission Required");
                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(mActivity,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                        Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_CODE);
                    }
                });
                builder.setNeutralButton("Cancel", null);
                AlertDialog dialog = builder.create();
                dialog.show();
            }else {
                ActivityCompat.requestPermissions(mActivity,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO}, MY_PERMISSIONS_REQUEST_CODE);
            }
        }else {
            Toast.makeText(getApplicationContext(), "Permission has already granted.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Android Permission Request Sample Code
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_CODE:{
                if (grantResults.length > 0 && (grantResults[0] + grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                    Toast.makeText(getApplicationContext(), "Permission Granted.",
                            Toast.LENGTH_SHORT).show();
                }else {
                    Toast.makeText(getApplicationContext(), "Permission Denied.",
                            Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    private void stopRecording() {
        isRecording = false;
    }

    /**
     * audio recording function.
     */
    private void startRecord() {
        isRecording = true;
        new Thread() {
            @Override
            public void run() {
                super.run();
                File file = new File(path, "audio.pcm");
                if (file.exists()) {
                    file.delete();
                }
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    Log.e(TAG, "create audio.pcm failed.");
                    e.printStackTrace();
                }
                DataOutputStream outputStream = null;
                try {
                    outputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
                    byte[] buffer = new byte[recordBufferSize];
                    audioRecord.startRecording();
                    int r = 0;
                    while (isRecording) {
                        int readResult = audioRecord.read(buffer, 0, recordBufferSize);
//                        outputStream.write(buffer, 0, recordBufferSize);
                        for (int i = 0; i < readResult; i++) {
                            outputStream.write(buffer[i]);
                        }
                        r++;
                        Log.e(TAG, "recording is undergoing...");
                    }
                    audioRecord.stop();
                    audioRecord.release();
//                    outputStream.flush();
                    outputStream.close();
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "audio recording failed.");
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.e(TAG, "audio.pcm writing attempt failed.");
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * convert pcm audio file to wav format.
     */
    private void convertToWav() {
        int FIXED_WAV_FILE_HEADER_LENGTH = 36;
        long totalAudioLength = 0;
        long totalDatalength = totalAudioLength + FIXED_WAV_FILE_HEADER_LENGTH;
        long byteRate = 16 * frequency * channelConfig / 8;
        byte[] data = new byte[recordBufferSize];

        try {
            File pcmFile = new File(FILE_PATH, "audio.pcm");
            File wavFile = new File(FILE_PATH, "audio.wav");
            FileInputStream in = new FileInputStream(pcmFile);
            FileOutputStream out = new FileOutputStream(wavFile);
            totalAudioLength = in.getChannel().size();
            totalDatalength = totalAudioLength  + 36;
            createWavFileHeader(out, totalAudioLength, totalDatalength, frequency, channelConfig,
                    byteRate);
            while(in.read(data) != -1) {
                out.write(data);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * create wav file's header
     * @param out
     * @param totalAudioLength
     * @param totalDatalength
     * @param frequency
     * @param channelConfig
     * @param byteRate
     */
    private void createWavFileHeader(FileOutputStream out, long totalAudioLength, long totalDatalength, int frequency, int channelConfig, long byteRate) {
        byte[] header = new byte[44];
        header[0] = 'R'; // RIFF
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDatalength & 0xff);//数据大小
        header[5] = (byte) ((totalDatalength >> 8) & 0xff);
        header[6] = (byte) ((totalDatalength >> 16) & 0xff);
        header[7] = (byte) ((totalDatalength >> 24) & 0xff);
        header[8] = 'W';//WAVE
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        //FMT Chunk
        header[12] = 'f'; // 'fmt '
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';//过渡字节
        //数据大小
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        //编码方式 10H为PCM编码格式
        header[20] = 1; // format = 1
        header[21] = 0;
        //通道数
        header[22] = (byte) channelConfig;
        header[23] = 0;
        //采样率，每个通道的播放速度
        header[24] = (byte) (frequency & 0xff);
        header[25] = (byte) ((frequency >> 8) & 0xff);
        header[26] = (byte) ((frequency >> 16) & 0xff);
        header[27] = (byte) ((frequency >> 24) & 0xff);
        //音频数据传送速率,采样率*通道数*采样深度/8
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        // 确定系统一次要处理多少个这样字节的数据，确定缓冲区，通道数*采样位数
        header[32] = (byte) (channelConfig * 16 / 8);
        header[33] = 0;
        //每个样本的数据位数
        header[34] = 16;
        header[35] = 0;
        //Data chunk
        header[36] = 'd';//data
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLength & 0xff);
        header[41] = (byte) ((totalAudioLength >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLength >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLength >> 24) & 0xff);
        try {
            out.write(header, 0, 44);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void readWavHeader(DataInputStream din) {
        byte[] byteIntValue = new byte[4];
        byte[] byteShortValue = new byte[2];
        try {
            String chunkID =
                    "" + (char)din.readByte() + (char)din.readByte()
                            + (char)din.readByte() + (char)din.readByte();
            Log.e("Wav_Header", "chunkID:" + chunkID);

            din.read(byteIntValue);
            int chunkSize = byteArrayToInt(byteIntValue);
            Log.e("Wav_Header", "chunkSize:" + chunkSize);

            String format =
                    "" + (char)din.readByte() + (char)din.readByte()
                            + (char)din.readByte() + (char)din.readByte();
            Log.e("Wav_Header", "format:" + format);

            String subchunk1ID =
                    "" + (char)din.readByte() + (char)din.readByte()
                            + (char)din.readByte() + (char)din.readByte();
            Log.e("Wav_Header", "subchunkID:" + subchunk1ID);

            din.read(byteIntValue);
            int subchunk1Size = byteArrayToInt(byteIntValue);
            Log.e("Wav_Header", "subchunk1Size:" + subchunk1Size);

            din.read(byteShortValue);
            short audioFormat = byteArrayToShort(byteShortValue);
            Log.e("Wav_Header", "audioFormat:" + audioFormat);

            din.read(byteShortValue);
            short numChannels = byteArrayToShort(byteShortValue);
            Log.e("Wav_Header", "numChannels:" + numChannels);

            din.read(byteIntValue);
            int sampleRate = byteArrayToInt(byteIntValue);
            Log.e("Wav_Header", "sampleRate:" + sampleRate);

            din.read(byteIntValue);
            int byteRate = byteArrayToInt(byteIntValue);
            Log.e("Wav_Header", "byteRate:" + byteRate);

            din.read(byteShortValue);
            short blockAlign = byteArrayToShort(byteShortValue);
            Log.e("Wav_Header", "blockAlign:" + blockAlign);

            din.read(byteShortValue);
            short btsPerSample = byteArrayToShort(byteShortValue);
            Log.e("Wav_Header", "btsPerSample:" + btsPerSample);

            String subchunk2ID = "" + (char) din.readByte() + (char) din.readByte() + (char) din.readByte() + (char) din.readByte();
            Log.e("Wav_Header", "subchunk2ID:" + subchunk2ID);

            din.read(byteIntValue);
            int subchunk2Size = byteArrayToInt(byteIntValue);
            Log.e("subchunk2Size", "subchunk2Size:" + subchunk2Size);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * byte array to integer
     * @param byteIntValue
     * @return
     */
    private int byteArrayToInt(byte[] byteIntValue) {
        return ByteBuffer.wrap(byteIntValue).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    /**
     * byte array to short
     * @param byteShortValue
     * @return
     */
    private short byteArrayToShort(byte[] byteShortValue) {
        return ByteBuffer.wrap(byteShortValue).order(ByteOrder.LITTLE_ENDIAN).getShort();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void playWav() {
        int bufferSizeInBytes = AudioTrack.getMinBufferSize(frequency, channelConfig, mAudioFormat);
        AudioAttributes audioAttributes =
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();

        AudioFormat audioFormat =
                new AudioFormat.Builder().setSampleRate(frequency)
                        .setEncoding(mAudioFormat).setChannelMask(channelConfig).build();

        audioTrack = new AudioTrack(audioAttributes, audioFormat,
                bufferSizeInBytes, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
        File wavFile = new File(FILE_PATH, "audio.wav");
        try {
            din =new DataInputStream(new FileInputStream(wavFile));
            readWavHeader(din);
            new Thread(ReadDataRunnable).start();
        }catch (IOException ioex) {
            Log.e(TAG, "read wav file failed.");
            ioex.printStackTrace();
        }catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private Runnable ReadDataRunnable = new Runnable() {
        @Override
        public void run() {
            byte[] buffer = new byte[1024 * 2];
            try {
                while (din.read(buffer, 0, buffer.length) > 0) {
                    if (audioTrack.write(buffer, 0, buffer.length) != buffer.length) {

                    }
                    audioTrack.play();
                }
                audioTrack.stop();
                audioTrack.release();
                if (din != null) {
                    din.close();
                    din = null;
                }
            }catch (IOException e) {
                // TO DO need a global Exception Handler for all Runnable class or it's subclasses
                e.printStackTrace();
            }

        }
    };
}
