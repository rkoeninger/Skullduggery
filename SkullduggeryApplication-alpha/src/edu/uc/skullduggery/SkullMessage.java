package edu.uc.skullduggery;

public class SkullMessage {
	
	private byte[] _data;
	
	public SkullMessage(byte[] data)
	{
		_data = data;
	}
	
	public byte[] getData()
	{
		return _data;
	}
}
