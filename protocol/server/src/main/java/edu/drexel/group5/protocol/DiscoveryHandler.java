/*
 * Copyright 2010, Drexel University
 */

package edu.drexel.group5.protocol;

import edu.drexel.group5.MessageType;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class DiscoveryHandler extends Thread {
	private static final Logger logger = Logger.getLogger(DiscoveryHandler.class.getName());
	private final int serverPort;
	private final DatagramSocket discoverySocket;

	public DiscoveryHandler(int serverPort) throws SocketException {
		this.serverPort = serverPort;
		this.discoverySocket = new DatagramSocket(65530);
		discoverySocket.setSoTimeout(1000);
		discoverySocket.setBroadcast(true);
	}

	@Override
	public void run() {
		logger.log(Level.INFO, "Server auto discovery running...");
		final byte[] herp = new byte[1];
		final DatagramPacket packet = new DatagramPacket(herp, herp.length);
		while (!isInterrupted()) {
			try {
				discoverySocket.receive(packet);
				logger.log(Level.INFO, "Received packet on discovery port");
				MessageType type = MessageType.getMessageTypeFromId(herp[0]) ;
				if (type != MessageType.HERP) {
					continue; //ignore all other messages
				}
				logger.log(Level.INFO, "Received Herp from {0}", packet.getSocketAddress());
				final byte[] derpBuffer = new byte[5];
				ByteBuffer bytes = ByteBuffer.wrap(derpBuffer);
				bytes.put(MessageType.DERP.getMessageId());
				bytes.putInt(serverPort);
				final DatagramPacket derp = new DatagramPacket(derpBuffer, 0, derpBuffer.length, packet.getSocketAddress());
				discoverySocket.send(derp);
				logger.log(Level.INFO, "Sent Derp in reply!");
			} catch (SocketTimeoutException ex) {
				//socket timed out check interrupt flag
			} catch (IOException ex) {
				//something bad happened
				logger.log(Level.SEVERE, "Error while listening for discovery messages!", ex);
				interrupt();
			}
		}
	}
}
