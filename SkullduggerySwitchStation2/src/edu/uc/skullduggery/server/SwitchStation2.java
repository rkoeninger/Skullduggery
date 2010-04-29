/*
 * This code is written for Java1.5 itself
 * NOT for use with android.
 */
package edu.uc.skullduggery.server;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Responds to Skullduggery phones with info about other phones on the
 * Skullduggery network.
 * 
 * Format of a request packet:
 *
 * (Packet must come in on the listen port specified when server is started)
 * (Multi-byte values are stored big-endian)
 * 
 *     Bytes 1-4  must be "SKUL" in ASCII
 *     Byte  5    is one of
 *           0x01 for a register request
 *           0x02 for a phone info request
 *           
 *     If it is a register request:
 *     Byte  6    is the length of the trimmed (no hypens) phone number
 *     Bytes 7-I  are the bytes of the phone number
 *     Bytes I-J  are the bytes of the IP address (4 bytes)
 *     Bytes J-K  are the bytes of the port at that address (2 bytes)
 * 
 *     If it is a phone info request:
 *     Byte  6    is the length of the trimmed (no hypens) phone number
 *     Bytes 7-I  are the bytes of the phone number
 * 
 * Format of response packet:
 * 
 *     If it is a register request:
 *     Byte  1    a single non-zero byte indicating success
 *                or 0 indicating entry couldn't be added for any reason
 *     
 *     If it is a phone info request:
 *     Byte  1    a single non-zero byte indicating success
 *                or 0 indicating entry couldn't be added for any reason
 *     Bytes 2-5  are the bytes that make up the IPv4 address
 *     Bytes 6-7  are a 16-bit value that indicates the port at that address
 * 
 */
public class SwitchStation2 {

	/**
	 * Entry point for application, args are as follows:
	 * 
	 * java SwitchStation listenPort
	 */
	public static void main(String[] args) {
		if(args.length == 0) {
			System.out.println("Usage: SwitchStation2 PORTNUM");
			System.exit(0);
		}
		
		int listenPort = Integer.parseInt(args[0]);
		SwitchStation2 server = new SwitchStation2(listenPort);
		server.start();
	}

	private int listenPort;
	private Hashtable<String, InetSocketAddress> phoneTable;
	
	/**
	 * Creates an instance of the switch station server listening on
	 * the port specified from the command line.
	 */
	public SwitchStation2(int port){
		listenPort = port;
		phoneTable = new Hashtable<String, InetSocketAddress>();
	}
	
	/**
	 * Starts this instance of a server.
	 */
	public void start(){
		Thread listenThread = new ListenThread();
		listenThread.start();
	}

	/**
	 * Requests are carried out in a different thread in case we expand
	 * the design to include a pool of response threads (a new thread starting
	 * immediately after its predecessor has received a request).
	 */
	public class ListenThread extends Thread{
		ServerSocket serverSocket;
		public ListenThread()
		{
			try {
				serverSocket= new ServerSocket(listenPort);
			} catch (IOException ioe) {
				// TODO Auto-generated catch block
				ioe.printStackTrace();
			}
		}
		
		private void register(DataInputStream input, DataOutputStream output, InetAddress client) throws IOException
		{
			// Read phone number from socket
			int phoneNumLength = input.readByte();
			
			byte[] phoneNumBytes = new byte[phoneNumLength];
			
			input.readFully(phoneNumBytes);
			
			String phoneNum = new String(phoneNumBytes);
			
			byte portByte1 = input.readByte();
			byte portByte2 = input.readByte();
			
			int port = ((portByte1 << 8) & 0xff00) |
			(portByte2 & 0xff);
			
			// Add value to table
			phoneTable.put(phoneNum, new InetSocketAddress(client, port));
			
			// Indicate success
			output.writeByte(1);
			
			// Logging
			System.out.print("+ Phone info added ");
			System.out.print(phoneNum);
			System.out.print(" ");
			System.out.print(client.toString());
			System.out.print(":");
			System.out.print(port);
			System.out.println();
		}
		
		private void infoRequest(DataInputStream input, DataOutputStream output) throws IOException
		{
			System.out.println("Phone info request");
			// Read phone number from socket
			int phoneNumLength = input.readByte();
			
			byte[] phoneNumBytes = new byte[phoneNumLength];
			
			input.readFully(phoneNumBytes);
			
			String phoneNum = new String(phoneNumBytes);
			
			// Get phone info from table
			InetSocketAddress info = phoneTable.get(
			new String(phoneNumBytes));
			
			if (info == null){
				
				// Indicate success
				output.writeByte(0);
				
				// Logging
				System.out.print("! Request FAIL ");
				System.out.print(phoneNum);
				System.out.println();
			} else {
			
				// Indicate success
				output.writeByte(1);
				
				InetAddress ip = info.getAddress();
				int port = info.getPort();
				
				byte portByte1 = (byte) (port >> 8);
				byte portByte2 = (byte) (port);
				
				// Write ip and port to socket
				output.write(ip.getAddress());
				output.writeByte(portByte1);
				output.writeByte(portByte2);
			
				// Logging
				System.out.print("? Phone info requested ");
				System.out.print(phoneNum);
				System.out.print(" ");
				System.out.print(ip);
				System.out.print(":");
				System.out.print(port);
				System.out.println();
			}
		}
		
		private void processLoop() throws IOException{
			System.out.println("SKUL".getBytes().length);
			
			Socket socket = serverSocket.accept();
			
			System.out.print("Client connected - ");
			System.out.print(socket.getInetAddress().toString());
			System.out.println();
			System.out.println();
			
			DataInputStream input = new DataInputStream(
			socket.getInputStream());
			DataOutputStream output = new DataOutputStream(
			socket.getOutputStream());

			int requestType = input.readByte();

			switch(requestType)
			{
				case 1: 
					System.out.println("Register request");
					register(input, output, socket.getInetAddress()); 
					break;
				case 2:
					System.out.println("Register request");
					infoRequest(input, output);
					break;
				default: System.out.println("Invalid request type ("+requestType+")" ); break;
			}				
		
			socket.close();
			//serverSocket.close();
			
			System.out.println("Client disconnected");
		}
		
		public void run(){
			try{
				while(true)
					processLoop();
							
			} catch (IOException exc){
				exc.printStackTrace();
			}
			
			// Start a new listen thread, then this one ends
//			SwitchStation2.this.start();
		}
	}
}
