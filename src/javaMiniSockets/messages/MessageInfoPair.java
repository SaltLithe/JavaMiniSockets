package javaMiniSockets.messages;

import java.io.Serializable;

import javaMiniSockets.clientSide.ServerInfo;
import javaMiniSockets.serverSide.ClientInfo;

/**
 * Class used to process incoming messages , the reading loop saves every
 * message alongisde its client for the AsynchronousServer to process.
 * 
 * @author Carmen Gómez Moreno
 *
 */

public class MessageInfoPair {

	private Serializable message;
	private ClientInfo client;
	ServerInfo server;

	public MessageInfoPair(Serializable message, ClientInfo client) {
		this.setClient(client);
		this.setMessage(message);
	}

	public MessageInfoPair(Serializable message, ServerInfo server) {
		this.server = server;
		this.setMessage(message);
	}

	public ClientInfo getClient() {
		return client;
	}

	public void setClient(ClientInfo client) {
		this.client = client;
	}

	public Serializable getMessage() {
		return message;
	}

	public void setMessage(Serializable message) {
		this.message = message;
	}

}
