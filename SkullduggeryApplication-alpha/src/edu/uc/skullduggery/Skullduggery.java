package edu.uc.skullduggery;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.TextView;
import android.view.View;

public class Skullduggery extends Activity {
	
	private SkullTalkService service;
	private final Handler handlerCallback = new Handler(){
		public void handleMessage(Message msg){
			switch (msg.what){
			case 1:
				//show talk screen
			case 2:
				//show dial screen
			default:
				//error
			}
		}
	};
	
    View.OnClickListener callButtonListener = new View.OnClickListener(){
        public void onClick(View view){
        	String phoneNum =
        	((TextView) findViewById(R.id.phoneNumField)).getText().toString();
        	service.dial(phoneNum);
        	// After service hangs-up, handler
        	// is called and UI is updated there
        }
    };
    
    View.OnClickListener hangupButtonListener = new View.OnClickListener(){
        public void onClick(View view){
        	service.hangup();
        	// After service hangs-up, handler
        	// is called and UI is updated there
        }
    };
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        findViewById(R.id.callButton).setOnClickListener(callButtonListener);
        findViewById(R.id.hangupButton).setOnClickListener(hangupButtonListener);
        
        service = new SkullTalkService(handlerCallback, this);
        
    }
    
    public void onDestroy(){
    	
    	//stop call
    	
    	super.onDestroy();
    	
    }

    public void onStart(){
    	super.onStart();
    	
    }
    
    public void onStop(){
    	
    	//possibly stop call
    	
    	super.onStop();
    	
    }
    
    public void onResume(){
    	super.onResume();
    	
    }
    
    public void onPause(){
    	super.onPause();
    	
    }
    
    public void onSaveInstanceState(Bundle outState){
    	super.onSaveInstanceState(outState);
    	
    }
    
    public void onRestoreInstanceState(Bundle savedInstanceState){
    	super.onRestoreInstanceState(savedInstanceState);
    	
    }
    
}