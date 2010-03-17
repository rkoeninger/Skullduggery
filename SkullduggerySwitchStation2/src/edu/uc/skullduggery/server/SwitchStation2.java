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
		int listenPort = Integer.parseInt(args[0]);
		SwitchStation2 server = new SwitchStation2(listenPort);
		server.start();
	}

	/**
	 * Simple struct to contain info about how to contact a phone.
	 */
	public static class PhoneInfo{
		public int ip;
		public int port;
		public PhoneInfo(int ip, int port){
			this.ip = ip;
			this.port = port;	
		}	
	}
	
	private int listenPort;
	private Hashtable<String, PhoneInfo> phoneTable;
	
	/**
	 * Creates an instance of the switch station server listening on
	 * the port specified from the command line.
	 */
	public SwitchStation2(int port){
		listenPort = port;
		phoneTable = new Hashtable<String, PhoneInfo>();
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
		public void run(){
			try{
				ServerSocket serverSocket = new ServerSocket(listenPort);
				Socket socket = serverSocket.accept();
				
				System.out.print("Client connected - ");
				System.out.print(socket.getInetAddress().toString());
				System.out.println();
				System.out.println();
				
				DataInputStream input = new DataInputStream(
				socket.getInputStream());
				DataOutputStream output = new DataOutputStream(
				socket.getOutputStream());

				// Read magic number from socket
				byte magicByte1 = input.readByte();
				byte magicByte2 = input.readByte();
				byte magicByte3 = input.readByte();
				byte magicByte4 = input.readByte();
				
				String magicNum = "" +
				((char) magicByte1) +
				((char) magicByte2) +
				((char) magicByte3) +
				((char) magicByte4);
				
				if (magicNum.equals("SKUL")){
					
					int requestType = input.readByte();

					if (requestType == 1){ /* Register request */
						
						// Read phone number from socket
						int phoneNumLength = input.readByte();
						
						byte[] phoneNumBytes = new byte[phoneNumLength];
						
						input.readFully(phoneNumBytes);
						
						String phoneNum = new String(phoneNumBytes);
						
						// Read ip and port bytes from socket
						byte ipByte1 = input.readByte();
						byte ipByte2 = input.readByte();
						byte ipByte3 = input.readByte();
						byte ipByte4 = input.readByte();
						byte portByte1 = input.readByte();
						byte portByte2 = input.readByte();
						
						int ip = (ipByte1 << 24) | (ipByte2 << 16) |
						(ipByte3 << 8) | (ipByte4);
						int port = ((portByte1 << 8) & 0xff00) |
						(portByte2 & 0xff);
						
						// Add value to table
						phoneTable.put(phoneNum, new PhoneInfo(ip, port));
						
						// Indicate success
						output.writeByte(1);
						
						// Logging
						System.out.print("+ Phone info added ");
						System.out.print(phoneNum);
						System.out.print(" ");
						System.out.print(ipByte1);
						System.out.print(".");
						System.out.print(ipByte2);
						System.out.print(".");
						System.out.print(ipByte3);
						System.out.print(".");
						System.out.print(ipByte4);
						System.out.print(":");
						System.out.print(port);
						System.out.println();
						
					} else if (requestType == 2){ /* Phone info request */
						
						// Read phone number from socket
						int phoneNumLength = input.readByte();
						
						byte[] phoneNumBytes = new byte[phoneNumLength];
						
						input.readFully(phoneNumBytes);
						
						String phoneNum = new String(phoneNumBytes);
						
						// Get phone info from table
						PhoneInfo info = phoneTable.get(
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
							
							int ip = info.ip;
							int port = info.port;
							
							// Write ip and port to socket
							byte ipByte1 = (byte) (ip >> 24);
							byte ipByte2 = (byte) (ip >> 16);
							byte ipByte3 = (byte) (ip >> 8 );
							byte ipByte4 = (byte) (ip);
							byte portByte1 = (byte) (port >> 8);
							byte portByte2 = (byte) (port);
							
							output.writeByte(ipByte1);
							output.writeByte(ipByte2);
							output.writeByte(ipByte3);
							output.writeByte(ipByte4);
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
				}
				
				socket.close();
				serverSocket.close();
				
				System.out.println("Client disconnected");
				
			} catch (IOException exc){
				exc.printStackTrace();
			}
			
			// Start a new listen thread, then this one ends
			SwitchStation2.this.start();
		}
	}
}
