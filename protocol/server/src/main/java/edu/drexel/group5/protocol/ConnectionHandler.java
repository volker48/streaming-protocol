package edu.drexel.group5.protocol;

import com.google.common.base.Preconditions;
import com.sun.org.apache.xml.internal.dtm.ref.DTMDefaultBaseIterators.PrecedingIterator;
import java.net.Socket;

/**
 *
 * @author Marcus McCurdy <marcus@drexel.edu>
 */
public class ConnectionHandler implements Runnable {

	private final Socket connection;

	public ConnectionHandler(Socket connection) {
		Preconditions.checkNotNull(connection);
		Preconditions.checkArgument(connection.isConnected());
		this.connection = connection;
	}

	public void run() {
		throw new UnsupportedOperationException("Not supported yet.");
	}
}
