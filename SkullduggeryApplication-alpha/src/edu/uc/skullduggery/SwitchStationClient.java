package edu.uc.skullduggery;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;

/**
 * This class encapsulates all the behaviors involved in communicating with the
 * switch station server or SIP proxy.
 * 
 * The current implementation does not stay connected between calls to
 * register/request/remove/etc. It starts a new connection for each request.
 */
public class SwitchStationClient {

	private long sessionKey;
	
	private SocketAddress serverAddress;
	
	public SwitchStationClient(){
	}

	public long getSessionKey() {
		return sessionKey;
	}

	public void setSessionKey(long sessionKey) {
		this.sessionKey = sessionKey;
	}

	/**
	 * Connects to the switch station server at the given address.
	 * Current implementation just stores the address info and
	 * contacts server for each request.
	 * 
	 * @param ip
	 * @param port
	 */
	public void connect(String ip, int port){
		serverAddress = new InetSocketAddress(ip, port);
	}
	
	/**
	 * For a persistently-connected version of this client, disconnect
	 * would end the session. The current implementation just clears the
	 * members storing the ip:port info.
	 */
	public void disconnect(){
		serverAddress = null;
	}

	/**
	 * An interface that abstracts the data exchange of a particular
	 * server request. Implementers can allow
	 * IOExceptions, SocketTimeoutExceptions, etc. to be thrown and
	 * the makeRequest method will handle them.
	 */
	protected interface Processor {
		public int process(Socket connection) throws IOException;
	}
	
	/**
	 * This method generifies the code to contact the server and
	 * make a request. The given Processor instance handles the actual
	 * conversation and determines what kind of request is performed.
	 * 
	 * @param proc
	 * @return status code from server
	 */
	protected int makeRequest(Processor proc){

		/*
		 * Get an SSL socket. Could throw an exception; report error if so.
		 */
		Socket connection;
		try{
			connection = SSLSocketFactory.getDefault().createSocket();
		}catch (IOException ioe){
			return 1; // Cannot connect to host
		}
		
		/*
		 * Make 300 attempts to connect, each lasting no longer than 100ms.
		 * This gives a total timeout of 30 seconds.
		 * If the socket is still not connected at the end of this loop,
		 * report an error. 
		 */
		for (int i = 0; i < 300; ++i){
			try{
				connection.connect(this.serverAddress, 100);
			}catch (SocketTimeoutException ste){
				continue;
			}catch (IOException ioe){
				break;
			}
		}
		if (! connection.isConnected()){
			return 1; // Cannot connect to host
		}
		
		/*
		 * Run the request code. Handle errors.
		 */
		try{
			return proc.process(connection);
		}catch (IOException ioe){
			return 1; // Communication error
		}finally{
			if (connection.isConnected()){
				try {
					connection.close();
				}catch (IOException ioe){
					// What to do?
				}
			}
		}
	}
	
	/**
	 * Contacts server to register ip:port info for this phone.
	 * Register is successful if return code is 0.
	 * Will not be successful is phone number is already in table.
	 * 
	 * Make sure the phone number string follows the proper format used
	 * by Skullduggery.
	 * 
	 * @param phoneNum
	 * @param ipBytes
	 * @param port32
	 * @return status
	 */
	public int register(final String phoneNum, byte[] ipBytes, int port32){
		final int ip = Constants.ipBytesToInt(ipBytes);
		final short port = (short) port32;
		
		final Object[] retvals = new Object[1];
		
		int status = makeRequest(new Processor(){
			public int process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());
				
				out.writeByte(1); // Flag for register packet
				out.writeByte(phoneNum.length());
				out.write(phoneNum.getBytes());
				out.write(ip);
				out.writeShort(port);
				
				byte status = in.readByte();
				if (status != 0){
					return status; // Server error in processing packet
				}
				
				retvals[0] = new Long(in.readLong());
				
				return 0; // Success
			}
		});
		
		sessionKey = ((Long) retvals[0]).longValue();
		return status;
	}
	
	/**
	 * Contacts server for ip:port info about the given phone number.
	 * Request is successful if return code is 0.
	 * Will not be successful if phone number is not in table.
	 * 
	 * An Object array of length 2 needs to be provided to get info
	 * from server.
	 * 
	 * [0] - java.lang.Integer object containing 32-bit ip address.
	 * [1] - java.land.Short object containing 16-bit port value.
	 * 
	 * @param phoneNum
	 * @param retvals
	 * @return status
	 */
	public int request(final String phoneNum, final Object[] retvals){
		int status = makeRequest(new Processor(){
			public int process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());
				
				out.writeByte(2);
				out.writeByte(phoneNum.length());
				out.write(phoneNum.getBytes());
				
				byte status = in.readByte();
				if (status != 0){
					return status; // Server error in processing packet
				}
				
				retvals[0] = new Integer(in.readInt());
				retvals[1] = new Short(in.readShort());
				return 0; // Success
			}
		});
		
		return status;
	}

	/**
	 * Contacts server to remove info about this phone from the
	 * switch station.
	 * Remove is successful if return code is 0.
	 * Will not be successful if phone is not registered or if
	 * wrong session key is given.
	 * 
	 * @return status
	 */
	public int remove(){
		int status = makeRequest(new Processor(){
			public int process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());
				
				out.writeByte(3);
				out.writeLong(sessionKey);
				
				byte status = in.readByte();
				if (status != 0){
					return status; // Server error in processing packet
				}
				
				return 0; // Success
			}
		});
		
		return status;
	}
	
	/**
	 * Contacts server to change ip:port info for a phone that is already
	 * registered.
	 * Update is successful if return code is 0.
	 * Will not be succesful if phone is not in table or
	 * wrong session key is given.
	 * 
	 * @param phoneNum
	 * @param ipBytes
	 * @param port32
	 * @return
	 */
	public int update(final String phoneNum, byte[] ipBytes, int port32){
		final int ip = Constants.ipBytesToInt(ipBytes);
		final short port = (short) port32;
		
		int status = makeRequest(new Processor(){
			public int process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());
				
				out.writeByte(4);
				out.writeLong(sessionKey);
				out.writeByte(phoneNum.length());
				out.write(phoneNum.getBytes());
				out.writeInt(ip);
				out.writeShort(port);
				
				byte status = in.readByte();
				if (status != 0){
					return status; // Server error in processing packet
				}
				
				return 0; // Success
			}
		});
		
		return status;
	}
	
}