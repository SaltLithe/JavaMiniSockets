package javaMiniSockets.messages;

import java.io.Serializable;

public class HandShakeInternalMessage implements Serializable {
	
	
	 
	/**
	 * 
	 */
	private static final long serialVersionUID = -6466944337195024106L;
	public String address;
	public int port;

	public HandShakeInternalMessage(String address, int port) {
		
		this.address = address;
		this.port = port; 
		
	}
	
 
	
	
}
