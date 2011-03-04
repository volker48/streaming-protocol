package edu.drexel.group5.protocol;

import edu.drexel.group5.MessageType;
import java.net.DatagramPacket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class does the work of processing the client packets and provisioning
 * a new StreamSession if needed or routes the packet to an existing session.
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class PacketHandler extends Thread {

	private static final Logger logger = Logger.getLogger(PacketHandler.class.getName());
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final Map<String, LinkedBlockingQueue<DatagramPacket>> sessions;
	private final Executor executor = Executors.newCachedThreadPool();
	private int numSessions = 0;

	public PacketHandler(LinkedBlockingQueue<DatagramPacket> packetQueue) {
		super("Packet Handler");
		this.packetQueue = packetQueue;
		sessions = new ConcurrentHashMap<String, LinkedBlockingQueue<DatagramPacket>>();
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
					case SESSION_REQUEST:
						if (sessions.containsKey(ip)) {
							logger.log(Level.WARNING, "Received a SessionRequest for an existing session! Packet Info: {0}", packet);
							continue;
						}
						final LinkedBlockingQueue<DatagramPacket> sessionsQueue = new LinkedBlockingQueue<DatagramPacket>();
						sessions.put(ip, sessionsQueue);
						final StreamSession session = new StreamSession(packet, sessionsQueue, numSessions++);
						executor.execute(session);
						break;
					case THROTTLE:
					case CHALLENGE_RESPONSE:
					case DISCONNECT:
						if (!sessions.containsKey(ip)) {
							logger.log(Level.WARNING, "Received MessageType: {0}, but no session exists for IP: {1}", new Object[]{message, ip});
						}
						sessions.get(ip).put(packet);
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