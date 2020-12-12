package javaMiniSockets.messages;

import java.io.Serializable;

public class HandShakeInternalMessage implements Serializable {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 8351128675938229367L;
	/**
	 * 
	 */
	public String address;
	public int port;

	public HandShakeInternalMessage() {}
	public HandShakeInternalMessage(String address, int port) {
		
		this.address = address;
		this.port = port; 
		
	}
	
 
	
	
}
