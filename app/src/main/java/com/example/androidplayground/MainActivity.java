package com.example.androidplayground;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button audioRecordPCMButton = (Button) findViewById(R.id.audio_record_pcm);
        audioRecordPCMButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleAudioRecordPCMButtonClick();
            }
        });

        Button decodeButton = (Button) findViewById(R.id.decode_button);
        decodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleDecodeButtonClick();
            }
        });
    }

    private void handleDecodeButtonClick() {
        Intent intent = new Intent();
        intent.setClass(this, DecodeActivity.class);
        startActivity(intent);
    }

    private void handleAudioRecordPCMButtonClick() {
        Intent intent = new Intent();
        intent.setClass(this, AudioRecordPCMActivity.class);
        startActivity(intent);
//        overridePendingTransition(R.anim.slide_right, R.anim.slide_left);
    }
}
