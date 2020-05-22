package com.sujin.dataoversound;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity implements CallbackSendRec {

    //Is sending flag
    private boolean isSending=false;
    //Is listening flag
    private boolean isListening=false;
    //Is listening and receiving flag
    private boolean isReceiving=false;
    //Task for sending message
    private BufferSoundTask sendTask=null;
    //Task for receiving message
    private RecordTask listenTask=null;

    public static Integer DEF_START_FREQUENCY= 17500;
    public static Integer DEF_END_FREQUENCY= 20000;
    public static Integer DEF_BIT_PER_TONE=4;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button button = findViewById(R.id.play_button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendData();
            }
        });
        init();
    }

    void init(){
        BitFrequencyConverter bitFrequencyConverter = new BitFrequencyConverter(200,300,4);
        Log.v("startHandshake", Integer.toString( bitFrequencyConverter.getHandshakeStartFreq()));
        Log.v("EndHandshake", Integer.toString( bitFrequencyConverter.getHandshakeEndFreq()));
        Log.v("Padding", Integer.toString( bitFrequencyConverter.getPadding()));
        Log.v("freq", Integer.toString( bitFrequencyConverter.specificFrequency((byte)0)));
        Log.v("freq", Integer.toString( bitFrequencyConverter.specificFrequency((byte)1)));
        Log.v("freq", Integer.toString( bitFrequencyConverter.specificFrequency((byte)2)));
        Log.v("freq", Integer.toString( bitFrequencyConverter.specificFrequency((byte)14)));
        Log.v("freq", Integer.toString( bitFrequencyConverter.specificFrequency((byte)17)));
    }

    void sendData(){
        sendTask = new BufferSoundTask();
        try {
            byte[] byteText = "sendText".getBytes("UTF-8");
            sendTask.setBuffer(byteText);
            Integer[] tempArr=new Integer[6];
            tempArr[0] = DEF_START_FREQUENCY;
            tempArr[1] = DEF_END_FREQUENCY;
            tempArr[2] = DEF_BIT_PER_TONE;
            sendTask.execute(tempArr);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }


    //Called to start listening task and update GUI to listening
    private void listen(){
        isListening=true;
        Integer[] tempArr=new Integer[6];
        tempArr[0] = DEF_START_FREQUENCY;
        tempArr[1] = DEF_END_FREQUENCY;
        tempArr[2] = DEF_BIT_PER_TONE;
        listenTask=new RecordTask();
        listenTask.setCallbackRet(this);
        listenTask.execute(tempArr);
    }

    //Called on listen button click
    public void listenMessage(View view) {
        //If sending task is active, stop it and update GUI
        if(isSending){
            stopSending();
            if(sendTask!=null){
                sendTask.setWorkFalse();
            }
        }
        //If its not listening check for mic permission and start listening
        if(!isListening) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, 0);
            } else {
                listen();
            }
        }
        //If its already listening, stop listening and update GUI
        else{
            if(listenTask!=null){
                listenTask.setWorkFalse();
            }
            stopListening();
        }
    }

    //Called when user answers on permission request
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case 0: {
                //If user granted permission on mic, continue with listening
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    listen();
                }
                break;
            }
        }
    }

    //Called to reset view and flag to initial state from sending state
    private void stopSending(){
        isSending=false;
    }

    private void stopListening(){
        if(isReceiving){
            isReceiving=false;
        }
        isListening=false;
    }



    @Override
    public void actionDone(int srFlag, String message) {

    }

    @Override
    public void receivingSomething() {

    }
}
