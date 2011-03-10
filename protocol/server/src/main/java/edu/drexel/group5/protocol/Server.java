package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/**
 * This class is responsible for listening for UDP client messages. It does not
 * handle the packets it places them in a queue and the PacketHandler takes it
 * from there so the Server can immediately get back to listening for more
 * packets.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class Server extends Thread {
	private static final int DEFAULT_PORT = 32456;
	private static final Logger logger = Logger.getLogger(Server.class.getName());
	private static final int BUFFER_LENGTH = 128;
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final DatagramSocket socket;
	private final PacketHandler packetHandler;
	private final DiscoveryHandler discovery;

	public Server(int port, String pathToFile, AudioFormat format) throws SocketException {
		super("Streaming Protocol Server");
		Preconditions.checkArgument(port >= 0 && port <= 65535, "%s is not a valid port", port);
		logger.log(Level.INFO, "Stream Server starting on port: {0} ...", port);
		this.packetQueue = new LinkedBlockingQueue<DatagramPacket>();
		this.socket = new DatagramSocket(port);
		this.packetHandler = new PacketHandler(packetQueue, socket, pathToFile, format);
		this.packetHandler.start();
		this.discovery = new DiscoveryHandler(port);
		discovery.start();
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				final byte[] buffer = new byte[BUFFER_LENGTH];
				final DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
				socket.receive(packet);
				logger.log(Level.INFO, "Received packet from IP: {0}, Port: {1}", new Object[]{packet.getAddress(), packet.getPort()});
				packetQueue.put(packet);
			} catch (IOException ex) {
				logger.log(Level.WARNING, "Error handling packet!", ex);
			} catch (InterruptedException ex) {
				interrupt();
				logger.log(Level.INFO, "Server shutting down...");
			}
		}
	}

	public static void main(String[] args) throws IOException {
		File audioFile = new File(args[0]);
		Preconditions.checkArgument(audioFile.isFile(), "The argument for the path to the file to stream is not a file!");
		final AudioFileFormat audioFileFormat;

		try {
			audioFileFormat = AudioSystem.getAudioFileFormat(audioFile);
		} catch (UnsupportedAudioFileException ex) {
			Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "The file given to the server is not an audio file!", ex);
			System.exit(1);
			return;
		} catch (IOException ex) {
			Logger.getLogger(Server.class.getName()).log(Level.SEVERE, "Could not read the file at: " + audioFile, ex);
			System.exit(1);
			return;
		}
		AudioFormat format = audioFileFormat.getFormat();
		Server server = null;
		if (args.length == 1) {
			server = new Server(DEFAULT_PORT, args[0], format);
		} else if (args.length == 2) {
			server = new Server(Integer.parseInt(args[1]), args[0], format);
		} else {
			logger.log(Level.SEVERE, "Illegal number of arguments given to the server! Start the server with 1 argument path-to-file or 2 arguments path-to-file port");
			System.exit(1);
		}
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
