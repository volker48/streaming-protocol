package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class Server extends Thread {
	private final Executor executor = Executors.newCachedThreadPool();
	private final int port;
	private final ServerSocket socket;

	public Server(int port) {
		super("Streaming Protocol Server");
		Preconditions.checkArgument(port >= 0 && port <= 65535, port + " is not a valid port");
		this.port = port;
		try {
			this.socket = new ServerSocket(port);
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a ServerSocket on port: " + port + " please start the server again with a different unused port", ex);
		}
	}

	public void run() {
		while (!isInterrupted()) {
			try {
				final Socket client = socket.accept();
				executor.execute(new ConnectionHandler(client));
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}

	public static void main(String[] args) {
		
	}

}
