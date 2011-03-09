package edu.drexel.group5.common;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.File;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.sound.sampled.*;

/**
 * Factory class used for creating the messages of the protocol.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class PacketFactory {

	private final SocketAddress destination;
	private final MessageDigest messageDigest;

	/**
	 * Constructor that will set all packets created by this factory to the
	 * destination given in the constructor.
	 * @param destinationPort the destination port.
	 * @param destinationAddress the destination address.
	 */
	public PacketFactory(int destinationPort, InetAddress destinationAddress) {
		this(new InetSocketAddress(destinationAddress, destinationPort));
	}

	public PacketFactory(SocketAddress destination) {
		this.destination = destination;
		try {
			this.messageDigest = MessageDigest.getInstance("SHA-1");
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException("SHA-1 is not available on the system!", ex);
		}
	}

	/**
	 * Creates a SessionRequestMessage
	 * @param protocolVersion the byte version of the protocol the client is
	 * running.
	 * @return a DatagramPacket that represents a ERP SessionRequestMessage
	 * @throws SocketException
	 */
	public DatagramPacket createSessionRequest(byte protocolVersion) throws SocketException {
		final byte[] data = new byte[]{MessageType.SESSION_REQUEST.getMessageId(), protocolVersion};
		return new DatagramPacket(data, data.length, destination);
	}

	/**
	 * Creates a SessionMessage suitable for sending to the client from the
	 * server after the server receives the SessionRequest.
	 * @param sessionId the id of the session.
	 * @param version the protocol version.
	 * @param format the String name of the stream format. Must be less than 256 characters.
	 * @param challengeValue the random int that will be hashed with the shared secret by the client.
	 * @return
	 */
	public DatagramPacket createSessionMessage(byte sessionId, byte version, int challengeValue, AudioFormat audioFormat) throws IOException {
		final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		final DataOutputStream out = new DataOutputStream(bytesOut);
		out.writeByte(MessageType.SESSION.getMessageId());
		out.writeByte(sessionId);
		out.writeByte(version);
		out.writeInt(challengeValue);
		out.writeFloat(audioFormat.getSampleRate());
		out.writeInt(audioFormat.getSampleSizeInBits());
		out.writeInt(audioFormat.getChannels());
		out.writeBoolean(audioFormat.getEncoding() == AudioFormat.Encoding.PCM_SIGNED ? true : false);
		out.writeBoolean(audioFormat.isBigEndian());
		out.flush();
		final byte[] data = bytesOut.toByteArray();
		out.close();
		return new DatagramPacket(data, data.length, destination);
	}

	/**
	 * Creates the challenge response message the client sends back to the sever
	 * after receiving the session message containing the challengeValue
	 * @param sessionId
	 * @param challengeValue
	 * @param secret
	 * @return a DatagramPacket that represents a ChallengeResponse
	 * @throws IOException
	 */
	public DatagramPacket createChallengeResponse(byte sessionId, int challengeValue, String secret) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream output = new DataOutputStream(new BufferedOutputStream(bytes));
		output.writeByte(MessageType.CHALLENGE_RESPONSE.getMessageId());
		output.writeByte(sessionId);
		messageDigest.update(intToByteArray(challengeValue));
		messageDigest.update(secret.getBytes("US-ASCII"));
		final byte[] digest = messageDigest.digest();
		output.writeInt(digest.length);
		output.write(digest, 0, digest.length);
		output.flush();
		final byte[] data = bytes.toByteArray();
		output.close();
		return new DatagramPacket(data, data.length, destination);
	}

	/**
	 * Creates a RechallengeMessages. This messages is sent to the client when
	 * it fails authentication.
	 * @param sessionId the byte sessionId of the recipient
	 * @param challengeValue the new int challengeValue
	 * @return a DatagramPacket that represents an ERP RechallengeMessage
	 * @throws SocketException
	 */
	public DatagramPacket createRechallengeMessage(byte sessionId, int challengeValue) throws SocketException {
		final byte[] data = new byte[6]; //1 for message type, 1 for sessionId 4 fo challengeValue
		final ByteBuffer buffer = ByteBuffer.wrap(data);
		buffer.put(MessageType.RECHALLENGE.getMessageId());
		buffer.put(sessionId);
		buffer.putInt(challengeValue);
		return new DatagramPacket(data, data.length, destination);
	}

	/**
	 * Utility method to convert an integer to a byte array.
	 * @param value the int to convert.
	 * @return a byte array of size 4.
	 */
	public byte[] intToByteArray(int value) {
		return new byte[]{
					(byte) (value >>> 24),
					(byte) (value >>> 16),
					(byte) (value >>> 8),
					(byte) value};
	}

	/**
	 * Creates the disconnect message.
	 * @param sessionId the byte id of the session being disconnected
	 * @return a DatagramPacket representing a disconnect message.
	 * @throws SocketException
	 */
	public DatagramPacket createDisconnectMessage(byte sessionId) throws SocketException {
		final byte[] data = new byte[]{MessageType.DISCONNECT.getMessageId(), sessionId};
		return new DatagramPacket(data, data.length, destination);
	}

	/**
	 * Message sent to the client when it fails authentication an implementation
	 * dependent number of times.
	 * @param sessionId the byte sessionId of the recipient
	 * @param errorCode the int errorCode of why the authentication failed.
	 * @return a DatagramPacket representing an authentication error message.
	 * @throws SocketException
	 * @throws IOException
	 */
	public DatagramPacket createAuthenticationError(byte sessionId, int errorCode) throws SocketException, IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(sessionId);
		baos.write(intToByteArray(errorCode), 0, 4);
		final byte[] data = baos.toByteArray();
		baos.close();
		return new DatagramPacket(data, data.length, destination);
	}

	public DatagramPacket createStreamMessage(byte sessionId, int sequenceNumber, byte[] data, byte[] crc) throws SocketException, IOException {
		final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		final DataOutputStream outputStream = new DataOutputStream(bytesOut);
		outputStream.writeByte(MessageType.STREAM.getMessageId());
		outputStream.writeByte(sessionId);
		outputStream.writeInt(sequenceNumber);
		outputStream.writeInt(data.length);
		outputStream.write(data, 0, data.length);
		outputStream.writeInt(crc.length);
		outputStream.write(crc, 0, crc.length);
		final byte[] outputData = bytesOut.toByteArray();
		return new DatagramPacket(outputData, outputData.length, destination);
	}

	/**
	 * Creates a pause message.
	 * @param sessionId the recipients sessionId
	 * @param isPaused true if the streaming should be paused, false otherwise
	 * @return a DatagramPacket representing a pause message.
	 * @throws SocketException
	 */
	public DatagramPacket createPauseMessage(byte sessionId, boolean isPaused) throws SocketException {
		final byte[] data = new byte[]{MessageType.PAUSE.getMessageId(), sessionId, isPaused ? (byte)1 : (byte)0};		
		return new DatagramPacket(data, data.length, destination);
	}
	
	public DatagramPacket createThrottleMessage(byte sessionId, int rateDiff) throws SocketException, IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final DataOutputStream output = new DataOutputStream(new BufferedOutputStream(bytes));
		output.writeByte(MessageType.THROTTLE.getMessageId());
		output.writeByte(sessionId);
		output.writeInt(rateDiff);
		output.flush();
		final byte[] data = bytes.toByteArray();
		output.close();
		return new DatagramPacket(data, data.length, destination);
	}
}
