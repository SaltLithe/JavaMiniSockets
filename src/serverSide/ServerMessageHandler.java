package serverSide;

import java.io.Serializable;

public interface ServerMessageHandler {

	/**
	 * Called by the server when a message has been sent from a client
	 * 
	 * @param message
	 * @param client
	 */
	public void onMessageSent(Serializable message, ClientInfo client);

	/**
	 * Called by the server when the server when it is ready to accept clients
	 */
	public void onReady();

	public void onDisconnect();

	/**
	 * Called by the server when it has established a connection with a new client
	 * 
	 * @param client
	 */
	public void onServerConnect(ClientInfo client);

	/**
	 * Called by the server when a new client has connected to the server
	 * 
	 * @param client
	 */
	public void onClientConnect(ClientInfo client);

	/**
	 * Called by the server whenever a client disconnects
	 * 
	 * @param client
	 */
	public void onClientDisconnect(ClientInfo client);

}
