package edu.uc.skullduggery;

import java.math.BigInteger;
import android.os.Handler;
import android.os.Message;

/**
 * This class is a collection of threads that constitute the process
 * of an encrypted conversation and the data related to it.
 * 
 * A single instance should be all that is necessary for as long as the
 * app runs.
 */
public class SkullTalkService{
	
	public static enum CallState
	{LISTENING, CALLING, TALKING};
	
	private BigInteger transmitKey;
	private BigInteger receiveKey;
	private CallState callState;
	private Thread callThread;
	private Thread acceptThread;
	private Thread transmitThread;
	private Thread receiveThread;
	private Handler uiHandler;
	
	public SkullTalkService(Handler uiHandler){
		this.uiHandler = uiHandler;
	}
	
	public void start(){
		// Contact server to register ip info
		listen();
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
		transmitThread = new TransmitThread();
		receiveThread = new ReceiveThread();
		transmitThread.start();
		receiveThread.start();
		uiHandler.handleMessage(
		Message.obtain(uiHandler, 1));
	}
	
	// called if error reported by receiveThread or transmitThread
	// or if callThread fails.
	// OR if user hangs-up.
	private synchronized void endComm(){
		callState = CallState.LISTENING;
		transmitThread = null;
		receiveThread = null;
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
		public CallThread(String number){
			this.number = number;
		}
		public void run(){
			// Opens port to listener on receiving phone/
			//                      request direction from switch station
			
			// Open a socket between the phones
			
			// Write first packet including our encrypt key
			
			// Read response packet from receiving phone
			
			// ^^^This is the handshake protocol
			
			/* Start transmit thread
			 * Start receive thread
			 * Notify Activity that call has started through a callback,
			 *     so it can update the UI
			 * End this thread
			 * Any running AcceptThread should stop also
			 * 
			 * ^^^ This is all done by the call below
			 */
			SkullTalkService.this.startComm();
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
			// Receives call (srvskt.accept();)/
			//               reads call after polling switch station
			
			//At this point, a socket is opened between the phones
			
			//Read first packet from calling phone which includes
			//encrypt key
			
			//Send a packet which includes this phone's public key
			
			//^^^This is the handshake procedure
			
			
			/* Start transmit thread
			 * Start receive thread
			 * Notify Activity that call has started through a callback,
			 *     so it can update the UI
			 * End this thread
			 * 
			 * ^^^ This is all done by the call below
			 */
			SkullTalkService.this.startComm();
			
			
		}
	}
	
	/**
	 * Reads data from microphone, encrypts and transmits
	 * voice packets.
	 * 
	 * Started by SkullTalkService after AcceptThread
	 * notifies it of an incoming call.
	 * 
	 * Stopped by SkullTalkService if user hangs up or
	 * if ReceiveThread gets a hang-up signal.
	 */
	public class TransmitThread extends Thread{
		public void run(){
			// check that the call is still in progress and socket is open
			if (SkullTalkService.this.callState != CallState.TALKING){
				return;
			}
			
			// read a block of data from the audio buffer
			
			// perform block cipher
			
			// write voice data packet to other phone
			
			
			//If at any point, call has ended or connect error,
			//                  end this thread
			SkullTalkService.this.endComm();
		}
	}
	
	/**
	 * Reads data off of the data connection, decrypts and
	 * plays back over speaker/headset.
	 * 
	 * Started by SkullTalkService after AcceptThread notifies
	 * it of an incoming call.
	 * 
	 * Stopped by SkullTalkService if user hangs up or
	 * if a hang-up signal is read from the other phone.
	 */
	public class ReceiveThread extends Thread{
		public void run(){
			// check that the call is still in progress and socket is open
			if (SkullTalkService.this.callState != CallState.TALKING){
				return;
			}
			
			// read a packet from the socket
			
			// perform block cipher
			
			// write voice data to audio buffer
			
			
			//If at any point, call has ended or connect error,
			//                  end this thread
			SkullTalkService.this.endComm();
		}
	}

}
