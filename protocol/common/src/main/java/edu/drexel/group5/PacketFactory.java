package edu.drexel.group5;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.nio.charset.Charset;
import java.security.MessageDigest;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class PacketFactory {
	
	private InetSocketAddress destination;

	/**
	 * Constructor that will set all packets created by this factory to the
	 * destination given in the constructor.
	 * @param destinationPort the destination port.
	 * @param destinationAddress the destination address.
	 */
	public PacketFactory(int destinationPort, InetAddress destinationAddress) {
		destination = new InetSocketAddress(destinationAddress, destinationPort);
	}

	public PacketFactory(InetSocketAddress destination) {
		this.destination = destination;
	}



	public DatagramPacket createSessionRequest(byte protocolVersion) throws SocketException {
		final byte[] data = new byte[]{MessageType.SESSION_REQUEST.getMessageId(), protocolVersion};
		final DatagramPacket packet = new DatagramPacket(data, data.length, destination);
		return packet;
	}

	/**
	 *
	 * @param sessionId the id of the session.
	 * @param version the protocol version.
	 * @param format the String name of the stream format. Must be less than 256 characters.
	 * @return
	 */
	public DatagramPacket createSessionMessage(byte sessionId, byte version, String format) throws IOException {
		if (format.length() > 256) {
			throw new IllegalArgumentException("Format string must be less than 256 characters!");
		}
		byte[] formatBytes = format.getBytes(Charset.forName("US-ASCII"));
		final ByteArrayOutputStream bytesOut = new ByteArrayOutputStream();
		final ObjectOutputStream out = new ObjectOutputStream(bytesOut);
		out.writeByte(MessageType.SESSION.getMessageId());
		out.writeByte(sessionId);
		out.writeByte(version);
		out.writeByte(formatBytes.length);
		out.write(formatBytes);
		final byte[] data = bytesOut.toByteArray();
		out.close();
		return new DatagramPacket(data, data.length, destination);
	}


	public DatagramPacket createChallengeResponse(byte sessionId, int response) throws IOException {
		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		final ObjectOutputStream output = new ObjectOutputStream(bytes);
		output.writeByte(MessageType.CHALLENGE_RESPONSE.getMessageId());
		output.writeByte(sessionId);
		output.writeInt(response);
		final byte[] data = bytes.toByteArray();
		output.close();
		return new DatagramPacket(data, data.length, destination);
	}

	public DatagramPacket createDisconnectMessage(byte sessionId) throws SocketException {
		final byte[] data = new byte[]{MessageType.DISCONNECT.getMessageId(), sessionId};
		return new DatagramPacket(data, data.length, destination);
	}
}
