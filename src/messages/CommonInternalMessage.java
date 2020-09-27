package messages;

import java.io.Serializable;

/**
 * Class that is used to send messages between AsynchronousServers and
 * AsynchronousClients alongside timestamps.
 * 
 * @author Carmen Gómez Moreno
 */
public class CommonInternalMessage implements Serializable {
	public static final long serialVersionUID = 1;

	private Serializable message;
	private long timestamp;

	public CommonInternalMessage(Serializable m, long t) {
		setMessage(m);
		setTimestamp(t);
	}

	public Serializable getMessage() {
		return message;
	}

	public void setMessage(Serializable message) {
		this.message = message;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
}
