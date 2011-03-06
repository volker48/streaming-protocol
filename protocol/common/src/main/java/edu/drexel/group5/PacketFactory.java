package edu.drexel.group5;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 *
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

	public DatagramPacket createSessionRequest(byte protocolVersion) throws SocketException {
		final byte[] data = new byte[]{MessageType.SESSION_REQUEST.getMessageId(), protocolVersion};
		return new DatagramPacket(data, data.length, destination);
	}

	/**
	 *
	 * @param sessionId the id of the session.
	 * @param version the protocol version.
	 * @param format the String name of the stream format. Must be less than 256 characters.
	 * @param challengeValue the random int that will be hashed with the shared secret by the client.
	 * @return
	 */
	public DatagramPacket createSessionMessage(byte sessionId, byte version, String format, int challengeValue) throws IOException {
		if (format.length() > 256) {
			throw new IllegalArgumentException("Format string must be less than 256 characters!");
		}
		byte[] formatBytes = format.getBytes(Charset.forName("US-ASCII"));
		final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(6 + formatBytes.length); //1 for id, 1 for version, 4 for challengeValue
		final DataOutputStream out = new DataOutputStream(bytesOut);
		out.writeByte(MessageType.SESSION.getMessageId());
		out.writeByte(sessionId);
		out.writeByte(version);
		out.writeByte(formatBytes.length);
		out.write(formatBytes);
		out.writeInt(challengeValue);
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
		final ByteArrayOutputStream output = new ByteArrayOutputStream();
		output.write(MessageType.CHALLENGE_RESPONSE.getMessageId());
		output.write(sessionId);
		messageDigest.update(intToByteArray(challengeValue));
		messageDigest.update(secret.getBytes("US-ASCII"));
		final byte[] digest = messageDigest.digest();
		output.write(digest.length);
		output.write(digest);
		output.flush();
		final byte[] data = output.toByteArray();
		output.close();
		return new DatagramPacket(data, data.length, destination);
	}

	public DatagramPacket createChallengeResult(byte sessionId, byte result) throws SocketException {
		final byte[] data = new byte[3];
		data[0] = MessageType.CHALLENGE_RESULT.getMessageId();
		data[1] = sessionId;
		data[2] = result;
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

	public DatagramPacket createAuthenticationError(byte sessionId, int errorCode) throws SocketException, IOException {
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		baos.write(sessionId);
		baos.write(intToByteArray(errorCode), 0, 4);
		final byte[] data = baos.toByteArray();
		baos.close();
		return new DatagramPacket(data, data.length, destination);
	}

	public DatagramPacket createStreamMessage(byte sessionId, byte sequenceNumber, byte[] data, byte[] crc) throws SocketException, IOException {
		final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		final DataOutputStream outputStream = new DataOutputStream(bytesOut);
		outputStream.write(MessageType.STREAM.getMessageId());
		outputStream.write(sessionId);
		outputStream.write(sequenceNumber);
		outputStream.writeInt(data.length);
		outputStream.write(data, 0, data.length);
		outputStream.writeInt(crc.length);
		outputStream.write(crc, 0, crc.length);
		final byte[] outputData = bytesOut.toByteArray();
		return new DatagramPacket(outputData, outputData.length, destination);
	}
}
