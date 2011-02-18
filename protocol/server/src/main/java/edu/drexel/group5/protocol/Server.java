package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class Server extends Thread {
	private final Executor executor = Executors.newCachedThreadPool();
	private final int port;
	private final DatagramSocket socket;
	private static final int BUFFER_LENGTH = 2;

	public Server(int port) {
		super("Streaming Protocol Server");
		Preconditions.checkArgument(port >= 0 && port <= 65535, port + " is not a valid port");
		this.port = port;
		try {
			this.socket = new DatagramSocket(port);
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a ServerSocket on port: " + port + " please start the server again with a different unused port", ex);
		}
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				final byte[] buffer = new byte[BUFFER_LENGTH];
				final DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);
				socket.receive(packet);
				executor.execute(new ConnectionHandler(packet));
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		Preconditions.checkArgument(args.length == 1, "No port number given! Please start the server by giving a valid port number.");
		Server server = new Server(Integer.parseInt(args[0]));
		server.start();
	}

}
