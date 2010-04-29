package edu.uc.skullduggery.x;

import java.io.*;
import java.net.*;
import java.util.*;

//import javax.net.*;
import javax.net.ServerSocketFactory;
import javax.net.ssl.*;

public class SSLSwitchStation {
	public static final String[] SSLCIPHERSUITES = 
		new String[] {
		"SSL_DH_anon_WITH_RC4_128_MD5"
		,"TLS_DH_anon_WITH_AES_128_CBC_SHA"
		,"TLS_DH_anon_WITH_AES_256_CBC_SHA"
		,"SSL_DH_anon_WITH_3DES_EDE_CBC_SHA"
		,"SSL_DH_anon_WITH_DES_CBC_SHA"
		,"TLS_ECDH_anon_WITH_RC4_128_SHA"
		,"TLS_ECDH_anon_WITH_AES_128_CBC_SHA"
		,"TLS_ECDH_anon_WITH_AES_256_CBC_SHA"
		,"TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA"
		,"SSL_DH_anon_EXPORT_WITH_RC4_40_MD5"
		,"SSL_DH_anon_EXPORT_WITH_DES40_CBC_SHA"
		,"TLS_ECDH_anon_WITH_NULL_SHA"
	};                       
	
	public static void main(String[] args){
		if(args.length == 0) {
			System.out.println("Usage: SSLSwitchStation PORTNUM");
			System.exit(0);
		}
		int listenPort = Integer.parseInt(args[0]);
		SSLSwitchStation server = new SSLSwitchStation(listenPort);
		server.start();
	}
	/**
	 * Simple struct to contain info about how to contact a phone.
	 */
	public static class PhoneInfo{
		public InetSocketAddress ip;
		public long sessionKey;
		public PhoneInfo(InetAddress ip, int port, long sessionKey){
			this.ip = new InetSocketAddress(ip, port);
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
				acceptSocket = ServerSocketFactory.getDefault().createServerSocket(listenPort);
				//acceptSocket = SSLServerSocketFactory.getDefault().				createServerSocket(listenPort);
				//((SSLServerSocket) acceptSocket).setEnabledCipherSuites(SSLCIPHERSUITES);
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
		
		
		
		private void register(DataInputStream input, DataOutputStream output) throws IOException
		{
			// Read phone number from socket
			int phoneNumLength = input.readByte();
			byte[] phoneNumBytes = new byte[phoneNumLength];
			input.readFully(phoneNumBytes);
			String phoneNum = new String(phoneNumBytes);
			
			// Read ip and port bytes from socket
			InetAddress ip = connection.getInetAddress();
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
			System.out.print(ip.toString());
			System.out.print(':');
			System.out.print(port);
			System.out.println();	
		}
		
		private void infoRequest(DataInputStream input, DataOutputStream output) throws IOException
		{
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
			
			InetSocketAddress ip = info.ip;
			int port = info.ip.getPort();
			
			output.write(ip.getAddress().getAddress());
			output.writeShort(port);
		
			// Logging
			System.out.print("? Phone info requested ");
			System.out.print(phoneNum);
			System.out.print(" ");
			System.out.print(ip);
			System.out.print(":");
			System.out.print(port);
			System.out.println();
		}
		
		private void infoRemove(DataInputStream input, DataOutputStream output) throws IOException
		{
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
		}
		
		private void infoUpdate(DataInputStream input, DataOutputStream output) throws IOException
		{
			long offeredKey = input.readLong();
			
			// Read phone number from socket
			int phoneNumLength = input.readByte();
			byte[] phoneNumBytes = new byte[phoneNumLength];
			byte[] ipBytes = new byte[4];
			input.readFully(phoneNumBytes);
			String phoneNum = new String(phoneNumBytes);
			input.readFully(ipBytes);
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
			
			info.ip = new InetSocketAddress(InetAddress.getByAddress(ipBytes), port);
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
				switch(requestType)
				{
					case 1:
						System.out.println("Register request");
						register(input, output);
						break;
					case 2:
						System.out.println("Info request");
						infoRequest(input, output);
						break;
					case 3:
						System.out.println("Info remove request");
						infoRemove(input, output);
						break;
					case 4:
						System.out.println("Info update request");
						infoUpdate(input, output);
						break;
					default:
						System.out.println("Invalid request type: " + requestType);
						break;
				}
				output.flush();
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