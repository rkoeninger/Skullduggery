package applications.skullduggery;

import java.net.Socket;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import java.io.OutputStream;
import java.io.PrintWriter;


public class Skullduggery extends Activity {
	
	public static java.io.OutputStream out;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);
                
        final Button sendbutton = (Button) findViewById(R.id.send_button);
        sendbutton.setOnClickListener(
        	new View.OnClickListener() 
        	{
        		public void onClick(View v) 
        		{ 
    				//((EditText) findViewById(R.id.edit_text)).setText("Call Check");
    				
    				Socket skt = null;
    				OutputStream skt_out = null;
    				boolean kill = false;
        			try{
        				skt = new Socket("10.0.2.2",9001);
        				skt_out = skt.getOutputStream();
        			}
        			catch (Exception e) {
        				((EditText) findViewById(R.id.edit_text)).setText(e.getMessage());
        				kill = true;
        			}
        			
        			if(kill)
        			{
        				try{
        					skt_out.close();
        					skt.close();
        				}
        				catch(Exception e) {}
        				return;
        			}
        			
    				PrintWriter out = new PrintWriter(skt_out);
    				out.print(((EditText) findViewById(R.id.edit_text)).getText().toString());
    				out.close();
    				
        			try{
        				skt_out.close();
        				skt.close();
        			}
        			catch (Exception e){
        				((EditText) findViewById(R.id.edit_text)).setText("Closing");
        			}
        		}
			});
    }
}