package edu.drexel.group5;

import java.net.DatagramPacket;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class PacketFactory {

	public DatagramPacket createSessionRequest(byte protocolVersion) {
		final byte[] data = new byte[]{MessageType.SESSION_REQUEST.getMessageId(), protocolVersion};
		final DatagramPacket packet = new DatagramPacket(data, data.length);
		return packet;
	}
}
