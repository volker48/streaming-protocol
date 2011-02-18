package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import java.net.DatagramPacket;
import java.net.Socket;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class ConnectionHandler implements Runnable {

	private final DatagramPacket packet;

	/**
	 *
	 * @param packet a DatagramPacket that represents a SESSION_REQUEST message.
	 * The data in this packet will be used for future communication with the
	 * client.
	 */
	public ConnectionHandler(DatagramPacket packet) {
		Preconditions.checkNotNull(packet);
		this.packet = packet;
	}

	public void run() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}
