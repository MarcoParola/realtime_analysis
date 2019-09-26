package com.example.realtimeanalysis;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Environment;

import java.io.File;
import java.io.IOException;


public class MainActivity extends AppCompatActivity {

    public File pressure_file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pressure_file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "pressure_watch3.csv");
        if (!pressure_file.exists()) {
            try {
                pressure_file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        // registro il listener che riceve i messaggi inviati dal service MessageService
        IntentFilter messageFilter = new IntentFilter(Intent.ACTION_SEND);
        Receiver messageReceiver = new Receiver(this);
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, messageFilter);
    }


}
