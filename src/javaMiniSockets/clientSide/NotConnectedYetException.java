package javaMiniSockets.clientSide;

/**
 * 
 * @author Carmen Gómez Moreno
 *
 */
class NotConnectedYetException extends Exception {

	private static final long serialVersionUID = 1L;

	public NotConnectedYetException(String errorMessage) {
		super(errorMessage);
	}

}
