package edu.uc.skullduggery.x;

import android.app.*;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.util.Log;
import java.io.*;
import java.net.*;

public class CommTestST extends Activity {
	
	private static final String TAG = "CommTest";
	
    final int sampleRate = 8000;
    final int channelConfigIn = AudioFormat.CHANNEL_IN_MONO;
    final int channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
    final int encoding = AudioFormat.ENCODING_PCM_16BIT;
    
    // Make sure ports are forwarded between emulators before starting call
    final String ip = "10.0.2.2";
    final int port = 9002;
    
	private CommThread commThread;
	private Socket commSocket;
	
	public void onSaveInstanceState(Bundle out){
        Log.i(TAG, "onSaveInstanceState");
		out.putBoolean("talking", commSocket != null);
	}
	
	public void onRestoreInstanceState(Bundle savedInstanceState){
        final Button callButton = ((Button) findViewById(R.id.callButton));
        final Button acceptButton = ((Button) findViewById(R.id.acceptButton));
        
        Log.i(TAG, "onRestoreInstanceState");
        Log.i(TAG, "    bundle is " +
                (savedInstanceState == null ? "NULL" : "not null"));
                
        
        if (savedInstanceState != null){
	        if (savedInstanceState.getBoolean("talking", false)){
	    		callButton.setEnabled(false);
	    		acceptButton.setEnabled(false);
	        }
        }
	}

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Log.i(TAG, "onCreate");
        Log.i(TAG, "    bundle is " +
        (savedInstanceState == null ? "NULL" : "not null"));
        
        final Button callButton = ((Button) findViewById(R.id.callButton));
        final Button acceptButton = ((Button) findViewById(R.id.acceptButton));
        
        acceptButton.setOnClickListener(new View.OnClickListener(){
        	public void onClick(View v){
        		callButton.setEnabled(false);
        		acceptButton.setEnabled(false);
        		try{
        			ServerSocket waitSocket = new ServerSocket(port);
        			waitSocket.setSoTimeout(100);
        			Socket newConnection = null;
        			while (commSocket == null){
        				try{
        					newConnection = waitSocket.accept();
        				}catch (SocketTimeoutException ste){
        					// Consider the exception throw the same as
        					// a normal function return with val "false"
        				}
        				if (commThread != null){
        	    			Log.d(TAG, "accept thread ending early");
        					return;
        				}
        				else if (newConnection != null){
        					commSocket = newConnection;
        				}
        			}
            		commThread = new CommThread();
            		commThread.start();
        		}catch (Exception e){
        			Log.e(TAG, "error in accept thread");
        			throw new Error(e);
        		}
        	}
        });
        
        callButton.setOnClickListener(new View.OnClickListener(){
        	public void onClick(View v){
        		callButton.setEnabled(false);
        		acceptButton.setEnabled(false);
        		try{
        			Log.d(TAG, "attempting socket connect");
        			Socket newConnection = new Socket();
        			while (! newConnection.isConnected()){
        				try{
        					newConnection.connect(
        					new InetSocketAddress(ip, port), 100);
        				}catch (SocketTimeoutException ste){
        					// Consider the exception throw the same as
        					// a normal function return with val "false"
        				}
        				if (commSocket != null){
        	    			Log.d(TAG, "call thread ending early");
        					return;
        				}
        			}
        			commSocket = newConnection;
            		commThread = new CommThread();
            		commThread.start();
        		}catch (Exception e){
        			Log.e(TAG, "connect fail");
        			throw new Error(e);
        		}
        	}
        });
    }
	
    public class CommThread extends Thread{
    	public CommThread(){}
    	public void run(){
    		
    		DataInputStream in;
    		DataOutputStream out;
    		AudioTrack aout;
    		AudioRecord ain;
    		
    		try{
    			
    			byte[] buf = new byte[4000];
    			int bytesRead = 0;
    			long localPacketSeq = 0;
    			long localPacketTime = 0;
    			long remotePacketSeq = 0;
    			long remotePacketTime = 0;
    			
    			in = new DataInputStream(commSocket.getInputStream());
    			out = new DataOutputStream(commSocket.getOutputStream());
    			
    			aout = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
    	        sampleRate, channelConfigOut, encoding, buf.length,
    	        AudioTrack.MODE_STREAM);
    			
    			ain = new AudioRecord(
    			MediaRecorder.AudioSource.VOICE_RECOGNITION,
    			sampleRate, channelConfigIn, encoding, buf.length);
    	        
    			ain.startRecording();
    			
    			while (commSocket.isConnected() && ! commSocket.isClosed() &&
    			ain.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING){

    				localPacketTime = System.currentTimeMillis();
    				bytesRead = ain.read(buf, 0, buf.length);
    				bytesRead = resample8000To4000(buf, 0, bytesRead);
    				
    				out.writeLong(localPacketSeq);
    				out.writeLong(localPacketTime);
    				out.writeInt(bytesRead);
    				
    				if (bytesRead < 0) break;
    				
    				out.write(buf, 0, bytesRead);
    				
    				remotePacketSeq = in.readLong();
    				remotePacketTime = in.readLong();
    				bytesRead = in.readInt();
    				
    				if (bytesRead < 0) break;
    				
    				if (localPacketSeq > remotePacketSeq){
    					Log.w(TAG, "remote phone fell behind");
    					continue;
    				}else if (localPacketSeq < remotePacketSeq){
    					Log.w(TAG, "remote phone got ahead");
    					localPacketSeq = remotePacketSeq;
    					continue;
    				}
    				
    				in.readFully(buf, 0, bytesRead);
    				bytesRead = resample4000To8000(buf, 0, bytesRead);
    				aout.write(buf, 0, bytesRead);
    				
                    if (aout.getPlayState() !=
                    AudioTrack.PLAYSTATE_PLAYING)
                        aout.play();
                    
                    localPacketSeq += 1;
    				
                    if ((localPacketSeq-1) % 32 == 0){
                    	android.util.Log.d(TAG,
                    	"pkt#=" + localPacketSeq +
                    	"datalen=" + bytesRead +
                    	"transtime=" +
                    	(System.currentTimeMillis() - remotePacketTime));
                    }
                    
    			}
    			
            	commSocket.close();
            	commSocket = null;
            	
    		}catch (Exception e){
    			throw new Error(e);
    		}
    		
            if (ain != null)
            	if (ain.getRecordingState() ==
            	AudioRecord.RECORDSTATE_RECORDING)
            		ain.stop();
            
            if (aout != null)
            	if (aout.getPlayState() ==
            	AudioTrack.PLAYSTATE_PLAYING)
            		aout.stop();
            
            commThread = null;
            
    	}
    }
    
    private static int resample8000To4000(byte[] data, int off, int len){
    	int resampledLength = len / 2;
    	byte[] resampledData = new byte[resampledLength];
    	for (int x = 0; x < resampledLength; x++){
    		resampledData[x] = data[off + (2 * x)];
    	}
    	System.arraycopy(resampledData, 0, data, off, resampledLength);
    	return resampledLength;
    }
    private static int resample4000To8000(byte[] data, int off, int len){
    	int resampledLength = len * 2;
    	byte[] resampledData = new byte[resampledLength];
    	for (int x = 0; x < resampledLength; x += 2){
    		resampledData[x] = resampledData[x + 1] = data[off + x];
    	}
    	System.arraycopy(resampledData, 0, data, off, resampledLength);
    	return resampledLength;
    }
}