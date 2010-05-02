package edu.uc.skullduggery;

import java.io.*;
import java.net.*;

import javax.net.SocketFactory;

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
		public void process(Socket connection) throws IOException;
	}
	
	/**
	 * This method generifies the code to contact the server and
	 * make a request. The given Processor instance handles the actual
	 * conversation and determines what kind of request is performed.
	 * 
	 * @param proc
	 * @return status code from server
	 */
	protected void makeRequest(Processor proc) throws IOException{

		/*
		 * Get an SSL socket. Could throw an exception; report error if so.
		 */
		Socket connection = null;
		
		/*
		 * Make 300 attempts to connect, each lasting no longer than 100ms.
		 * This gives a total timeout of 30 seconds.
		 * If the socket is still not connected at the end of this loop,
		 * report an error. 
		 */
		for (int i = 0; i < 300; ++i){
			try{
				//connection = SSLSocketFactory.getDefault().createSocket();
				//((SSLSocket) connection).setEnabledCipherSuites(Constants.SSLCIPHERSUITES);
				connection = SocketFactory.getDefault().createSocket();
				connection.connect(this.serverAddress, 100);
				break;
			}catch (SocketTimeoutException ste){
				connection.close();
				continue;
			}
		}
		if (! connection.isConnected()){
			throw new IOException("Unable to contact server");
		}
		
		/*
		 * Run the request code. Handle errors.
		 */
		try{
			proc.process(connection);
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
	 * @param port32
	 * @return status
	 */ 
	public void register(final String phoneNum, int port32)
	throws IOException{
//		final int ip = Constants.ipBytesToInt(ipBytes);
		final short port = (short) port32;
		
		final Object[] retvals = new Object[1];
		
		makeRequest(new Processor(){
			public void process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());
				
				out.write(Constants.MAGICBYTES);
				out.writeByte(1); // Flag for register packet
				out.writeByte(phoneNum.length());
				out.write(phoneNum.getBytes());
//				out.write(ip);
				out.writeShort(port);
				out.flush();
				
				byte status = in.readByte();
				if (status != 0){
					throw new IOException("Request failed");
				}
				
				retvals[0] = new Long(in.readLong());
			}
		});
		
		sessionKey = ((Long) retvals[0]).longValue();
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
	public void request(final String phoneNum, final Object[] retvals)
	throws IOException{
		makeRequest(new Processor(){
			public void process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());
				
				out.write(Constants.MAGICBYTES);
				out.writeByte(2);
				out.writeByte(phoneNum.length());
				out.write(phoneNum.getBytes());
				out.flush();
				
				byte status = in.readByte();
				if (status != 0){
					throw new IOException("Request failed");
				}
				
				byte[] ipBytes = new byte[4];
				in.readFully(ipBytes);
				retvals[0] = new InetSocketAddress(InetAddress.getByAddress(ipBytes), in.readShort());
			}
		});
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
	public void remove() throws IOException{
		makeRequest(new Processor(){
			public void process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());

				out.write(Constants.MAGICBYTES);
				out.writeByte(3);
				out.writeLong(sessionKey);
				out.flush();
				
				byte status = in.readByte();
				if (status != 0){
					throw new IOException("Request failed");
				}
			}
		});
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
	public void update(final String phoneNum, int port32)
	throws IOException{
		final short port = (short) port32;
		
		makeRequest(new Processor(){
			public void process(Socket connection) throws IOException{
				DataInputStream in = new DataInputStream(
				connection.getInputStream());
				DataOutputStream out = new DataOutputStream(
				connection.getOutputStream());

				out.write(Constants.MAGICBYTES);
				out.writeByte(4);
				out.writeLong(sessionKey);
				out.writeByte(phoneNum.length());
				out.write(phoneNum.getBytes());
				out.writeShort(port);
				out.flush();
				
				byte status = in.readByte();
				if (status != 0){
					throw new IOException("Request failed");
				}
			}
		});
	}
	
}