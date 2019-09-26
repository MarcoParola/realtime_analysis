package com.example.realtimeanalysis;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;

import com.example.shared_mobile_wear.DataSensor;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends WearableActivity implements SensorEventListener {

    static int WINDOW_SIZE = 50;
    static int GAUSSIAN_WINDOW_SIZE = 100;
    static int PORTION_OF_SIGNAL = 100;
    int counter;
    Float media;

    String gaussianFile = "gaussian_weights";
    BufferedReader br = null;
    String line = "";

    private SensorManager sensorManager;
    ArrayList<Sensor> sensorList;

    ArrayList <Float> gauss = new ArrayList<Float>();
    ArrayList<Float> noisy_signal;
    ArrayList<Long> noisy_signal_timestamp;
    ArrayList<Float> movmean_signal;
    ArrayList<Long> movmean_signal_timestamp;
    ArrayList<Float> gaussian_signal;
    ArrayList<Long> gaussian_signal_timestamp;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        counter = 0;
        media = 0.0f;
        noisy_signal = new ArrayList<Float>();
        noisy_signal_timestamp = new ArrayList<Long>();
        movmean_signal = new ArrayList<Float>();
        movmean_signal_timestamp = new ArrayList<Long>();
        gaussian_signal = new ArrayList<Float>();
        gaussian_signal_timestamp = new ArrayList<Long>();

        // ------ registro i sensrori che mi interessano ------
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorList = new ArrayList<Sensor>();
        sensorList.add(sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD));
        sensorList.add(sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE));
        sensorList.add(sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER));
        sensorList.add(sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE));
        for (Sensor s: sensorList) {
            sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_FASTEST);
        }

        // ----- preparo array con i pesi gaussiani letti da file -----
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open(gaussianFile)));
            while ((line = br.readLine()) != null) {
                gauss.add(Float.parseFloat(line));
                Log.i("weight ", line );
            }
        } catch (FileNotFoundException e) {e.printStackTrace();
        } catch (IOException e) {e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                }
                catch (IOException e) { e.printStackTrace();}
            }
        }

        // Enables Always-on
        setAmbientEnabled();

    }



    public void onSensorChanged(SensorEvent event) {

        if(event.sensor.getType() == 6){ // PRESSURE

            noisy_signal.add(event.values[0]);
            noisy_signal_timestamp.add(event.timestamp);
            counter++;

            // ----- calcolo la media non appena ho tanti campioni quanto è la dimensione della finestra ----
            if(counter == WINDOW_SIZE){
                for(Float f : noisy_signal)
                    media += f;
                media /= WINDOW_SIZE;
            }

            if(counter > WINDOW_SIZE){

                Float removed = noisy_signal.remove(0);
                noisy_signal_timestamp.remove(0);
                media = media + ((event.values[0] - removed) / WINDOW_SIZE);

                // ---- inizio a riempire anche il buffer del segnale filtrato a media mobile -----
                movmean_signal.add(media);
                movmean_signal_timestamp.add(noisy_signal_timestamp.get(WINDOW_SIZE/2));

                if(counter >  GAUSSIAN_WINDOW_SIZE + WINDOW_SIZE){
                    movmean_signal.remove(0);
                    movmean_signal_timestamp.remove(0);

                    Float fl = gaussian_average(movmean_signal);



                    // ---- DEBUG ----
                    DataSensor mess = new DataSensor();
                    mess.setSensorType(event.sensor.getType());
                    mess.setTimestamp(String.valueOf(event.timestamp));
                    mess.setValue0(String.valueOf(fl));
                    new SendMessage("/data", mess).start();

                    gaussian_signal.add(fl);
                    gaussian_signal_timestamp.add(movmean_signal_timestamp.get(GAUSSIAN_WINDOW_SIZE / 2));

                    // ----- preparo il segnale filtrato a col filtro gaussiano -----
                    if(counter >  WINDOW_SIZE + GAUSSIAN_WINDOW_SIZE + PORTION_OF_SIGNAL){
                        gaussian_signal.remove(0);
                        gaussian_signal_timestamp.remove(0);

                        boolean peak = findPeak(gaussian_signal);
                        Log.i("PEAK", "PICCOOOO!");
                        if(peak){
                            Log.i("PEAK", "TROOOOOOOOOOOOOOOOOOOOVATOOOOOOOOOOOO");
                            noisy_signal.clear();
                            noisy_signal_timestamp.clear();
                            movmean_signal.clear();
                            movmean_signal_timestamp.clear();
                            gaussian_signal.clear();
                            gaussian_signal_timestamp.clear();
                        }
                    }
                }
            }
        }

    }

    private boolean findPeak(ArrayList<Float> signal) {
        ArrayList<Float> avgs = new ArrayList<Float>();
        Float sum = 0.0f;
        int i=0;

        for(Float f : signal) {
            sum += f;
            i++;

            if((i % (WINDOW_SIZE / 10)) == 0) {
                avgs.add(sum);
                sum = 0.0f;
            }
        }

        boolean ret = true;
        for (i = 0; i < (avgs.size() / 2) - 1; i++)
            ret = ret && (avgs.get(i) * 1.05 < avgs.get(i+1));
        for (i = (avgs.size() / 2) + 1; i < avgs.size() - 1; i++)
            ret = ret && (avgs.get(i) > 1.05 * avgs.get(i+1));

        return ret;
    }

    Float gaussian_average(ArrayList<Float> signal){
        Float avg = 0.0f;
        int i = 0;

        for(Float f : signal) {
            Log.i("Calcolo media" , f + " * " + gauss.get(i));
            avg = avg + (f * gauss.get(i));
            i++;
        }
        avg /= GAUSSIAN_WINDOW_SIZE;
        return avg;
    }


    public void onAccuracyChanged(Sensor sensor, int accuracy) {}



    class SendMessage extends Thread {
        String path;
        //Constructor for sending information to the Data Layer//
        DataSensor message;

        SendMessage(String p, DataSensor m) {
            path = p;
            message = m;
        }

        public void run() {

            //Retrieve the connected devices//
            Task<List<Node>> nodeListTask =
                    Wearable.getNodeClient(getApplicationContext()).getConnectedNodes();


            try {

                //Block on a task and get the result synchronously//
                List<Node> nodes = Tasks.await(nodeListTask);
                for (Node node : nodes) {
                    try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ObjectOutputStream oos = new ObjectOutputStream(bos);
                        oos.writeObject(message);

                        //Send the message//
                        Wearable.getMessageClient(MainActivity.this).sendMessage(node.getId(), path, bos.toByteArray());
                        oos.close();
                        bos.close();
                    }
                    catch(IOException e){e.printStackTrace();}
                }
            }
            catch (ExecutionException e) {e.printStackTrace();}
            catch (InterruptedException e) {e.printStackTrace();}
        }
    }


}
