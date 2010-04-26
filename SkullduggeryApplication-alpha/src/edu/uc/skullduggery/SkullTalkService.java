package edu.uc.skullduggery;

import java.io.*;
import java.math.*;
import java.net.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.util.Arrays;
import edu.uc.skullduggery.SkullMessage.MessageType;
import android.content.*;
import android.media.*;
import android.os.*;
import android.telephony.TelephonyManager;
import android.util.Log;

// TODO: Several here:      *Don't remove this T0D0 marker*

//       Rewrite documentation and COMMENTS as sections are changed.
//       All comments will need constant editing.

//       Call state infrastructure and interface methods
//       i.e. (start/stop/hangup/startComm/endComm)
//       Need to be re-written or removed

//       Communication is currently being done line by line
//        - that means, SkullMessages are not being used.
//       Neither are the writeMessage and readMessage methods.
//       Rewrite may be necessary.

//       SkullMessage or SkullMessage factory need to have encryption built
//       into them. If we had a block of data to transmit, we could call
//       SkullMessageFactory.setCipherInfo(...);
//       SkullMessageFactory.getMessage(data);


//       The handshake method could still use alot of work
//        - I didn't understand key exchange very well
//       So...
//       To keep this simple,
//       Each phone generates/loads it's keys
//       and sends PUB-key, MAC, whatever else in one round.
//       Each phone sends one packet.
//       Following this exchange, we are ready to talk.

//       I ignored the idea of (checking cached key for security reasons)
//       for development speed reasons.

public class SkullTalkService{
	
	/* Logging tag for Android logcat */
	private static final String TAG = "SkullTalk";
	
	/* Audio encoding parameters */
	private static final int sampleRate = 8000;
	private static final int channelConfigIn = AudioFormat.CHANNEL_IN_MONO;
	private static final int channelConfigOut = AudioFormat.CHANNEL_OUT_MONO;
	private static final int encoding = AudioFormat.ENCODING_PCM_16BIT;
	
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
	private SkullUserInfoManager userManager;
	private final String phoneNumber;
	private Socket callSocket;
	
	/**
	 * Information about the remote phone current talking with.
	 * Specifically, the phone number.
	 * Should be null when callState does not equal TALKING.
	 */
	private String remotePhoneNumber;
	
	/**
	 * Contact info and interface for the proxy (switch station) server.
	 */
	private SwitchStationClient serverComm;
	private String serverIP = "10.0.2.2";
	private int serverPort = 9002;
	
	public SkullTalkService(Handler uiHandler, ContextWrapper context){
		this.uiHandler = uiHandler;
		
		phoneNumber = ((TelephonyManager) context.getSystemService(
		Context.TELEPHONY_SERVICE)).getLine1Number();
		
		serverComm = new SwitchStationClient();
	}
	
	public synchronized CallState getCallState(){
		return callState;
	}
	
	public synchronized void setCallState(CallState state){
		callState = state;
	}
	
	public void start(){
		//TODO: Read public key from file
		keyManager = new SkullKeyManager(appContext);
		userManager = new SkullUserInfoManager(appContext);
		serverComm.connect(serverIP, serverPort);
		
		// TODO: How to find our own contact info?
//		serverComm.register(phoneNumber, new byte[]{1,1,1,1}, 9001);
		
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
		acceptThread = new AcceptThread(9002);
		acceptThread.start();
	}

	// TODO: All this infrastructure needs to be re-written
	
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
	
	private static void readMagic(DataInputStream dis) throws IOException{
		byte[] magic = new byte[Constants.MAGICBYTES.length];
		dis.readFully(magic, 0, magic.length);
		if (! Arrays.equals(magic, Constants.MAGICBYTES))
			throw new IOException("Wrong magic bytes");
	}
	
	private static void readType(DataInputStream dis, MessageType expectedType)
	throws IOException{
		if (dis.readByte() != MessageType.CALL.ordinal()){
			throw new IOException("Unexpected packet type");
		}
	}
	
	private void writeMessage(DataOutputStream dos, SkullMessage mes)
	throws IOException{
		byte[] hash = mes.getHash();
		byte[] data = mes.getData();
		SkullMessage.MessageType mesType = mes.getType();
		
		dos.write(Constants.MAGICBYTES);
		dos.write(hash);
		dos.writeByte((byte) mesType.ordinal());
		dos.writeInt(data.length);
		dos.write(data);
	}
	
	private SkullMessage readMessage(InputStream s) throws IOException{
		SkullMessage rMessage = null;
		
		//      Read MAC from the stream.
		//      Read Type from the stream.
		//      Read data length from the stream.
		//      Read data from the stream.
		//      Construct a SkullMessage
		//      Return our SkullMessage
		return rMessage;
	}
	
	/**
	 * Key generation and exchange occurs here.
	 */
	private void performHandshake(DataInputStream dis, DataOutputStream dos)
	throws IOException, InvalidKeySpecException {

		/*
		 * Generate local phone's keys for this conversation.
		 */
		KeyFactory keygen;
		KeyPair localKeys;
		BigInteger localPubMod, localPubExp;
		try {
			keygen = KeyFactory.getInstance(Constants.ASYMALGORITHM);
			localKeys = keyManager.getKeys();
			RSAPublicKey pub = (RSAPublicKey) localKeys.getPublic();
			localPubMod = pub.getModulus();
			localPubExp = pub.getPublicExponent();
		} catch (NoSuchAlgorithmException nsae){
			throw new Error(nsae); // Can't recover from this
		}
		
		/*
		 * Write our public key Modulus and Exponent.
		 */
		dos.write(Constants.MAGICBYTES);
		dos.write((byte) MessageType.PUBMOD.ordinal());
		dos.writeInt(localPubMod.toByteArray().length);
		dos.write(localPubMod.toByteArray());
		dos.write(Constants.MAGICBYTES);
		dos.write((byte) MessageType.PUBEXP.ordinal());
		dos.writeInt(localPubExp.toByteArray().length);
		dos.write(localPubExp.toByteArray());
		
		/*
		 * Read remote phone's public key Modulus and Exponent.
		 */
		readMagic(dis);
		readType(dis, MessageType.PUBMOD);
		byte[] remotePubModBytes = new byte[dis.readInt()];
		dis.readFully(remotePubModBytes);
		readMagic(dis);
		readType(dis, MessageType.PUBEXP);
		byte[] remotePubExpBytes = new byte[dis.readInt()];
		dis.readFully(remotePubExpBytes);
		BigInteger remotePubMod = new BigInteger(remotePubModBytes);
		BigInteger remotePubExp = new BigInteger(remotePubExpBytes);
		
		/*
		 * Generate a public key for the remote phone from the
		 * MOD and EXP parts received.
		 */
		RSAPublicKeySpec remotePubKeySpec =
		new RSAPublicKeySpec(remotePubMod, remotePubExp);
		PublicKey remotePublicKey = keygen.generatePublic(remotePubKeySpec);
		
		// TODO: figure this out:
			// Generate a session key
			// Generate a MAC key
			// If rejected by user: Send 'reject' packet. Close connection.
			// Send session, MAC key through encrypted thinger.

	}
	
	public class CallThread extends Thread{
		private String remotePhoneNumber;
		public CallThread(String number){ remotePhoneNumber = number; }
		public void run(){
			try{
				
				/*
				 * Get the contact info of the remote phone from server.
				 * If could not get info, report error to UI and quit.
				 */
				Object[] retvals = new Object[2];
				serverComm.request(remotePhoneNumber, retvals);
				int ip32 = ((Integer) retvals[0]).intValue();
				String ipString =
				Constants.ipBytesToString(
				Constants.ipIntToBytes(ip32));
				int port32 = ((Short) retvals[1]).shortValue();
				InetSocketAddress remotePhoneAddress =
				InetSocketAddress.createUnresolved(ipString, port32);
				
				/* 
				 * Open connection to remote phone.
				 * This is analogous to dialing.
				 * Makes 300 connect attempts at 100ms a piece for 30s timeout.
				 * Open input and output streams.
				 * If could not connect, report error to UI and cleanup + quit.
				 */
				callSocket = new Socket();
				for (int x = 0; x < 300; ++x){
					try{
						callSocket.connect(remotePhoneAddress, 100);
					}catch (SocketTimeoutException ste){
						continue;
					}catch (Exception e){
						throw new Error(e);
					}
				}
				if (! callSocket.isConnected()){
					throw new SocketException();
				}
				DataInputStream dis =
				new DataInputStream(callSocket.getInputStream());
				DataOutputStream dos =
				new DataOutputStream(callSocket.getOutputStream());
	
				/*
				 * The calling phone first sends a CALL packet containing
				 * info about the caller and calling phone.
				 */
				// TODO: Rewrite this as a SkullMessage
				dos.write(Constants.MAGICBYTES);
				dos.write((byte) MessageType.CALL.ordinal());
				dos.writeInt(phoneNumber.length());
				dos.write(phoneNumber.getBytes());
				
				/*
				 * Make sure we receive an ACCEPT packet in return or fail.
				 */
				// TODO: Rewrite this as a SkullMessage
				readMagic(dis);
				byte messageType = dis.readByte();
				if (messageType == MessageType.BUSY.ordinal()){
					throw new IOException("Remote phone busy");
				}
				if (messageType != MessageType.ACCEPT.ordinal()){
					throw new IOException("Unexpected packet type");
				}

				/*
				 * Both phones at this point perform the handshake.
				 * The handshake is the same for
				 * calling (CallThread) and receiving (AcceptThread)
				 * phones.
				 */
				performHandshake(dis, dos);
				
				/*
				 * At this point, we have exchanged keys and are ready
				 * to talk.
				 * Start TalkThread and end.
				 */
				// TODO: this isn't finished,
				//       talk thread needs to be given cipher in constructor
				//       and infrastructure needs to be built.
				talkThread = new TalkThread();
				talkThread.start();
				callThread = null;
				
			}catch (InvalidKeySpecException ikse){
				// TODO: this error is possibly caused by other
				//       phone sending bad key parts
				//       notify user
				if (callSocket.isConnected()){
					try{ callSocket.close(); }
					catch (IOException ioe2){}
				}
				callSocket = null;
			}catch (IOException ioe){
				// TODO: Notify user of connect error
				//       Can do error-specific exception handling later
				if (callSocket.isConnected()){
					try{ callSocket.close(); }
					catch (IOException ioe2){}
				}
				callSocket = null;
			}
		}
	}
	
	public class AcceptThread extends Thread{
		private int listenPort;
		public AcceptThread(int port){ listenPort = port; }
		public void run(){
			
			/*
			 * Initialize server socket.
			 * Set listen port and timeout.
			 * If this fails, then we won't be able to receive calls. 
			 */
			ServerSocket serverSocket;
			try{
				serverSocket = new ServerSocket(listenPort);
				serverSocket.setSoTimeout(100);
			}catch (IOException e){
				throw new Error(); // Can't continue
			}

			/*
			 * Main call accept loop.
			 * Each iteration, it attempts to accept an incoming connection
			 * and respond.
			 * If the accept() call times-out, the loop can check for
			 * application status changes (closing) and try to accept() again.
			 */
			while (callState != CallState.STOPPED){
				Socket newConnection;
				
				try{
					
					/*
					 * Wait for a connection.
					 * If connection times-out an exception is thrown
					 * and we just loop again.
					 */
					newConnection = serverSocket.accept();
					DataInputStream dis =
					new DataInputStream(newConnection.getInputStream());
					DataOutputStream dos =
					new DataOutputStream(newConnection.getOutputStream());
					
					/*
					 * Read the initial CALL packet sent by the caller.
					 */
					// TODO: rewrite this as receiving a SkullMessage
					readMagic(dis);
					readType(dis, MessageType.CALL);
					int remotePhoneNumberLength = dis.readInt();
					byte[] remotePhoneNumberBytes =
					new byte[remotePhoneNumberLength];
					dis.readFully(remotePhoneNumberBytes);
					remotePhoneNumber = new String(remotePhoneNumberBytes);

					/*
					 * After receiving the CALL packet,
					 * return an ACCEPT or a BUSY packet.
					 */
					if (callState == CallState.TALKING){
						// TODO: rewrite this as sending a SkullMessage
						dos.write(Constants.MAGICBYTES);
						dos.write((byte) MessageType.BUSY.ordinal());
						continue;
					} else {
						// TODO: rewrite this as sending a SkullMessage
						dos.write(Constants.MAGICBYTES);
						dos.write((byte) MessageType.ACCEPT.ordinal());
					}
					
					/*
					 * Both phones at this point perform the handshake.
					 * The handshake is the same for
					 * calling (CallThread) and receiving (AcceptThread)
					 * phones.
					 */
					performHandshake(dis, dos);
					
					/*
					 * At this point, we have exchanged keys and are ready
					 * to talk.
					 * Start TalkThread and end.
					 */
					// TODO: this isn't finished,
					//       talk thread needs to be
					//           given cipher in constructor
					//       and infrastructure needs to be built.
					talkThread = new TalkThread();
					talkThread.start();
					acceptThread = null;
					
				}catch (SocketTimeoutException ste){
					if (callState == CallState.STOPPED){
						acceptThread = null;
						return;
					}
					continue; // Loop again if timeout
				}catch (InvalidKeySpecException ikse){
					// TODO: likely caused by bad key parts sent
					//       by remote phone, perhaps give user this
					//       specific error message?
					if (callSocket.isConnected()){
						try{ callSocket.close(); }
						catch (IOException ioe2){}
					}
					callSocket = null;
				}catch (IOException ioe){
					// TODO: Handle errors somehow, report to user
					if (callSocket.isConnected()){
						try{ callSocket.close(); }
						catch (IOException ioe2){}
					}
					callSocket = null;
				}
			}
		}
	}
	
	public class TalkThread extends Thread{
		public void run(){

			DataInputStream in;
			DataOutputStream out;
			AudioTrack aout = null;
			AudioRecord ain = null;
			
			try{
				
				byte[] buf = new byte[4000];
				int bytesRead = 0;
				long localPacketSeq = 0;
				long remotePacketSeq = 0;
				
				/*
				 * Open streams and audio tracks.
				 */
				in = new DataInputStream(callSocket.getInputStream());
				out = new DataOutputStream(callSocket.getOutputStream());
				aout = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
						sampleRate, channelConfigOut, encoding, buf.length,
						AudioTrack.MODE_STREAM);
				ain = new AudioRecord(
						MediaRecorder.AudioSource.VOICE_RECOGNITION,
						sampleRate, channelConfigIn, encoding, buf.length);
				ain.startRecording();

				// TODO: This loop needs to respond to changes in call state
				while (callSocket.isConnected()
						&& !callSocket.isClosed()
						&& ain.getRecordingState() ==
						AudioRecord.RECORDSTATE_RECORDING) {

					bytesRead = ain.read(buf, 0, buf.length);
					// TODO: encrypt here
					//       write magic?
					//       Re-write as a SkullMessage
					out.writeLong(localPacketSeq);
					out.writeInt(bytesRead);

					if (bytesRead < 0) break;

					out.write(buf, 0, bytesRead);
					remotePacketSeq = in.readLong();
					bytesRead = in.readInt();

					if (bytesRead < 0) break;

					if (localPacketSeq != remotePacketSeq) {
						Log.e(TAG, "localPacketSeq=" + localPacketSeq +
						" remotePacketSeq=" + remotePacketSeq);
						throw new Error("unsynched");
					}

					in.readFully(buf, 0, bytesRead);
					// TODO: decrypt here
					//       read magic?
					//       Rewrite as a SkullMessage
					aout.write(buf, 0, bytesRead);

					if (aout.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
						aout.play();

					localPacketSeq += 1;

				}

				if (! callSocket.isClosed()){
					callSocket.close();
					callSocket = null;
				}
				
			}catch (IOException ioe){
				// TODO: cleanup and notify user of communication error
			}
			
			if (ain != null)
				if (ain.getRecordingState() ==
				AudioRecord.RECORDSTATE_RECORDING)
					ain.stop();

			if (aout != null)
				if (aout.getPlayState() ==
				AudioTrack.PLAYSTATE_PLAYING)
					aout.stop();

			talkThread = null;
			
		}
	}
}
