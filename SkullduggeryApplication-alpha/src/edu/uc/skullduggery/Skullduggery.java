package edu.uc.skullduggery;

import android.app.Activity;
import android.os.*;
import android.widget.*;
import android.view.*;

public class Skullduggery extends Activity {
	
	private SkullTalkService service;
	
	private final Handler handlerCallback = new Handler(){
		public void handleMessage(Message msg){
			TextView textOutput = (TextView) findViewById(R.id.phoneNumField);
	        Button callButton = (Button) findViewById(R.id.callButton);
	        Button hangupButton = (Button) findViewById(R.id.hangupButton);
			switch (msg.what){
			case 1: // Display message to user
				textOutput.append((String) msg.obj);
				textOutput.append("\n");
			case 2: // Call has ended
		        callButton.setEnabled(true);
		        hangupButton.setEnabled(false);
				textOutput.append("call ended\n");
			case 3: // Call has started
		        callButton.setEnabled(false);
		        hangupButton.setEnabled(true);
				textOutput.append("call placed\n");
			case 4: // Call is in progress
				callButton.setEnabled(false);
				hangupButton.setEnabled(false);
				textOutput.append("call in progress\n");
			default:
				throw new Error("SkullduggeryApp - bad handler message code");
			}
		}
	};
	
    View.OnClickListener callButtonListener = new View.OnClickListener(){
        public void onClick(View view){
        	String phoneNum =
        	((TextView) findViewById(R.id.phoneNumField)).getText().toString();
        	service.dial(phoneNum);
        }
    };
    
    View.OnClickListener hangupButtonListener = new View.OnClickListener(){
        public void onClick(View view){
        	service.hangup();
        }
    };
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Button callButton = (Button) findViewById(R.id.callButton);
        Button hangupButton = (Button) findViewById(R.id.hangupButton);
        callButton.setOnClickListener(callButtonListener);
        hangupButton.setOnClickListener(hangupButtonListener);
        callButton.setEnabled(true);
        hangupButton.setEnabled(false);
        service = new SkullTalkService(handlerCallback, this);
        service.start();
    }
    
    public void onDestroy(){
    	service.stop();
    	super.onDestroy();
    }
    
}