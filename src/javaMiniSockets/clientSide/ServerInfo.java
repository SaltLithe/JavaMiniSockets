package javaMiniSockets.clientSide;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;

/**
 * Class that contains information about the server an AsynchronousClient is
 * connected and essential instances of a series of objects.
 * 
 * @author Carmen Gómez MOreno
 *
 */

public class ServerInfo {
	protected AsynchronousServerSocketChannel client;
	protected AsynchronousSocketChannel serverIn;
	protected ByteBuffer inputBuffer;
	protected ByteArrayInputStream serverInputBAOS;
	protected ObjectInputStream serverInput;

	protected ServerInfo() {
	}

}
