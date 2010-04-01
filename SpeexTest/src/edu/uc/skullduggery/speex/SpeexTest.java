package edu.uc.skullduggery.speex;

import android.app.Activity;
import android.os.Bundle;
import android.os.*;
//import android.view.*;
import android.widget.*;

import org.xiph.speex.*;

import java.io.*;

/*
 * Important:
 * You MUST have the file /sdcard/audio (raw PCM file) and
 * the correct parameters for sampleRate, channels, etc. specified below.
 */

public class SpeexTest extends Activity {
	
	final Handler h = new Handler(){
		public void handleMessage(Message m){
			if (m.what == 0){
				((TextView) findViewById(R.id.textView1)
				).append((String) m.obj);
			}
		}
		
	};
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        
        new Thread(new Runnable(){public void run(){
        
        try{
        h.sendMessage(Message.obtain(h, 0, "opening file streams\n"));
//        FileInputStream fin = new FileInputStream("/sdcard/rawaudio");
        DataInputStream fin =
        new DataInputStream(new FileInputStream("/sdcard/audio"));
//        FileOutputStream fout = new FileOutputStream("/sdcard/audio.spx");
        AudioFileWriter fout = new OggSpeexWriter();
        fout.open("/sdcard/audio.spx");
        DataOutputStream out2 =
        new DataOutputStream(new FileOutputStream("/sdcard/audio2"));
        h.sendMessage(Message.obtain(h, 0, "init SpeexEncoder\n"));
        
        final int sampleRate = 8000;
        final int channels = 1;
        final int sampleSizeBits = 16;
        //If more than 8-bit should always be LITTLE_ENDIAN
        
        SpeexEncoder enc = new SpeexEncoder();
        //1 packet NB = 160 samples = 20 ms
        //1 packet WB = 320 samples = 20 ms
        //1 packet UWB = 640 samples = 20 ms
        enc.init(0,//mode
        		//0 for NarrowBand(8khz)
        		//1 - WideBand(16khz)
        		//2-UltraWideBand(32khz) 
        		1,//quality level 1-10
        		sampleRate,
        		channels);
        
        SpeexDecoder dec = new SpeexDecoder();
        //for the Decoder, sampleRate and channels refer to the output
        //format, but we'll make it the same so we can compare in and out files
        dec.init(0, sampleRate, channels, false);
        
        // Raw block size is 160 samples for NB,
        // times 2 for stereo
        // times (16/8) = 2 for 16-bit
        final int rawBlockSize = enc.getFrameSize()
        * channels * (sampleSizeBits / 8);
        
//        final int framesPerPacket = 1;//always 1 for now (it's the default)
        
        h.sendMessage(Message.obtain(h, 0, "raw block size="));
        h.sendMessage(Message.obtain(h, 0,
        Integer.toString(rawBlockSize) + "\n"));
        int everySoMany = 0; //used for debug output so
                               //doesn't show for every loop iteration

        //byte[] bbuf2 = new byte[1024];
        //int bytesWritten = 0;
        
        byte[] bbuf = new byte[1024];
        int bytesRead = 0;
        
        long delay1, delay2, delay3, delay4, delayTotal;

        try {
            // read until we get to EOF
            while (true) {
            	
//              fin.readFully(bbuf, 0, framesPerPacket*rawBlockSize);
              
              fin.readFully(bbuf, 0, rawBlockSize);
              
//              for (int i=0; i<framesPerPacket; i++)
//                enc.processData(bbuf, i*rawBlockSize, rawBlockSize);
              
              if ((everySoMany++ % 10) == 0){
            	  h.sendMessage(Message.obtain(
            	  h, 0, rawBlockSize + " bytes to process\n"));
              }
              
              delay1=System.currentTimeMillis();
          
              // processData() assumes 16-bit samples are little-endian
              enc.processData(bbuf, 0, rawBlockSize);
              //why does this line get stopped up?
              
              delay2=System.currentTimeMillis();
              
              if (((everySoMany-1) % 10) == 0){
            	  h.sendMessage(Message.obtain(h, 0, "process done\n"));
              }
              
              delay3=System.currentTimeMillis();
              
              bytesRead = enc.getProcessedData(bbuf, 0);
              
              delay4=System.currentTimeMillis();
              
              if (((everySoMany-1) % 10) == 0){
            	  h.sendMessage(Message.obtain(h, 0, "proc+retreval time:"
            	   +  ((delay2-delay1)+(delay4-delay3))  +"\n"));
              }
              
              if (bytesRead > 0) {
            	
            	/*this line gets replaced with a paraphrase of code
            	 * from AudioFileWriter+OggSpeexWriter for writing to stream*/
                fout.writePacket(bbuf, 0, bytesRead);
                /*this line gets replaced with other code*/
              }
              
              // Decode the data again and we should get the
              // original signal (lower quality cause of speex of course)
              delay1=System.currentTimeMillis();
              dec.processData(bbuf, 0, bytesRead);
              bytesRead = dec.getProcessedData(bbuf, 0);
              delay2=System.currentTimeMillis();

              if (((everySoMany-1) % 10) == 0){
            	  h.sendMessage(Message.obtain(h, 0, "decode time:"
        			  +  (delay2-delay1)  +"\n"));
              }
              
              out2.write(bbuf, 0, bytesRead);
              
            }
        }
        catch (EOFException e) {}
        fout.close(); 
        fin.close();
        out2.close();
        
        
        
//        new JSpeexEnc().encode(
//        new File("/sdcard/audio.wav"), new File("/sdcard/audio.spx"));
        }catch (IOException iexc){
        	h.sendMessage(Message.obtain(h, 0, iexc.toString()));
        }
        
        
        }}).start();

    }
}