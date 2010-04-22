package edu.uc.skullduggery;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;

import edu.uc.skullduggery.SkullMessage.MessageType;

import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;

/**
 * This class is a collection of threads that constitute the process
 * of an encrypted conversation and the data related to it.
 * 
 * A single instance should be all that is necessary for as long as the
 * app runs.
 */

public class SkullTalkService{
	
	public static enum CallState
	{LISTENING, CALLING, TALKING, STOPPED};
	
	private CallState callState;
	private Thread callThread;
	private Thread acceptThread;
	private Thread talkThread;
	private Handler uiHandler;
	private ContextWrapper appContext;
	private SkullKeyManager keyManager;
	private SkullMessageFactory messageFact;
	private final String phoneNumber;
	private Socket callSocket;
	
	private SwitchStationClient serverComm;
	private String serverIP = "10.0.2.2";
	private int serverPort = 9002;
	
	public SkullTalkService(Handler uiHandler, ContextWrapper context){
		this.uiHandler = uiHandler;
		
		phoneNumber = ((TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE)).getLine1Number();
		
		serverComm = new SwitchStationClient();
	}
	
	public void start(){
		//TODO: Read public key from file
		keyManager = new SkullKeyManager(appContext);
	
		serverComm.connect(serverIP, serverPort);
		serverComm.register(phoneNumber, new byte[]{1,1,1,1}, 9001);
		
		// Contact server to register ip info
		listen();
	}
	
	public synchronized void stop(){
		serverComm.disconnect();
		if (callState == CallState.TALKING){
			callState = CallState.STOPPED;
			talkThread = null;
			acceptThread = null;
			callThread = null;
			uiHandler.handleMessage(
			Message.obtain(uiHandler, 2));
		}
	}
	
	public void hangup(){
		endComm();
	}
	
	/*
	 * Functions are synchronized so there won't be concurrent changes
	 * to the state of the service from the UI/TransmitThread/ReceiveThread
	 */

	public synchronized void dial(String number){
		callState = CallState.CALLING;//stops accept thread
		acceptThread = null;
		callThread = new CallThread(number);
		callThread.start();
	}
	
	//gets called initially by start() or constructor
	private synchronized void listen(){
		callState = CallState.LISTENING;
		acceptThread = new AcceptThread();
		acceptThread.start();
	}

	//called from callThread or acceptThread once handshake
	// is completed
	private synchronized void startComm(){
		callState = CallState.TALKING;
		callThread = null;
		acceptThread = null;
		talkThread = new TalkThread();
		talkThread.start();
		uiHandler.handleMessage(
		Message.obtain(uiHandler, 1));
	}
	
	// called if error reported by receiveThread or transmitThread
	// or if callThread fails.
	// OR if user hangs-up.
	private synchronized void endComm(){
		callState = CallState.LISTENING;
		talkThread = null;
		uiHandler.handleMessage(
		Message.obtain(uiHandler, 2));
		listen();
	}
	
	/**
	 * Either attempts to open a socket on receiving phone
	 * or accesses "switching station" server. In it's own thread
	 * so the delay in making connection doesn't hang service.
	 * 
	 * Started by SkullTalkService when the user makes a call.
	 * 
	 * Stopped by itself when the connection to the other phone
	 * has been established or the recipient is unreachable or if the
	 * user hangs-up prematurely.
	 */
	public class CallThread extends Thread{
		private String number;
		
		private InetSocketAddress getAddressByNumber(String recNum){
			Object[] retvals = new Object[2];
			if (serverComm.request(number, retvals) != 0){
				return null;
			}
			int ip32 = ((Integer) retvals[0]).intValue();
			String ipString =
			Constants.ipBytesToString(
			Constants.ipIntToBytes(ip32));
			int port32 = ((Short) retvals[1]).shortValue();
			return InetSocketAddress.createUnresolved(ipString, port32);
		}
		public CallThread(String number){
			this.number = number;
		}
		public void run(){
			
			try{
				
			//TODO: Get the IP address of the recipient
			InetSocketAddress receiver = this.getAddressByNumber(this.number);
			
			messageFact = SkullMessageFactory.getInstance();
			SkullMessage mes; 
			//Open connection to called phone
			
			callSocket = new Socket(receiver.getAddress(), receiver.getPort());
			
			InputStream cis = callSocket.getInputStream();
			OutputStream cos = callSocket.getOutputStream();
			
			DataInputStream dis = new DataInputStream(cis);
			DataOutputStream dos = new DataOutputStream(cos);
			//TODO: Send first packet (Own phone number, SKUL magic, etc)
			mes = messageFact.createMessage(phoneNumber.getBytes(), SkullMessage.MessageType.CALL);
			
			SkullMessageFactory.writeMessage(dos, mes);
			
			//read the reply
			//TODO this should have pubkey
			//TODO this should have exp
			//TODO: Get first packet (should be BUSY or ACCEPT or whatever + Pub Key)
			
			mes = SkullMessageFactory.readMessage(dis);
			BigInteger pubExp, pubMod;
			
			//TODO: Get first packets (should be CALL + Number)
			String calledNumber = new String(mes.getData());
			if (calledNumber != number || mes.getType() != MessageType.CALL)
			{
				//TODO disconnect
				//TODO Call handler to tell user that the connection is busy
				//TODO Change call state to disconnected
			}
			
			
			
			//TODO graceful failure if we don't get a PUBEXP, PUBMOD in order OR we get them ooo or something
			//TODO idk.
			if(mes.getType() == MessageType.PUBEXP)
				pubExp = new BigInteger(mes.getData());
				
			
			mes = SkullMessageFactory.readMessage(dis);
			
			if(mes.getType() == MessageType.PUBMOD)
				pubMod = new BigInteger(mes.getData());
			else {
				//TODO Disconnect; Enter accept state, print error.
			}
				
			
			
			
			
			
			//TODO: Check pubKey against stored public key hash

			//TODO: If it's good, good
			//TODO: If it's not good, notify the handler through a callback
			//TODO: If it's new, notify the handler through a callback (different)
			//TODO: Generate a session key
			//TODO: Generate a MAC key
			//TODO: If rejected by user: Send 'reject' packet. Close connection.
			//TODO: Send session, MAC key through encrypted thinger.

			//These are done by the call below:
			//TODO: Start talk thread.
			//TODO: Notify Activity the conversation has started.
			//TODO: Kill this thread, any accept thread
			SkullTalkService.this.startComm();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
	
	/**
	 * Accepts an incoming call, either by polling server
	 * or answering a ServerSocket. Notifies SkullTalkService
	 * when a call has been detected.
	 * 
	 * Started by SkullTalkService initially or once a call
	 * has ended.
	 * 
	 * Stopped once a call has started.
	 */
	public class AcceptThread extends Thread{
		public void run(){
			
			KeyPair keys;
			SkullMessage pubMod, privMod;
			try {
			
				keys = keyManager.getKeys();
				RSAPublicKey pub = (RSAPublicKey) keys.getPublic();
				pubMod = messageFact.createMessage(pub.getModulus().toByteArray(), SkullMessage.MessageType.PUBMOD);
				privMod = messageFact.createMessage(pub.getPublicExponent().toByteArray(), SkullMessage.MessageType.PUBEXP);
				
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvalidKeySpecException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			//TODO: Accept socket connection
			//TODO: Open socket between phones
			//TODO: Read first packet
			//TODO: If talking:
				//TODO: Send reject packet
				//TODO: hang up
			//TODO: If not talking:
			//TODO: Reply with public key, own phone number, etc.
			//TODO: Wait for (encrypted) response; should be session key, MAC key
			//TODO: May be 'reject' packet.
			//TODO: Read Cipher'd stream
			
			//SkullTalkService does these:
			//TODO: Start Talk Thread
			//TODO: Notify Activity that call has started through a callback
			SkullTalkService.this.startComm();
		}
	}
	
	/**
	 * Reads data from microphone, encrypts and transmits
	 * voice packets.
	 * 
	 * Reads data from socket, decrypts and plays voice packets.
	 * 
	 * Started by SkullTalkService after AcceptThread
	 * notifies it of an incoming call.
	 * 
	 * Stopped by SkullTalkService if user hangs up or
	 * if ReceiveThread gets a hang-up signal.
	 */
	public class TalkThread extends Thread{
		public void run(){
			// check that the call is still in progress and socket is open
			if (SkullTalkService.this.callState != CallState.TALKING){
				return;
			}
			//TODO: Import what we've learned from CommTest project into this class.
			SkullTalkService.this.endComm();
		}
	}
}
