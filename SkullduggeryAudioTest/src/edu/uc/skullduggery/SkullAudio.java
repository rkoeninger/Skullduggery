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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import edu.uc.skullduggery.SkullMessageFactory.TrickeryException;

public class SkullAudio extends Activity {

	private final String HMAC = "HmacSHA1";
	private final String AES = "AES";
	private final int MESSAGE_SIZE = 4096;
	
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
    			KeyGenerator cryptoGen = KeyGenerator.getInstance(AES);
    			KeyGenerator hashGen = KeyGenerator.getInstance(HMAC);
    			cryptoGen.init(128);
    			hashGen.init(128);
    			SecretKey cryptoKey = cryptoGen.generateKey();
    			SecretKey hashKey = hashGen.generateKey();
    			
    			SkullMessageFactory SMF = new SkullMessageFactory(cryptoKey, hashKey);
    			
    			android.util.Log.d("SkullAudio", "Beginning audio recording thread.");
	    		int source = MediaRecorder.AudioSource.VOICE_RECOGNITION;
	    		int bitRate = 8000; //11025, 22050, 44100
	    		int channelConfig = AudioFormat.CHANNEL_IN_MONO;
	    		int encoding = AudioFormat.ENCODING_PCM_16BIT;
	    		int bufSize = AudioRecord.getMinBufferSize(bitRate, channelConfig, encoding)*8;

    			android.util.Log.d("SkullAudio", "Initializing output stream.");
	    		DataOutputStream DOS = new DataOutputStream(soundSock.getOutputStream());
	    		
	    		DOS.write(cryptoKey.getEncoded(), 0, cryptoKey.getEncoded().length);
	    		DOS.write(hashKey.getEncoded(), 0, hashKey.getEncoded().length);
	    		
	    		DOS.writeInt(bitRate);
	    		//DOS.writeInt(channelConfig);
	    		DOS.writeInt(encoding);
	    		
	    		byte[] buffer = new byte[MESSAGE_SIZE];

    			android.util.Log.d("SkullAudio", "Initializing recorder.");
	    		AudioRecord SoundRecorder = new AudioRecord(source, bitRate, channelConfig, encoding, bufSize);

    			android.util.Log.d("SkullAudio", "Starting to record.");
	    		SoundRecorder.startRecording();
	
	    		while(SoundRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING && soundSock.isConnected())
	    		{
	    			android.util.Log.d("SkullAudio", "Reading recorded data.");
	    			int len = 0;
	    			while(len < buffer.length)
	    				len += SoundRecorder.read(buffer, len, buffer.length - len);
	    			SkullMessage m = SMF.createMessage(buffer);
	    			
	    			DOS.write(m.getHashedData());

	    			android.util.Log.d("SkullAudio", "Recorded data written.");
	    		}

    			android.util.Log.d("SkullAudio", "Connection closed, etc.");
	    		
	    		SoundRecorder.stop();
    		}
    		catch (IOException e)
    		{
    			android.util.Log.e("SkullAudio", "Error when recording audio.");
    			android.util.Log.e("SkullAudio", e.getMessage());
    		} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadPaddingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
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
				
				byte[] buf;
				byte[] keybuf = new byte[16];
				SecretKey cryptoKey, hashKey;
				
				int sampleRate, channelConfig, format;
				AudioTrack track;
				
				if(soundSock.isConnected() && !soundSock.isClosed())
				{
					rawAudio.readFully(keybuf);
					cryptoKey = new SecretKeySpec(keybuf, AES);
					rawAudio.readFully(keybuf);
					hashKey = new SecretKeySpec(keybuf, HMAC);
					
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
					track = new AudioTrack(AudioManager.STREAM_VOICE_CALL, sampleRate, channelConfig, format, AudioTrack.getMinBufferSize(sampleRate, channelConfig, format)*32, AudioTrack.MODE_STREAM);

				}
				else
				{
					rawAudio.close();
					soundSock.close();
					return;
				}
				android.util.Log.d("SkullAudio", "Track initialized. Playing back data.");
				
				SkullMessageFactory SMF = new SkullMessageFactory(cryptoKey, hashKey);
				buf = new byte[MESSAGE_SIZE + SMF.getHashSize()];
			
				while(soundSock.isConnected() && ! soundSock.isClosed())
				{
					android.util.Log.d("SkullAudio", "Beginning read");
					
					rawAudio.readFully(buf);
					
					SkullMessage m = SMF.readMessage(buf);
					
					track.write(m.getData(), 0, m.getData().length);
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
    			android.util.Log.e("SkullAudio", "Error when reading from stream.");
    			e.printStackTrace();
    		} catch (NoSuchAlgorithmException e) {
    			android.util.Log.e("SkullAudio", "Bad encryption algorithm spec");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				android.util.Log.e("SkullAudio","Something weird with the padding.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				android.util.Log.e("SkullAudio","Wrong key for this algorithm.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				android.util.Log.e("SkullAudio","Bad block size for algorithm.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadPaddingException e) {
				android.util.Log.e("SkullAudio", "Something's messed up with padding.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (TrickeryException e) {
				android.util.Log.e("SkullAudio","Bad hash on message.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
}
