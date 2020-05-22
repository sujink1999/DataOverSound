package com.sujin.dataoversound;

import android.os.AsyncTask;
import android.os.Process;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

import static android.os.Process.THREAD_PRIORITY_BACKGROUND;
import static android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE;

public class RecordTask extends AsyncTask<Integer, Void, Void> implements Callback {

    //Size of recorded samples
    private int bufferSizeInBytes = 0;
    //Working task flag
    private boolean work=true;
    //List of samples that need to be calculated
    private ArrayList<ChunkElement> recordedArray;
    //Semaphore for "producer consumer synchronization" around recordedArray
    final private String recordedArraySem="Semaphore";
    //Recorder task used for recording samples
    private Recorder recorder=null;
    //Received message (after recording)
    private String myString="";
    //Callback (father) activity where message is passed after recording
    private CallbackSendRec callbackRet;
    //If data is being sent, this is name of directory where file should be saved
    private String fileName=null;

    @Override
    protected Void doInBackground(Integer... integers) {
        Process.setThreadPriority(THREAD_PRIORITY_BACKGROUND + THREAD_PRIORITY_MORE_FAVORABLE);
        //Load passed settings arguments
        int StartFrequency=integers[0];
        int EndFrequency=integers[1];
        int BitPerTone=integers[2];
        int Encoding=integers[3];
        int ErrorCheck=integers[4];
        int ErrorCheckByteNum=integers[5];
        //Create list for recorded samples
        recordedArray=new ArrayList<ChunkElement>();
        //Create frequency to bit converter with specific parameters
        BitFrequencyConverter bitConverter = new BitFrequencyConverter(StartFrequency, EndFrequency, BitPerTone);
        //Load chanel synchronization parameters
        int HalfPadd= bitConverter.getPadding()/2;
        int HandshakeStart= bitConverter.getHandshakeStartFreq();
        int HandshakeEnd= bitConverter.getHandshakeEndFreq();
        //Create recorder and start it
        recorder=new Recorder();
        recorder.setCallback(this);
        recorder.start();
        //Flag used for start of receiving
        int listeningStarted=0;
        //Counter used to know when to start receiving
        int startCounter=0;
        //Counter used to know when to end receiving
        int endCounter=0;
        //Used if file is being received for name part of file
        byte[] namePartBArray=null;
        //Flag used to know if data has been received before last synchronization bit
        int lastInfo=2;
        myString="";

        while (work) {
            //Wait and get recorded data
            ChunkElement tempElem;
            synchronized (recordedArraySem){
                while (recordedArray.isEmpty()){
                    try {
                        recordedArraySem.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                tempElem=recordedArray.remove(0);
                recordedArraySem.notifyAll();
            }
            //Calculate frequency from recorded data
            double currNum=calculate(tempElem.getBuffer(), StartFrequency, EndFrequency, HalfPadd);
            //Check if listening started
            if(listeningStarted==0){
                //If listening didn't started and frequency is in range of StartHandshakeFrequency
                if((currNum>(HandshakeStart-HalfPadd)) && (currNum<(HandshakeStart+HalfPadd))){
                    startCounter++;
                    //If there were two StartHandshakeFrequency one after another start recording
                    if(startCounter>=2){
                        listeningStarted=1;
                        //Used to tell callback that receiving started
                        publishProgress();
                    }
                }
                else{
                    //If its not StartHandshakeFrequency reset counter
                    startCounter=0;
                }
            }
            //If listening started
            else{
                //Check if its StartHandshakeFrequency (used as synchronization bit) after receiving
                //starts
                if((currNum>(HandshakeStart-HalfPadd)) && (currNum<(HandshakeStart+HalfPadd))){
                    //Reset flag for received data
                    lastInfo=2;
                    //Reset end counter
                    endCounter=0;
                }
                else{
                    //Check if its EndHandshakeFrequency
                    if(currNum>(HandshakeEnd-HalfPadd)){
                        endCounter++;
                        //If there were two EndHandshakeFrequency one after another stop recording if
                        //chat message is expected fileName==null or if its data transfer and only name
                        //has been received, reset counters and flags and start receiving file data.
                        if(endCounter>=2){
                            if(fileName!=null && namePartBArray==null){
                                namePartBArray=bitConverter.getAndResetReadBytes();
                                listeningStarted=0;
                                startCounter=0;
                                endCounter=0;
                            }
                            else{
                                setWorkFalse();
                            }
                        }
                    }
                    else{
                        //Reset end counter
                        endCounter=0;
                        //Check if data has been received before last synchronization bit
                        if(lastInfo!=0){
                            //Set flag
                            lastInfo=0;
                            //Add frequency to received frequencies
                            bitConverter.calculateBits(currNum);
                        }
                    }
                }
            }
        }

        //Convert received frequencies to bytes
        byte[] readBytes= bitConverter.getAndResetReadBytes();
        try {
            if(namePartBArray==null) {
                //If its chat communication set message as return string
                myString = new String(readBytes, "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //Called for calculating frequency with highest amplitude from sound sample
    private double calculate(byte[] buffer, int StartFrequency, int EndFrequency, int HalfPad) {
        int analyzedSize=1024;
        Complex[] fftTempArray1= new Complex[analyzedSize];
        int tempI=-1;
        //Convert sound sample from byte to Complex array
        for (int i = 0; i < analyzedSize*2; i+=2) {
            short buff = buffer[i + 1];
            short buff2 = buffer[i];
            buff = (short) ((buff & 0xFF) << 8);
            buff2 = (short) (buff2 & 0xFF);
            short tempShort= (short) (buff | buff2);
            tempI++;
            fftTempArray1[tempI]= new Complex(tempShort, 0);
        }
        //Do fast fourier transform
        final  Complex[] fftArray1= FFT.fft(fftTempArray1);
        //Calculate position in array where analyzing should start and end

        int startIndex1=((StartFrequency-HalfPad)*(analyzedSize))/44100;
        int endIndex1=((EndFrequency+HalfPad)*(analyzedSize))/44100;

        int max_index1 = startIndex1;
        double max_magnitude1 = (int)fftArray1[max_index1].abs();
        double tempMagnitude;
        //Find position of frequency with highest amplitude


        for (int i = startIndex1; i < endIndex1; ++i){
            tempMagnitude=fftArray1[i].abs();
            if(tempMagnitude > max_magnitude1){
                max_magnitude1 = (int) tempMagnitude;
                max_index1 = i;
            }
        }
        return 44100 * max_index1 / (analyzedSize);

    }

    //Called to inform callback activity that receiving finished
    @Override
    protected void onPostExecute(Void aVoid) {
        super.onPostExecute(aVoid);
        if(callbackRet!=null) {
            callbackRet.actionDone(CallbackSendRec.RECEIVE_ACTION, myString);
        }
    }

    //Called to inform callback activity that receiving started
    @Override
    protected void onProgressUpdate(Void... values) {
        super.onProgressUpdate(values);
        if(callbackRet!=null){
            callbackRet.receivingSomething();
        }
    }

    //Called from recorder activity to put new samples
    @Override
    public void onBufferAvailable(byte[] buffer) {
        synchronized (recordedArraySem){
            recordedArray.add(new ChunkElement(buffer));
            recordedArraySem.notifyAll();
            while(recordedArray.size()>100){
                try {
                    recordedArraySem.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //Called to turn off task
    public void setWorkFalse(){
        if(recorder!=null){
            recorder.stop();
            recorder=null;
        }
        this.work=false;
    }

    @Override
    public void setBufferSize(int size) {
        bufferSizeInBytes=size;
    }

    public CallbackSendRec getCallbackRet() {
        return callbackRet;
    }

    public void setCallbackRet(CallbackSendRec callbackRet) {
        this.callbackRet = callbackRet;
    }

    public void setFileName(String fileName){
        this.fileName=fileName;
    }
}