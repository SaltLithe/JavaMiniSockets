package javaMiniSockets.serverSide;

/**
 * 
 * @author Carmen Gómez Moreno
 *
 */
class MaximumConnectionsReachedException extends Exception {

	private static final long serialVersionUID = 1L;

	public MaximumConnectionsReachedException(String errorMessage) {
		super(errorMessage);
	}

}
