package edu.uc.skullduggery;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SkullTalkService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
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
	 * 
	 * @author bort
	 */
	public class CallThread extends Thread{
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
	 * 
	 * @author bort
	 */
	public class AcceptThread extends Thread{
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
	 * 
	 * @author bort
	 */
	public class TransmitThread extends Thread{
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
	 * 
	 * @author bort
	 */
	public class ReceiveThread extends Thread{
	}

}
