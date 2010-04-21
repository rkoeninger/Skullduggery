package edu.uc.skullduggery;

public class SkullMessage {
	public enum MessageType
	{
		CALL,
		PUBMOD,
		PUBEXP,
		SESKEY,
		MESKEY,
		BUSY,
		HANGUP;
	}
	
	private byte[] _hash;
	private MessageType _type;
	private byte[] _data;
	
	public SkullMessage(byte[] hash, MessageType type, byte[] data)
	{
		_hash = hash;
		_type = type;
		_data = data;
	}
	
	public byte[] getData()
	{
		return _data;
	}
	
	public byte[] getHash()
	{
		return _hash;
	}
	
	public MessageType getType()
	{
		return _type;
	}
	
	public byte getTypeAsByte()
	{
		return (byte) _type.ordinal();
	}
}
