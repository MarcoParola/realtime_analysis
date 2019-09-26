package com.example.realtimeanalysis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.example.shared_mobile_wear.DataSensor;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Receiver extends BroadcastReceiver {

    int iterazione = 0;
    MainActivity main;


    public Receiver(MainActivity m) {
        main = m;
    }

    public void onReceive(Context context, Intent intent) {


        // ottengo un array di oggetti DataSensor da uno stream di byte
        DataSensor buffer;
        buffer = (DataSensor) intent.getSerializableExtra("message");
        Log.i("pressione", buffer.getValue0());

        /*for(int i=0; i<50; i++){
            Log.i("data" + iterazione + " " + i, buffer.getSensorType() + " " );
        }*/
        iterazione++;
        writeFile(buffer.getValue0());
    }

    public void writeFile(String buf) {

        try {

            String msg = null;
            FileOutputStream fw = null;

            fw = new FileOutputStream(main.pressure_file.getAbsoluteFile(), true);
            msg = buf + "\n";
            fw.write(msg.getBytes());
            fw.close();

        } catch (IOException e) {
            Log.e("Exception", e.getLocalizedMessage(), e);
        }


    }
}
