package edu.uc.skullduggery;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SkullduggeryAudioTestServer {

	/**
	 * @param args
	 * @throws IOException 
	 */
	
	private static void PlayFileToClients(File f, ServerSocket servSock) throws IOException
	{
		System.out.println("Playing audio");
		FileInputStream fin = new FileInputStream(f);
		while(true){
			Socket clientSock = servSock.accept();
			System.out.println("Playing audio to connected duder.");
			byte[] dataBuffer = new byte[1024];
			int dataLength;
			DataOutputStream dataSock = new DataOutputStream(clientSock.getOutputStream());
			while((dataLength = fin.read(dataBuffer)) > 0)
			{
				dataSock.write(dataBuffer, 0, dataLength);
			}
			dataSock.close();
			clientSock.close();
			System.out.println("Done playing Audio to connected duder.");
		}

	}
	
	private static void RecordFileFromClient(File f, ServerSocket servSock) throws IOException
	{
		System.out.println("Recording something from connected client");
		FileOutputStream fout = new FileOutputStream(f);
		Socket clientSock = servSock.accept();
		System.out.println("Client connected.");
		byte[] dataBuffer = new byte[1024];
		int dataLength;
		DataInputStream dataSock = new DataInputStream(clientSock.getInputStream());
		while((dataLength = dataSock.read(dataBuffer)) > 0)
		{
			System.out.println("Client sent audio to us.");
			fout.write(dataBuffer, 0 , dataLength);
			System.out.println("Audio written to file.");
		}
		fout.flush();
		fout.close();
		clientSock.close();
		System.out.println("Done recording from connected client");
	}
	
	public static void main(String[] args) {
		if(args.length < 1)
		{
			System.out.println("Usage: [PROG_NAME] [FILE]");
			return;
		}
		System.out.println(args[0]);
		File f = new File(args[0]);
		
		try{
			ServerSocket s = new ServerSocket(9002);
			if(f.createNewFile())
			{
				System.out.println("Creating new file");
				//create the file
				RecordFileFromClient(f,s);
			}
			
			if(!f.canRead())
			{
				System.out.println("Can't read the file. Exiting.");
				return;
			}
			//Play the file
			PlayFileToClients(f,s);
			s.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}
}
