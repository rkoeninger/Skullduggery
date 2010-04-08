package com.uc.skullduggery.CommTest;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.media.*;
import java.io.*;
import java.net.*;

public class CommTest extends Activity{

    final int sampleRate = 8000;
    final int channelConfigIn = AudioFormat.CHANNEL_IN_MONO;
    final int channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
    final int encoding = AudioFormat.ENCODING_PCM_16BIT;
    
    // Make sure ports are forwarded between emulators before starting call
    final String ip = "10.0.2.2";
    final int port = 9002;
	
	private AcceptThread acceptThread;
	private ConnectThread connectThread;
	private ListenThread listenThread;
	private TalkThread talkThread;
	private Socket commSocket;
	
	final Handler h = new Handler(){
		public void handleMessage(Message m){
			if (m.what == 0){
				((TextView) findViewById(R.id.textView1)
				).append(((String) m.obj) + "\n");
			}
		}
	};
	
	public void onSaveInstanceState(Bundle out){
        android.util.Log.i("CommTest", "onSaveInstanceState");
        
		out.putBoolean("talking", commSocket != null);
	}
	
	public void onRestoreInstanceState(Bundle savedInstanceState){
        final Button callButton = ((Button) findViewById(R.id.callButton));
        final Button acceptButton = ((Button) findViewById(R.id.acceptButton));
        
        android.util.Log.i("CommTest", "onRestoreInstanceState");
        android.util.Log.i("CommTest", "    bundle is " +
                (savedInstanceState == null ? "NULL" : "not null"));
                
        
        if (savedInstanceState != null){
	        if (savedInstanceState.getBoolean("talking", false)){
	    		callButton.setEnabled(false);
	    		acceptButton.setEnabled(false);
	        }
        }
	}
	
	public void onStart(){
		super.onStart();
        android.util.Log.i("CommTest", "onStart");
	}

	public void onStop(){
		super.onStop();
        android.util.Log.i("CommTest", "onStop");
	}
	
	public void onPause(){
		super.onPause();
        android.util.Log.i("CommTest", "onPause");
	}
	
	public void onResume(){
		super.onResume();
        android.util.Log.i("CommTest", "onResume");
	}
	
	public void onDestroy(){
		super.onDestroy();
        android.util.Log.i("CommTest", "onDestroy");
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        android.util.Log.i("CommTest", "onCreate");
        android.util.Log.i("CommTest", "    bundle is " +
        (savedInstanceState == null ? "NULL" : "not null"));
        
        final Button callButton = ((Button) findViewById(R.id.callButton));
        final Button acceptButton = ((Button) findViewById(R.id.acceptButton));
        
        callButton.setOnClickListener(new View.OnClickListener(){
        	public void onClick(View v){
        		callButton.setEnabled(false);
        		acceptButton.setEnabled(false);
        		acceptThread = null;
        		listenThread = null;
        		talkThread = null;
                connectThread = new ConnectThread(ip, port);
                connectThread.start();
        	}
        });
        
        acceptButton.setOnClickListener(new View.OnClickListener(){
        	public void onClick(View v){
        		callButton.setEnabled(false);
        		acceptButton.setEnabled(false);
        		connectThread = null;
        		listenThread = null;
        		talkThread = null;
                acceptThread = new AcceptThread(port);
                acceptThread.start();
        	}
        });
    }
    
    public class AcceptThread extends Thread{
    	private int port;
    	public AcceptThread(int port){
    		this.port = port;
    		android.util.Log.d("CommTest", "accept thread created");
    	}
    	public void run(){
    		try{
    			android.util.Log.d("CommTest", "creating server socket");
    			ServerSocket waitSocket = new ServerSocket(port);
    			waitSocket.setSoTimeout(10000); // wait 100 ms before looping
    			Socket newConnection = null;
    			while (commSocket == null){
    				try{
    					android.util.Log.d("CommTest", "attempt socket wait");
    					newConnection = waitSocket.accept();
    				}catch (SocketTimeoutException ste){
    					// Consider the exception throw the same as
    					// a normal function return with val "false"
    				}
    				
    				if ((listenThread != null) || (talkThread != null) ||
    				(connectThread != null) || (acceptThread == null)){
    	    			android.util.Log.d("CommTest",
    	    			"accept thread ending early");
    					return;
    				}
    				else if (newConnection != null){
    					commSocket = newConnection;
    					android.util.Log.d("CommTest", "socket connected");
    				}
    			}
    			android.util.Log.d("CommTest", "starting threads");
    			listenThread = new ListenThread();
    			talkThread = new TalkThread();
    			listenThread.start();
    			talkThread.start();
    			android.util.Log.d("CommTest", "threads started");
    			acceptThread = null;
    			android.util.Log.d("CommTest", "accept thread end");
    		}catch (Exception e){
    			android.util.Log.e("CommTest", "error in accept thread");
    			throw new Error(e);
    		}
    	}
    }
    
    public class ConnectThread extends Thread{
    	private String ip;
    	private int port;
    	public ConnectThread(String ip, int port){
    		this.ip = ip;
    		this.port = port;
    		android.util.Log.d("CommTest", "connect thread created");
    	}
    	public void run(){
    		try{
    			android.util.Log.d("CommTest", "attempting socket connect");
    			commSocket = new Socket(ip, port);
    			android.util.Log.d("CommTest", "socket connected");
    			android.util.Log.d("CommTest", "starting threads");
    			listenThread = new ListenThread();
    			talkThread = new TalkThread();
    			listenThread.start();
    			talkThread.start();
    			android.util.Log.d("CommTest", "threads started");
    			connectThread = null;
    			android.util.Log.d("CommTest", "connect thread end");
    		}catch (Exception e){
    			android.util.Log.e("CommTest", "connect fail");
    			throw new Error(e);
    		}
    	}
    }
    
    public class ListenThread extends Thread{
    	public void run(){
    		
    		android.util.Log.d("CommTest", "LISTEN thread started");
    		
            int bytesRead = 0;
            
            AudioTrack aout = null;
            
            try{

            	DataInputStream in = new DataInputStream(
            	commSocket.getInputStream());

            	// These statements not needed as format is fixed
            	//sampleRate = in.readInt();
            	//channelConfig = in.readInt();
            	//encoding = in.readInt();
            	
            	byte[] buf = new byte[1024];
            	
            	aout = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
            	sampleRate, channelConfigOut, encoding,
            	AudioTrack.getMinBufferSize(
            	sampleRate, channelConfigOut, encoding),
            	AudioTrack.MODE_STREAM);

            	while (commSocket.isConnected() && ! commSocket.isClosed()){
            		if ((bytesRead = in.read(buf, 0, buf.length)) >= 0)
            			aout.write(buf, 0, bytesRead);
            		else break;
                    if (aout.getPlayState() != AudioTrack.PLAYSTATE_PLAYING){
                        aout.play();
                    }
            	}
            	
            	aout.stop();
            	commSocket.close();
            	commSocket = null;
            	
            }catch (Exception e){
            	if (! (e instanceof EOFException)){
            		android.util.Log.e("CommTest",
            		"io error in listen thread");
            		throw new Error(e);
            	}
            }
    	}
    }
    
    public class TalkThread extends Thread{
    	public void run(){
    		
    		android.util.Log.d("CommTest", "TALK thread started");
    		
    		final int source = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            final int bufSize = AudioRecord.getMinBufferSize(
            sampleRate, channelConfigIn, encoding);
            int bytesRead = 0;
            
            AudioRecord ain = null;
            
            try{
            
            	DataOutputStream out = new DataOutputStream(
            	commSocket.getOutputStream());
            	
            	// These statements not needed as format is fixed
            	//out.writeInt(sampleRate);
                //out.writeInt(channelConfig);
                //out.writeInt(encoding);
            	
            	byte[] buf = new byte[1024];
            	
            	ain = new AudioRecord(
            	source, sampleRate, channelConfigIn, encoding, bufSize);
            	ain.startRecording();
            	
            	//random freq between 300 and 500 hz
            	final int FREQ = 300+(new java.util.Random().nextInt(200));
            	android.util.Log.d("CommTest", "talkthread freq=" + FREQ);
            	
                while (ain.getRecordingState() ==
                AudioRecord.RECORDSTATE_RECORDING && commSocket.isConnected()){
//                	if ((bytesRead = ain.read(buf, 0, buf.length)) >= 0)
//                		out.write(buf, 0, bytesRead);
//                	else
//                		ain.stop();
                	short sample;
                	for (int x=0;x<buf.length;x+=2){
                		sample=(short)(32767*Math.sin(
                			(2*3.1415926*FREQ)/sampleRate
                		));
                		out.write(sample&0xff);
                		out.write((sample>>8)&0xff);
                	}
                }
            	
            }catch (IOException e){
            	if (! (e instanceof EOFException)){
            		android.util.Log.e("CommTest",
            		"io error in talk thread");
            		throw new Error(e);
            	}
            }
            
            if (ain != null)
            	if (ain.getRecordingState() ==
            	AudioRecord.RECORDSTATE_RECORDING)
            		ain.stop();
    		android.util.Log.d("CommTest", "recording stopped");
            talkThread = null;
            
    	}
    }
}