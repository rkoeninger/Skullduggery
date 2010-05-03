package edu.uc.skullduggery;

public class SkullMessage {
	public enum MessageType
	{
		CALL,
		PUBMOD,
		PUBEXP,
		SESKEY,
		MESKEY,
		ACCEPT,
		REJECT,
		BUSY,
		VOICE,
		HANGUP;
	}
	
	private MessageType _type;
	private byte[] _data;
	
	public SkullMessage(MessageType type, byte[] data)
	{
		_type = type;
		_data = data;
	}
	
	public SkullMessage(MessageType type)
	{
		_type = type;
		_data = null;
	}
	
	public byte[] getData()
	{
		return _data;
	}
	
	public MessageType getType()
	{
		return _type;
	}
}
