package edu.drexel.group5.protocol;

import edu.drexel.group5.MessageType;
import java.net.DatagramPacket;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class does the work of processing the server packets 
 */
public class PacketHandler extends Thread {

	private static final Logger logger = Logger.getLogger(PacketHandler.class.getName());
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;

	public PacketHandler(LinkedBlockingQueue<DatagramPacket> packetQueue) {
		super("Packet Handler");
		this.packetQueue = packetQueue;
	}

	@Override
	public void run() {
		while (!isInterrupted()) {
			try {
				DatagramPacket packet = packetQueue.take();
				byte[] data = packet.getData();
				MessageType message = MessageType.getMessageTypeFromId(data[0]);
				String ip = packet.getAddress().getHostAddress();
				switch (message) {
					case SESSION:
						break;
					case CHALLENGE:
						break;
					case CHALLENGE_RESULT:
						break;
					case AUTHENTICATION_ERROR:
						break;
					case STREAM:
						break;
					case STREAM_ERROR:
						break;
					default:
						logger.log(Level.WARNING, "Received an unexpected message: {0} dropping the packet", message);
				}
			} catch (InterruptedException ex) {
				interrupt();
				logger.log(Level.INFO, "PacketHandler interrupt received shutting down...");
			}
		}
	}
}
