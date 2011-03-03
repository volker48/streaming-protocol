package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class opens a connection to the server to receive streaming media
 */
public class Client extends Thread {
	private static final Logger logger = Logger.getLogger(Client.class.getName());
	private static final int BUFFER_LENGTH = 128; //TODO: This can be lowered I think the largest client-to-server message is pretty small.
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final InetAddress serverAddress;
	private final int serverPort;
	private final DatagramSocket socket;
	private final PacketHandler packetHandler;

	public Client(InetAddress serverAddress, int serverPort) {
		super("Streaming Protocol Client");
		Preconditions.checkArgument(serverPort >= 0 && serverPort <= 65535, serverPort + " is not a valid port");
		logger.log(Level.INFO, "Stream Client starting");
		this.packetQueue = new LinkedBlockingQueue<DatagramPacket>();
		this.serverAddress = serverAddress;
		this.serverPort = serverPort;
		this.packetHandler = new PacketHandler(packetQueue);
		try {
			this.socket = new DatagramSocket();
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a ClientSocket", ex);
		}
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				final byte[] buffer = new byte[BUFFER_LENGTH];
				final DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
				socket.receive(packet);
				logger.log(Level.INFO, "Rcved packet: {0}", packet);
				packetQueue.add(packet);
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Error handling packet!", ex);
			}
		}
	}

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 2, "No server address given! Please start the server by giving a valid port number.");
		InetAddress serverAddress = null;
		try {
			serverAddress = InetAddress.getByName(args[0]);
		} catch(UnknownHostException ex) {
			logger.log(Level.SEVERE, "Unknown server", ex);
		}
		Client client = new Client(serverAddress, Integer.parseInt(args[1]));
		client.start();
		try {
			client.join();
		} catch (InterruptedException ex) {
			logger.log(Level.SEVERE, "The main thread was interrupted!", ex);
		}
	}
}
