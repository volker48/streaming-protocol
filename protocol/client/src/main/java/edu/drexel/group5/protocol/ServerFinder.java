package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class ServerFinder {

	private static final int MAX_RETRY = 5;

	public ServerFinder() {
	}

	public ServerInfo findServer() throws SocketException, UnknownHostException, IOException {
		final DatagramSocket socket = new DatagramSocket();
		socket.setSoTimeout(1000);
		socket.setBroadcast(true);
		final byte[] buffer = new byte[1];
		buffer[0] = MessageType.HERP.getMessageId();
		DatagramPacket herp = new DatagramPacket(buffer, 0, buffer.length, InetAddress.getByName("255.255.255.255"), 65530);
		socket.send(herp);
		int counter = 0;
		final byte[] derpBuffer = new byte[5];
		final DatagramPacket derp = new DatagramPacket(derpBuffer, derpBuffer.length);
		boolean received = false;
		while (counter < MAX_RETRY && !received) {
			try {
				socket.receive(derp);
				MessageType type = MessageType.getMessageTypeFromId(derpBuffer[0]);
				if (type != MessageType.DERP) {
					counter++;
					continue;
				}
				received = true;
			} catch (SocketTimeoutException ex) {
				counter++;
				socket.send(herp);
			}
		}
		if (!received) {
			return null;
		}
		ByteBuffer buf = ByteBuffer.wrap(derpBuffer);
		int serverPort = buf.getInt(1);
		return new ServerInfo(serverPort, derp.getAddress());
	}

	public static class ServerInfo {

		public final int port;
		public final InetAddress ip;

		public ServerInfo(int port, InetAddress ip) {
			Preconditions.checkArgument(port >= 0 && port <= 65535, port + " is not a valid port");
			this.port = port;
			this.ip = ip;
		}
	}
}
