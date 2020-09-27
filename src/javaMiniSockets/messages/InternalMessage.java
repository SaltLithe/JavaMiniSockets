package javaMiniSockets.messages;

import java.io.Serializable;

/**
 * Class that is used to send messages between AsynchronousServers and
 * AsynchronousClients alongside timestamps.
 * 
 * @author Carmen Gómez Moreno
 */
class InternalMessage implements Serializable {
	public static final long serialVersionUID = 1;

	Serializable message;
	long timestamp;

	protected InternalMessage(Serializable m, long t) {
		message = m;
		timestamp = t;
	}
}
