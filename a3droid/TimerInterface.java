package a3.a3droid;

/**The method to manage timeout firings.*/
public interface TimerInterface {

	/**
	 * Called by a Timer to notify its timeout firing.
	 * @param reason It indicates which timeout fired. The taken action will depend on this.
	 */
	public void timerFired(int reason);
}
