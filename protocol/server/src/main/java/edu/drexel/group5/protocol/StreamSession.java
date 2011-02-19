package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class StreamSession implements Runnable {

	private static final Logger logger = Logger.getLogger(StreamSession.class.getName());
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final DatagramPacket packet;
	private ObjectInputStream objectIn;
	private ObjectOutputStream objectOut;
	private ByteArrayOutputStream bytesOut;

	/**
	 *
	 * @param packet a DatagramPacket that represents a SESSION_REQUEST message.
	 * The data in this packet will be used for future communication with the
	 * client.
	 */
	public StreamSession(DatagramPacket packet, LinkedBlockingQueue<DatagramPacket> packetQueue) {
		Preconditions.checkNotNull(packet);
		this.packet = packet;
		this.packetQueue = packetQueue;
		try {
			objectIn = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(packet.getData())));
			bytesOut = new ByteArrayOutputStream();
			objectOut = new ObjectOutputStream(new BufferedOutputStream(bytesOut));
			MessageType message = MessageType.getMessageTypeFromId(objectIn.readByte());
		} catch (IOException ex) {
			logger.log(Level.SEVERE, "An error occured handling a message from client: " + packet.toString(), ex);
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
		}
	}


}
