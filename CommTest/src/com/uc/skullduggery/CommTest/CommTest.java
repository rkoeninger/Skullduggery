package com.uc.skullduggery.CommTest;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.media.*;
import java.io.*;
import java.net.*;

public class CommTest extends Activity{

    final int bitRate = 8000;
    final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
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
	
    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
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
    	}
    	public void run(){
    		try{
    			ServerSocket waitSocket = new ServerSocket(port);
    			waitSocket.setSoTimeout(100); // wait 100 ms before looping
    			while (commSocket == null){
    				
    				try{
    					commSocket = waitSocket.accept();
    				}catch (SocketTimeoutException ste){
    					// Consider the exception throw the same as
    					// a normal function return
    				}
    				
    				if ((listenThread != null) || (talkThread != null) ||
    				(connectThread != null))
    					return;
    			}
    			listenThread = new ListenThread();
    			talkThread = new TalkThread();
    			listenThread.start();
    			talkThread.start();
    			acceptThread = null;
    		}catch (Exception e){
    			h.sendMessage(Message.obtain(h, 0, "error in accept thread"));
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
    	}
    	public void run(){
    		try{
    			commSocket = new Socket(ip, port);
    			listenThread = new ListenThread();
    			talkThread = new TalkThread();
    			listenThread.start();
    			talkThread.start();
    			connectThread = null;
    		}catch (Exception e){
    			h.sendMessage(Message.obtain(h, 0, "connect fail"));
    			throw new Error(e);
    		}
    	}
    }
    
    public class ListenThread extends Thread{
    	public void run(){
            int bytesRead = 0;
            
            AudioTrack aout = null;
            
            try{

            	DataInputStream in = new DataInputStream(
            	commSocket.getInputStream());

            	// These statements not needed as format is fixed
            	//bitRate = in.readInt();
            	//channelConfig = in.readInt();
            	//encoding = in.readInt();
            	
            	byte[] buf = new byte[1024];
            	
            	aout = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
            	bitRate, channelConfig, encoding,
            	AudioTrack.getMinBufferSize(bitRate, channelConfig, encoding),
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
            		h.sendMessage(Message.obtain(h, 0,
            		"io error in listen thread"));
            		throw new Error(e);
            	}
            }
    	}
    }
    
    public class TalkThread extends Thread{
    	public void run(){
    		final int source = MediaRecorder.AudioSource.VOICE_RECOGNITION;
            final int bufSize = AudioRecord.getMinBufferSize(
            bitRate, channelConfig, encoding);
            int bytesRead = 0;
            
            AudioRecord ain = null;
            
            try{
            
            	DataOutputStream out = new DataOutputStream(
            	commSocket.getOutputStream());
            	
            	// These statements not needed as format is fixed
            	//out.writeInt(bitRate);
                //out.writeInt(channelConfig);
                //out.writeInt(encoding);
            	
            	byte[] buf = new byte[1024];
            	
            	ain = new AudioRecord(
            	source, bitRate, channelConfig, encoding, bufSize);
            	ain.startRecording();
            	
                while (ain.getRecordingState() ==
                AudioRecord.RECORDSTATE_RECORDING && commSocket.isConnected()){
                	if ((bytesRead = ain.read(buf, 0, buf.length)) >= 0)
                		out.write(buf, 0, bytesRead);
                	else
                		ain.stop();
                }
            	
            }catch (IOException e){
            	if (! (e instanceof EOFException)){
            		h.sendMessage(Message.obtain(h, 0,
            		"io error in talk thread"));
            		throw new Error(e);
            	}
            }
            
            if (ain != null)
            	if (ain.getRecordingState() ==
            	AudioRecord.RECORDSTATE_RECORDING)
            		ain.stop();
            h.sendMessage(Message.obtain(h, 0, "recording stopped"));
            talkThread = null;
            
    	}
    }
    
}