package skullduggery.server;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;

public class SkullduggeryServer extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        try{
        	ServerSocket sskt = new ServerSocket(9002);
        	TextView tv = (TextView) findViewById(R.id.text_view);
        	Socket skt = sskt.accept();
	        BufferedReader in = new BufferedReader(new InputStreamReader(skt.getInputStream()));
	        String s;
        	while(!in.ready()){}
	        while((s=in.readLine()) != null)
	        {
	        	tv.append(s);
	        }
	        in.close();
	        skt.close();
	        sskt.close();
        }
        catch (Exception e){
        	
        }
    }
}
