package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class is responsible for listening for UDP client messages. It does not
 * handle the packets it places them in a queue and the PacketHandler takes it
 * from there so the Server can immediately get back to listening for more
 * packets.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class Server extends Thread {

	private static final Logger logger = Logger.getLogger(Server.class.getName());
	private static final int BUFFER_LENGTH = 128; //TODO: This can be lowered I think the largest client-to-server message is pretty small.
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final int port;
	private final DatagramSocket socket;
	private final PacketHandler packetHandler;
	private final String pathToFile;

	public Server(int port, String pathToFile) {
		super("Streaming Protocol Server");
		Preconditions.checkArgument(port >= 0 && port <= 65535, port + " is not a valid port");
		Preconditions.checkArgument(!"".equals(pathToFile));
		logger.log(Level.INFO, "Stream Server starting on port: {0} ...", port);
		this.packetQueue = new LinkedBlockingQueue<DatagramPacket>();
		this.port = port;
		this.pathToFile = pathToFile;
		try {
			this.socket = new DatagramSocket(port);
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a ServerSocket on port: " + port + " please start the server again with a different unused port", ex);
		}
		this.packetHandler = new PacketHandler(packetQueue, socket, pathToFile);
		this.packetHandler.start();
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				final byte[] buffer = new byte[BUFFER_LENGTH];
				final DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
				socket.receive(packet);
				logger.log(Level.INFO, "Rcved packet from IP: {0}, Port: {1}", new Object[]{packet.getAddress(), packet.getPort()});
				packetQueue.add(packet);
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Error handling packet!", ex);
			}
		}
	}

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 2, "Invalid number of arguments! Usage: port path-to-file");
		Server server = new Server(Integer.parseInt(args[0]), args[1]);
		server.start();
		try {
			server.join();
		} catch (InterruptedException ex) {
			logger.log(Level.SEVERE, "The main thread was interrupted!", ex);
		}
		logger.log(Level.INFO, "Server shutting down, goodbye.");
		System.exit(0);
	}
}
