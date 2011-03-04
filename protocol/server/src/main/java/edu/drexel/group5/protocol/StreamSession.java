package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import edu.drexel.group5.MessageType;
import edu.drexel.group5.PacketFactory;
import edu.drexel.group5.State;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class StreamSession implements Runnable {

	private static final Logger logger = Logger.getLogger(StreamSession.class.getName());
	private static final byte SERVER_VERSION = 1;
	private static final String STREAM_FORMAT = "DEFAULT";
	private final LinkedBlockingQueue<DatagramPacket> packetQueue;
	private final DatagramPacket packet;
	private final PacketFactory packetFactory;
	private ObjectInputStream objectIn;
	private ObjectOutputStream objectOut;
	private ByteArrayOutputStream bytesOut;
	private DatagramSocket socket;
	private InetAddress clientIp;
	private int clientPort;
	private byte sessionId;
	private edu.drexel.group5.State state;

	/**
	 *
	 * @param packet a DatagramPacket that represents a SESSION_REQUEST message.
	 * The data in this packet will be used for future communication with the
	 * client.
	 */
	public StreamSession(DatagramPacket packet, LinkedBlockingQueue<DatagramPacket> packetQueue, int id) {
		Preconditions.checkNotNull(packet);
		this.packet = packet;
		this.packetQueue = packetQueue;		
		this.sessionId = (byte)id;
		this.clientIp = packet.getAddress();
		this.clientPort = packet.getPort();
		this.packetFactory = new PacketFactory(clientPort, clientIp);
		
		// set up socket for this session to send packets to client
		try {
			this.socket = new DatagramSocket();
		} catch (IOException ex) {
			throw new RuntimeException("Could not create a Session Socket: ", ex);
		}
				
		try {
			//objectIn = new ObjectInputStream(new BufferedInputStream(new ByteArrayInputStream(packet.getData())));
			bytesOut = new ByteArrayOutputStream();
			objectOut = new ObjectOutputStream(new BufferedOutputStream(bytesOut));
			//MessageType message = MessageType.getMessageTypeFromId(objectIn.readByte());			

		} catch (IOException ex) {
			logger.log(Level.SEVERE, "An error occured handling a message from client: " + packet.toString(), ex);
			Thread.currentThread().interrupt();
		}
		
		// send Session Message to client
		try {
			socket.send(packetFactory.createSessionMessage(sessionId, SERVER_VERSION, STREAM_FORMAT));
    		logger.log(Level.INFO, "Sent Session Message");
			state = State.CONNECTED;
    	} catch(IOException ex) {
    		logger.log(Level.WARNING, "Could not send Session Message");
    	}
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			//need to setup and actually start sending the stream messages
		}
	}


}
