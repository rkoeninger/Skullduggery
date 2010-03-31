package edu.uc.skullduggery.speex;

import android.app.Activity;
import android.os.Bundle;
//import android.view.*;
import android.widget.*;

import org.xiph.speex.*;

import java.io.*;

public class SpeexTest extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        TextView textView = (TextView) findViewById(R.id.textView1);
        try{
        textView.append("opening file streams\n");
//        FileInputStream fin = new FileInputStream("/sdcard/rawaudio");
        DataInputStream fin =
        new DataInputStream(new FileInputStream("/sdcard/audio"));
//        FileOutputStream fout = new FileOutputStream("/sdcard/audio.spx");
        AudioFileWriter fout = new OggSpeexWriter();
        fout.open("/sdcard/audio.spx");
        textView.append("init SpeexEncoder\n");
        
        SpeexEncoder enc = new SpeexEncoder();
        //1 packet NB = 160 samples = 20 ms
        //1 packet WB = 320 samples = 20 ms
        //1 packet UWB = 640 samples = 20 ms
        enc.init(0,//mode
        		//0 for NarrowBand(8khz)
        		//1 - WideBand(16khz)
        		//2-UltraWideBand(32khz) 
        		5,//quality level 1-10
        		44100,//sample rate
        		2);//number of channels
        
        // Raw block size is 160 samples for NB,
        // times 2 for stereo
        // times (16/8) = 2 for 16-bit
        final int rawBlockSize = enc.getFrameSize() * 2 * (16 / 8);
        
//        final int framesPerPacket = 1;//always 1 for now (it's the default)
        
        textView.append("raw block size=");
        textView.append(Integer.toString(rawBlockSize) + "\n");
        int everySoMany = 0; //used for debug output so
                               //doesn't show for every loop iteration

        //byte[] bbuf2 = new byte[1024];
        //int bytesWritten = 0;
        
        byte[] bbuf = new byte[1024];
        int bytesRead = 0;

        try {
            // read until we get to EOF
            while (true) {
            	
//              fin.readFully(bbuf, 0, framesPerPacket*rawBlockSize);
              
              fin.readFully(bbuf, 0, rawBlockSize);
              
//              for (int i=0; i<framesPerPacket; i++)
//                enc.processData(bbuf, i*rawBlockSize, rawBlockSize);
              
              if ((everySoMany++ % 10) == 0){
          	      textView.append(rawBlockSize + " bytes to process\n");
              }
              
              // processData() assumes 16-bit samples are little-endian
              enc.processData(bbuf, 0, rawBlockSize);
              //why does this line get stopped up?
              
              if (((everySoMany-1) % 10) == 0){
            	  textView.append("process done\n");
              }
              
              bytesRead = enc.getProcessedData(bbuf, 0);
              if (bytesRead > 0) {
            	
            	/*this line gets replaced with a paraphrase of code
            	 * from AudioFileWriter+OggSpeexWriter for writing to stream*/
                fout.writePacket(bbuf, 0, bytesRead);
                /*this line gets replaced with other code*/
              }
            }
        }
        catch (EOFException e) {}
        fout.close(); 
        fin.close();
        
        
        Toast.makeText(this, "DONE", Toast.LENGTH_LONG);
        
        
//        new JSpeexEnc().encode(
//        new File("/sdcard/audio.wav"), new File("/sdcard/audio.spx"));
        }catch (IOException iexc){
        	textView.append(iexc.toString());
        }

    }
}