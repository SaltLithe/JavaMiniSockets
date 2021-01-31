package javaMiniSockets.serverSide;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Class that contains information about clients connected to an
 * AsynchronousServer and essential instances of a series of objects.
 * 
 * @author Carmen Gómez Moreno
 *
 */

public class ClientInfo {

	protected AsynchronousServerSocketChannel server;
	protected SocketChannel clientOut;
	protected AsynchronousSocketChannel clientIn;
	protected ByteBuffer inputBuffer;
	protected long lastHeartBeatServerSide;
	protected ReentrantLock clientInputLock;
	protected ReentrantLock clientOutputLock;
	protected ByteArrayInputStream clientInputBAOS;
	protected ObjectInputStream clientInput;
	protected ByteArrayInputStream clientOutputBAOS;
	protected ObjectInputStream clientOutput;
	protected long newTimeStamp;
	protected long lastHeartBeat;
	protected int clientport; 

	@SuppressWarnings("unused")
	private AsynchronousSocketChannel client;

	public SocketAddress clientAddr;
	public int clientID;
	public ArrayList<String> lineList;

	protected ClientInfo() {
	}

	protected ClientInfo(AsynchronousServerSocketChannel server, AsynchronousSocketChannel client, ByteBuffer buffer,
			SocketAddress clientAddr, int clientID) {

		this.client = client;
		lineList = new ArrayList<String>();
		this.server = server;
		this.clientIn = client;
		this.inputBuffer = buffer;
		this.clientAddr = clientAddr;
		this.clientID = clientID;
		clientInputLock = new ReentrantLock();
		clientOutputLock = new ReentrantLock();
		clientInputLock.lock();
		clientOutputLock.lock();	

		clientInputLock.unlock();
		clientOutputLock.unlock();
		lastHeartBeat = 0;
		lastHeartBeatServerSide = 0;
	}

	protected void connectToClient(String clientIP, int clientport) {
		
		this.clientport = clientport; 

		try {
			clientOut = SocketChannel.open(new InetSocketAddress(clientIP,clientport));
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
}
