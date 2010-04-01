package edu.uc.skullduggery;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.*;

public class SkullAudio extends Activity {
	private ListenThread T;
	private SpeakThread S;
	private Socket soundSock;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        try {
			soundSock = new Socket("10.0.2.2", 9002);
	        T = new ListenThread(soundSock);
	        S = new SpeakThread(soundSock);
	        T.start();
	        S.start();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    
    private class SpeakThread extends Thread{
    	private Socket soundSock;
    	
    	public SpeakThread(Socket S)
    	{
    		soundSock = S;
    	}
    	
    	public void run()
    	{
    		try
    		{
    			android.util.Log.d("SkullAudio", "Beginning audio recording thread.");
	    		int source = MediaRecorder.AudioSource.VOICE_RECOGNITION;
	    		int bitRate = 8000; //11025, 22050, 44100
	    		int channelConfig = AudioFormat.CHANNEL_IN_MONO;
	    		int encoding = AudioFormat.ENCODING_PCM_16BIT;
	    		int bufSize = AudioRecord.getMinBufferSize(bitRate, channelConfig, encoding);
	    		int len = 0;

    			android.util.Log.d("SkullAudio", "Initializing output stream.");
	    		DataOutputStream DOS = new DataOutputStream(soundSock.getOutputStream());
	    		
	    		DOS.writeInt(bitRate);
	    		//DOS.writeInt(channelConfig);
	    		DOS.writeInt(encoding);
	    		
	    		byte[] buffer = new byte[1024];

    			android.util.Log.d("SkullAudio", "Initializing recorder.");
	    		AudioRecord SoundRecorder = new AudioRecord(source, bitRate, channelConfig, encoding, bufSize);

    			android.util.Log.d("SkullAudio", "Starting to record.");
	    		SoundRecorder.startRecording();
	
	    		while(SoundRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING && soundSock.isConnected())
	    		{
	    			android.util.Log.d("SkullAudio", "Reading recorded data.");
	        		while((len = SoundRecorder.read(buffer, 0, buffer.length)) > 0)
	    				DOS.write(buffer, 0, len);

	    			android.util.Log.d("SkullAudio", "Recorded data written.");
	    		}

    			android.util.Log.d("SkullAudio", "Connection closed, etc.");
	    		
	    		SoundRecorder.stop();
    		}
    		catch (IOException e)
    		{
    			android.util.Log.e("SkullAudio", "Error when recording audio.");
    			android.util.Log.e("SkullAudio", e.getMessage());
    		}
    	}
    }
    
    
    private class ListenThread extends Thread{
    	
    	private Socket soundSock;
    	
    	public ListenThread(Socket S)
    	{
    		soundSock = S;
    	}
    	
    	public void run()
    	{
    		try{

    			android.util.Log.d("SkullAudio", "Initializing the reading stream");
    			DataInputStream rawAudio = new DataInputStream(soundSock.getInputStream());
				
				byte[] buf = new byte[1024];
				int sampleRate, channelConfig, format;
				AudioTrack track;
				
				if(soundSock.isConnected() && !soundSock.isClosed())
				{

					android.util.Log.d("SkullAudio", "Reading sample rate");
					sampleRate = rawAudio.readInt();
					android.util.Log.d("SkullAudio", "Sample rate:" + Integer.toString(sampleRate));
					
					android.util.Log.d("SkullAudio", "Reading channel config");
					channelConfig = AudioFormat.CHANNEL_OUT_MONO;
//					channelConfig = rawAudio.readInt();
					android.util.Log.d("SkullAudio", "Channel Config:" + Integer.toString(channelConfig));
					
					android.util.Log.d("SkullAudio", "Reading audio format");
					format = rawAudio.readInt();
					android.util.Log.d("SkullAudio", "Audio format:" + Integer.toString(format));
					
					android.util.Log.d("SkullAudio", "Initializing track");
					track = new AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, channelConfig, format, AudioTrack.getMinBufferSize(sampleRate, channelConfig, format), AudioTrack.MODE_STREAM);

				}
				else
				{
					rawAudio.close();
					soundSock.close();
					return;
				}
				android.util.Log.d("SkullAudio", "Track initialized. Playing back data.");
				
				while(soundSock.isConnected() && ! soundSock.isClosed())
				{
					android.util.Log.d("SkullAudio", "Beginning read");
					
					int bytesRead = rawAudio.read(buf);
					track.write(buf, 0, bytesRead);
					if(track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING){
						track.play();
					}
					android.util.Log.d("SkullAudio", "Written to buffer");
					
				}
				android.util.Log.d("SkullAudio", "Done. Closing up.");
				
				rawAudio.close();
				soundSock.close();
    		}
    		catch (IOException e)
    		{
    			android.util.Log.e("SkullAudio", "Error when playing audio.");
    			android.util.Log.e("SkullAudio", e.getMessage());
    		}
    	}
    }
}
