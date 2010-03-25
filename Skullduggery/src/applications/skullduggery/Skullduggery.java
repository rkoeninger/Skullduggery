package applications.skullduggery;

import java.net.*;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import java.io.IOException;

//test comment.

public class Skullduggery extends Activity {
	private final Handler mHandler = new Handler();
	private TextView mTextView;
	private CommunicationThread T;
	
	
	private void SendString(String s)
	{
		T.SendString(s);		
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
        mTextView = (TextView) findViewById(R.id.text_view);
        T = new CommunicationThread();
        T.start();
        
        final Button sendbutton = (Button) findViewById(R.id.send_button);
        sendbutton.setOnClickListener(
        	new View.OnClickListener() 
        	{
        		public void onClick(View v) 
        		{ 
        			SendString(((EditText) findViewById(R.id.edit_text)).getText().toString());
        		}
			});
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        T.stop();
    }

	private class UIRunnable implements Runnable{
		private String updateString;
		UIRunnable(String s){
			updateString = s;
		}
		
		public void run(){
			mTextView.append(updateString);
		}
	}	

	private class CommunicationThread extends Thread {
		private DatagramSocket talkSock;
		
		private void writeString(String s)
		{
			mHandler.post(new UIRunnable(s));
		}
		
		public void run()
		{
			boolean good = true;
	        
			try{
				talkSock = new DatagramSocket(9001);
				talkSock.connect(InetAddress.getByName("10.0.2.2"), 9002);
				writeString("Connected to host on port 9001\n");
			}
			catch (Exception e){
				writeString(e.getMessage());
				good = false;
			}

			byte[] b = new byte[256];
			DatagramPacket p = new DatagramPacket(b,b.length);
			while(good)
			{
				try{
					writeString("Receiving message\n");
					talkSock.receive(p);
					writeString("Message received\n");
					byte[] ba = p.getData();
					writeString(new String(ba, 0, ba.length)+"\n");
					
				}
				catch (Exception e){
					//We're not good.
					writeString("Problem connecting to the socket\n");
					good = false;
				}
			}

			writeString("Problem connecting to the socket\n");
			talkSock.disconnect();
		}
		
		public void SendString(String s)
		{
	        final TextView textScreen = (TextView) findViewById(R.id.text_view);
			byte[] b = s.getBytes();
			DatagramPacket d = new DatagramPacket(b, b.length);
			if(talkSock.isConnected())
			{
				try {
					talkSock.send(d);
			        textScreen.append("String sent\n");
				} catch (IOException e) {
					textScreen.append(e.getMessage());
				}
			}	
		}
	}

}