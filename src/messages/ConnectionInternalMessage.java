package messages;

import java.io.Serializable;

public class ConnectionInternalMessage implements Serializable {
	public static final long serialVersionUID = 1;

	private String address;
	private int openPort;

	public ConnectionInternalMessage(String a, int o) {
		setAddress(a);
		setOpenPort(o);
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}

	public int getOpenPort() {
		return openPort;
	}

	public void setOpenPort(int openPort) {
		this.openPort = openPort;
	}

}
