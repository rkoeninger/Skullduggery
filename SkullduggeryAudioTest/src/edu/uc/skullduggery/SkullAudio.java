package edu.uc.skullduggery;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.AudioRecord.OnRecordPositionUpdateListener;
import android.os.Bundle;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.*;

public class SkullAudio extends Activity {
	private ListenThread T;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        T = new ListenThread();
        T.start();
    }
    
    
    private class ListenThread extends Thread{
    	
    	private Socket soundSock;

    	public ListenThread()
    	{
    		int source = MediaRecorder.AudioSource.DEFAULT;
    		int bitRate = 8000; //11025, 22050, 44100
    		int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    		int encoding = AudioFormat.ENCODING_PCM_16BIT;
    		//int encoding = AudioFormat.ENCODING_PCM_8BIT;
    		int bufSize = AudioRecord.getMinBufferSize(bitRate, channelConfig, encoding);
    		
    		AudioRecord SoundRecorder = new AudioRecord(source, bitRate, channelConfig, encoding, bufSize);
            SoundRecorder.setRecordPositionUpdateListener(
            		new OnRecordPositionUpdateListener() {    					
    					@Override
    					public void onPeriodicNotification(AudioRecord recorder) {
    						byte[] audioData = new byte[1024];
    						int len = 0;
    						
    						while((len = recorder.read(audioData, 0, audioData.length)) > 0)
    						{
    							try {
    								write(audioData, len);
    							} catch (IOException e) {
    								e.printStackTrace();
    								break;
    							}
    						}
    						
    						// TODO Auto-generated method stub
    						
    					}
    					
    					@Override
    					public void onMarkerReached(AudioRecord recorder) {
    						int format = 		recorder.getAudioFormat();
    						int channelConfig = recorder.getChannelConfiguration();
    						int sampleRate = 	recorder.getSampleRate();
    						try {
    							DataOutputStream DOS = new DataOutputStream(soundSock.getOutputStream());
    							DOS.writeInt(sampleRate);
    							DOS.writeInt(channelConfig);
    							DOS.writeInt(format);
    						}
    						catch (IOException e) {
    						
    						}
    						
    						onPeriodicNotification(recorder);
    					}
    				});
            SoundRecorder.startRecording();
            
    		try{
    			soundSock = new Socket("10.0.2.2", 9002);
    		}
    		catch(IOException e)
    		{
    			//Print an error message, etc.
    		}
    	}
    	
    	public void run()
    	{
    		DataInputStream rawAudio = null;
    		try {
    			rawAudio = new DataInputStream(soundSock.getInputStream());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			byte[] buf = new byte[1024];
			
			int sampleRate, channelConfig, format;
			AudioTrack track;
			
			try{
				if(soundSock.isConnected() && !soundSock.isClosed())
				{
					sampleRate = rawAudio.readInt();
					channelConfig = rawAudio.readInt();
					format = rawAudio.readInt();
					track = new AudioTrack(AudioManager.USE_DEFAULT_STREAM_TYPE, sampleRate, channelConfig, format, AudioTrack.getMinBufferSize(sampleRate, channelConfig, format), AudioTrack.MODE_STREAM);
				}
				else
				{
					rawAudio.close();
					soundSock.close();
					return;
				}
			}
			catch(IOException e)
			{
				return;
			}

			try {
				while(soundSock.isConnected() && ! soundSock.isClosed())
				{
					int bytesRead = rawAudio.read(buf);
					track.write(buf, 0, bytesRead);
					if(track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING){
						track.play();
					}

				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				try {
				
					rawAudio.close();
					soundSock.close();
				}
				catch (IOException e1) {
					// TODO Auto-generated catch block
				}
			}
    		
    	}
    	
    	public void write(byte[] audioData, int length) throws IOException
    	{
    		DataOutputStream rawAudio;
    		OutputStream soundSockOutput = soundSock.getOutputStream();
    		rawAudio = new DataOutputStream(soundSockOutput);
    		rawAudio.write(audioData, 0, length);
    	}
    }
}