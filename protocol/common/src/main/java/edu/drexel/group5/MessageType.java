package edu.drexel.group5;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public enum MessageType {

	SESSION_REQUEST((byte) 1),
	SESSION((byte) 2),
	CHALLENGE((byte) 3),
	CHALLENGE_RESPONSE((byte) 4),
	CHALLENGE_RESULT((byte) 5),
	AUTHENTICATION_ERROR((byte) 9),
	STREAM((byte) 5),
	THROTTLE((byte) 7),
	DISCONNECT((byte) 6),
	STREAM_ERROR((byte) 10);
	private final byte messageId;

	private MessageType(byte messageId) {
		this.messageId = messageId;
	}

	public byte getMessageId() {
		return messageId;
	}

	public static MessageType getMessageTypeFromId(byte messageId) {
		for (MessageType value : values()) {
			if (messageId == value.messageId) {
				return value;
			}
		}
		throw new IllegalArgumentException("Could not find a MessageType with ID: " + messageId);
	}
}
