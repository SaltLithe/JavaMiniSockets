package clientSide;

import java.io.Serializable;

public interface ClientMessageHandler {

	public void onMessageSent(Serializable message, ServerInfo serverInfo);

	/**
	 * Called by the client when the server has established a connection
	 * 
	 * @param server
	 */
	public void onServerConnect(ServerInfo server);

	/**
	 * Called by the client when it has managed to connect with the server
	 */
	public void onConnect();

	/*
	 * Called by the client whenever the server disconnects
	 */

	public void onServerDisconnect(ServerInfo server);

	/**
	 * Called by the client when it disconnects
	 */
	public void onDisconnect();

}
