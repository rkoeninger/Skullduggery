package edu.uc.skullduggery;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

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

public class SkullAudio extends Activity {

	private final String HMAC = "HmacSHA1";
	private final String AES = "AES";
	private final int MESSAGE_SIZE = 1024;
	
	private ListenThread _listenThread;
	private SpeakThread _speakThread;
	private Socket _soundSock;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        
        setContentView(R.layout.main);
        
        try {
			_soundSock = new Socket("10.0.2.2", 9002);
			_listenThread = new ListenThread(_soundSock);
	        _speakThread = new SpeakThread(_soundSock);
	        _listenThread.start();
	        _speakThread.start();
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
    			//TODO: subroutine for this. Invoking rule-of-3 for now.
    			KeyGenerator cryptoGen = KeyGenerator.getInstance(AES);
    			cryptoGen.init(128);
    			SecretKey cryptoKey = cryptoGen.generateKey();
    			SecretKeySpec cryptoKeySpec = new SecretKeySpec(cryptoKey.getEncoded(), AES);
    			Log.d("SkullAudio", "XMIT: Sending crypto key:" + Util.byteArrayToString(cryptoKeySpec.getEncoded()));
    			KeyGenerator hashGen = KeyGenerator.getInstance(HMAC);
    			hashGen.init(128);
    			SecretKey hashKey = hashGen.generateKey();
    			SecretKeySpec hashKeySpec = new SecretKeySpec(hashKey.getEncoded(), HMAC);
    			Log.d("SkullAudio", "XMIT: Sending hash key:" + Util.byteArrayToString(hashKeySpec.getEncoded()));
        		
    			SkullMessageFactory SMF = new SkullMessageFactory(cryptoKeySpec, hashKeySpec);
    			
    			Log.d("SkullAudio", "XMIT: Beginning audio recording thread.");
	    		int audioSource = MediaRecorder.AudioSource.VOICE_RECOGNITION;
	    		int audioBitRate = 8000; //11025, 22050, 44100
	    		int audioChannelConfig = AudioFormat.CHANNEL_IN_MONO;
	    		int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
	    		int audioBufferSize = AudioRecord.getMinBufferSize(audioBitRate, audioChannelConfig, audioEncoding)*8;
	    		byte[] dataBuffer = new byte[MESSAGE_SIZE];
	    		Log.d("SkullAudio", "XMIT: Initializing output stream.");
	    		DataOutputStream DOS = new DataOutputStream(soundSock.getOutputStream());
	    		
	    		DOS.write(cryptoKeySpec.getEncoded());
	    		DOS.write(hashKeySpec.getEncoded());
	    		
	    		DOS.writeInt(audioBitRate);
	    		//DOS.writeInt(channelConfig);
	    		DOS.writeInt(audioEncoding);
	    		
	    		//FIXME: Remove starting here...
	    		for(int i=0; i<MESSAGE_SIZE; i++)
	    			dataBuffer[i] = (byte) i;
	    		SkullMessage testMessage = SMF.createMessage(dataBuffer);
	    		
	    		Log.d("SkullAudio","XMIT: Sending test message.");
	    		Log.d("SkullAudio","XMIT: GBT1 Test Message:" + Util.byteArrayToString(testMessage.getData()));
	    		
	    		DOS.write(testMessage.getData());
	    		
	    		//FIXME: to here.
	    		
    			Log.d("SkullAudio", "XMIT: Initializing recorder.");
	    		AudioRecord SoundRecorder = new AudioRecord(audioSource, audioBitRate, audioChannelConfig, audioEncoding, audioBufferSize);

    			Log.d("SkullAudio", "XMIT: Starting to record.");
	    		SoundRecorder.startRecording();
	
	    		while(SoundRecorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING && soundSock.isConnected())
	    		{
	    			Log.d("SkullAudio", "XMIT: Reading recorded data.");
	    			int len = 0;
	    			
	    			//Read audio until full buffer.
	    			while(len < dataBuffer.length)
	    				len += SoundRecorder.read(dataBuffer, len, dataBuffer.length - len);
	    			
	    			//Write message containing encrypted audio data.
	    			SkullMessage audioMessage = SMF.createMessage(dataBuffer);
	    			DOS.write(audioMessage.getData());

	    			Log.d("SkullAudio", "XMIT: Recorded data written.");
	    		}

    			Log.d("SkullAudio", "XMIT: Connection closed, etc.");
	    		
	    		SoundRecorder.stop();
    		} 
    		catch (IOException e)
    		{
    			Log.e("SkullAudio", "XMIT: Error when recording audio.");
    			Log.e("SkullAudio", e.getMessage());
    		} catch (InvalidKeyException e) {
				// TODO Auto-generated catch block
    			Log.e("SkullAudio","XMIT: Invalid key for encryption algorithm.");
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
    			Log.e("SkullAudio","XMIT: Bad algorithm constants.");
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				// TODO Auto-generated catch block
				Log.e("SkullAudio","XMIT: Bad padding (message length?)");
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				// TODO Auto-generated catch block
				Log.e("SkullAudio","XMIT: Bad block size (message length?)");
				e.printStackTrace();
			} catch (BadPaddingException e) {
				// TODO Auto-generated catch block
    			Log.e("SkullAudio","XMIT: Bad padding (message length?)");
				e.printStackTrace();
			}
    	}
    }
    
    
    private class ListenThread extends Thread{
    	
    	private Socket skullSocket;
    	
    	public ListenThread(Socket S)
    	{
    		skullSocket = S;
    	}
    	
    	public void run()
    	{
    		try{
    			Log.d("SkullAudio", "RECV: Initializing the reading stream");
    			DataInputStream audioInputStream = new DataInputStream(skullSocket.getInputStream());
				
				byte[] dataBuffer = new byte[MESSAGE_SIZE];
				byte[] hashBuffer = new byte[20];
				byte[] keyBuffer = new byte[16];
				
				int audioSampleRate, audioChannelConfig, audioFormat;
				AudioTrack audioOutput;
				SecretKey cryptoKey, hashKey;
				SkullMessageFactory SMF;
				
				if(skullSocket.isConnected() && !skullSocket.isClosed())
				{
					audioInputStream.readFully(keyBuffer);
					Log.d("SkullAudio", "RECV: Read crypto key: " + Util.byteArrayToString(keyBuffer));
					cryptoKey = new SecretKeySpec(keyBuffer, AES);
					
					audioInputStream.readFully(keyBuffer);
					Log.d("SkullAudio", "RECV: Read hash key: " + Util.byteArrayToString(keyBuffer).toString());
					hashKey = new SecretKeySpec(keyBuffer, HMAC);
					
					
					Log.d("SkullAudio", "RECV: Reading sample rate");
					audioSampleRate = audioInputStream.readInt();
					Log.d("SkullAudio", "RECV: Sample rate:" + Integer.toString(audioSampleRate));
					
					Log.d("SkullAudio", "RECV: Reading channel config");
					audioChannelConfig = AudioFormat.CHANNEL_OUT_MONO;
//					audioChannelConfig = rawAudio.readInt();
					Log.d("SkullAudio", "RECV: Channel Config:" + Integer.toString(audioChannelConfig));
					
					Log.d("SkullAudio", "RECV: Reading audio format");
					audioFormat = audioInputStream.readInt();
					Log.d("SkullAudio", "RECV: Audio format:" + Integer.toString(audioFormat));
					
					Log.d("SkullAudio", "RECV: Initializing track");
					audioOutput = new AudioTrack(AudioManager.STREAM_VOICE_CALL, audioSampleRate, audioChannelConfig, audioFormat, AudioTrack.getMinBufferSize(audioSampleRate, audioChannelConfig, audioFormat)*32, AudioTrack.MODE_STREAM);
				}
				else
				{
					audioInputStream.close();
					skullSocket.close();
					return;
				}
				Log.d("SkullAudio", "RECV: Track initialized. Playing back data.");
				SMF = new SkullMessageFactory(cryptoKey, hashKey);
				
				if(SMF == null)
					SMF = new SkullMessageFactory(cryptoKey, hashKey);
			
				Log.d("SkullAudio", "RECV: Reading test message.");
				audioInputStream.readFully(dataBuffer);

	    		Log.d("SkullAudio","RECV: GBT1 Test Message:" + Util.byteArrayToString(dataBuffer));
	    		
				SMF.readMessage(dataBuffer);
				Log.d("SkullAudio", "RECV: Test message successfully decoded.");
				
				
				while(skullSocket.isConnected() && ! skullSocket.isClosed())
				{
					Log.d("SkullAudio", "RECV: Beginning read");
					
					audioInputStream.readFully(hashBuffer);
					audioInputStream.readFully(dataBuffer);
					
					SkullMessage m = SMF.readMessage(dataBuffer);
					
					audioOutput.write(m.getData(), 0, m.getData().length);
					if(audioOutput.getPlayState() != AudioTrack.PLAYSTATE_PLAYING){
						audioOutput.play();
					}
					Log.d("SkullAudio", "RECV: Written to buffer");
				}
				Log.d("SkullAudio", "RECV: Done. Closing up.");
				
				audioInputStream.close();
				skullSocket.close();
    		}
    		catch (IOException e)
    		{
    			Log.e("SkullAudio", "RECV: Error when playing audio.");
    			Log.e("SkullAudio", "RECV: Error when reading from stream.");
    			e.printStackTrace();
    		} catch (NoSuchAlgorithmException e) {
    			Log.e("SkullAudio", "RECV: Bad encryption algorithm spec");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				Log.e("SkullAudio","RECV: Something weird with the padding.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				Log.e("SkullAudio","RECV: Wrong key for this algorithm.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				Log.e("SkullAudio","RECV: Bad block size for algorithm.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (BadPaddingException e) {
				Log.e("SkullAudio", "RECV: Something's messed up with padding.");
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    }
}
