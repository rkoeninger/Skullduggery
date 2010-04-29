package edu.uc.skullduggery.x;

import java.io.*;
import java.net.*;
import java.util.*;

//import javax.net.*;
import javax.net.ssl.*;

public class SSLSwitchStation {
	public static void main(String[] args){
		int listenPort = Integer.parseInt(args[0]);
		SSLSwitchStation server = new SSLSwitchStation(listenPort);
		server.start();
	}
	/**
	 * Simple struct to contain info about how to contact a phone.
	 */
	public static class PhoneInfo{
		public int ip;
		public int port;
		public long sessionKey;
		public PhoneInfo(int ip, int port, long sessionKey){
			this.ip = ip;
			this.port = port;
			this.sessionKey = sessionKey;
		}
	}
	private Random sessionKeyGen;
	private Map<String, PhoneInfo> phoneTable;
	private boolean letDie = false;
	private int listenPort;
	private AcceptThread acceptThread;
	private Collection<Thread> processThreads;
	public SSLSwitchStation(int listenPort){
		this.listenPort = listenPort;
		this.phoneTable = new Hashtable<String, PhoneInfo>();
		this.sessionKeyGen = new Random();
		acceptThread = new AcceptThread(this.listenPort);
		processThreads = new LinkedList<Thread>();
	}
	public void start(){
		acceptThread.start();
	}
	public class AcceptThread extends Thread{
		private int listenPort;
		public AcceptThread(int listenPort){
			this.listenPort = listenPort;
		}
		public void run(){
			ServerSocket acceptSocket;
			Socket connection;
			try{
				acceptSocket = SSLServerSocketFactory.getDefault().
				createServerSocket(listenPort);
				acceptSocket.setSoTimeout(100);
				while (! letDie){
					try{
						connection = acceptSocket.accept();
					}catch (SocketTimeoutException ste){
						continue;
					}
					new ProcessThread(connection).start();
				}
			}catch (Exception e){
				throw new Error(e);
			}			
		}
	}
	public class ProcessThread extends Thread{
		private Socket connection;
		
		/**
		 * Constructor must be called with an already open connection.
		 * @param connection
		 */
		public ProcessThread(Socket connection){
			this.connection = connection;
			processThreads.add(this);
		}
		public void run(){
			
			try{
				
				System.out.print("Client connected - ");
				System.out.print(connection.getInetAddress().toString());
				System.out.println();
				System.out.println();
				
				DataInputStream input = new DataInputStream(
				connection.getInputStream());
				DataOutputStream output = new DataOutputStream(
				connection.getOutputStream());

				int requestType = input.readByte();

				if (requestType == 1){ /* Register request */
					
					// Read phone number from socket
					int phoneNumLength = input.readByte();
					byte[] phoneNumBytes = new byte[phoneNumLength];
					input.readFully(phoneNumBytes);
					String phoneNum = new String(phoneNumBytes);
					
					// Read ip and port bytes from socket
					int ip = input.readInt();
					short port = input.readShort();
					
					long sessionKey = sessionKeyGen.nextLong();
					
					PhoneInfo info = new PhoneInfo(ip, port, sessionKey);
					
					if (phoneTable.containsValue(info)){
						
						output.writeByte(1);

						// Logging
						System.out.print("! Request FAIL ");
						System.out.print(phoneNum);
						System.out.println();
						
						return;
						
					}
					
					// Add value to table
					phoneTable.put(phoneNum, info);
					
					// Indicate success
					output.writeByte(0);
					output.writeLong(sessionKey);
					
					// Logging
					System.out.print("+ Phone info added ");
					System.out.print(phoneNum);
					System.out.print(',');
					System.out.print(
					Integer.toString((ip >> 32) & 0xff) +
					Integer.toString((ip >> 16) & 0xff) +
					Integer.toString((ip >> 8) & 0xff) +
					Integer.toString(ip & 0xff));
					System.out.print(':');
					System.out.print(port);
					System.out.println();
					
				} else if (requestType == 2){ /* Phone info request */
					
					// Read phone number from socket
					int phoneNumLength = input.readByte();
					byte[] phoneNumBytes = new byte[phoneNumLength];
					input.readFully(phoneNumBytes);
					String phoneNum = new String(phoneNumBytes);
					
					// Get phone info from table
					PhoneInfo info = phoneTable.get(phoneNum);
					
					if (info == null){
						
						// Indicate failure
						output.writeByte(1);
						
						// Logging
						System.out.print("! Request FAIL ");
						System.out.print(phoneNum);
						System.out.println();
						
						return;
						
					}
					
					// Indicate success
					output.writeByte(0);
					
					int ip = info.ip;
					int port = info.port;
					
					output.writeInt(ip);
					output.writeShort(port);
				
					// Logging
					System.out.print("? Phone info requested ");
					System.out.print(phoneNum);
					System.out.print(" ");
					System.out.print(ip);
					System.out.print(":");
					System.out.print(port);
					System.out.println();
						
				} else if (requestType == 3){ /* Phone info remove */

					long offeredKey = input.readLong();

					for (Map.Entry<String, PhoneInfo> e :
					phoneTable.entrySet()){
						
						if (e.getValue().sessionKey == offeredKey){
							
							phoneTable.remove(e.getKey());
							output.writeByte(0); // Remove sucessful
							return;
							
						}
						
					}
					
					output.writeByte(1); // Entry not in table or wrong key
					
					// Logging
					System.out.println("! Remove requested for wrong key");
					
				} else if (requestType == 4){ /* Phone info update */

					long offeredKey = input.readLong();
					
					// Read phone number from socket
					int phoneNumLength = input.readByte();
					byte[] phoneNumBytes = new byte[phoneNumLength];
					input.readFully(phoneNumBytes);
					String phoneNum = new String(phoneNumBytes);

					int ip = input.readInt();
					int port = input.readShort();
					
					// Get phone info from table
					PhoneInfo info = phoneTable.get(phoneNum);
					
					if (offeredKey != info.sessionKey){
						
						output.writeByte(1); // Wrong key, failure
						
						// Logging
						System.out.print("! Request FAIL ");
						System.out.print(phoneNum);
						System.out.println();
						
						return;
					}
					
					info.ip = ip;
					info.port = port;
					
				}
			}catch (IOException ioe){
				//abort communication
			}catch (Exception e){
				throw new Error(e);
			}finally{
				try{
					connection.close();
				}catch (IOException ioe){}
				processThreads.remove(this);
			}
		}
	}
}