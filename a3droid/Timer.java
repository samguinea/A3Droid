package a3.a3droid;

/**This class is used in A3Channel and in Service, which implement the interface "TimerInterface".
 * After a 2 seconds timeout, it calls TimerInterface.timerFired(int), to notify the timeout fired.
 * @author Francesco
 *
 */
public class Timer extends Thread{

	/**The TimerInterface to notify at timeout firing time.*/
	private TimerInterface channel;
	
	/**It indicates why the timeout is needed.
	 * It is passed in timerFired(int), in order for the TimerInterface to know which timeout fired.
	 */
	private int reason;

	/**The time to wait before timer firing.*/
	private int timeToWait;
	
	/**
	 * @param channel The TimerInterface to notify at timeout firing time.
	 * @param reason It indicates why the timeout is needed on "channel".
	 */
	public Timer(TimerInterface channel, int reason){
		super();
		this.channel = channel;
		this.reason = reason;
		timeToWait = 2000;
	}
	
	/**
	 * @param timerInterface The TimerInterface to notify at timeout firing time.
	 * @param reason It indicates why the timeout is needed on "channel".
	 * @param timeout The time to wait before timer firing.
	 */
	public Timer(TimerInterface timerInterface, int reason, int timeout) {
		// TODO Auto-generated constructor stub
		this(timerInterface, reason);
		timeToWait = timeout;
	}

	/**
	 * It notify timeout firing after a 2s timeout.
	 */
	@Override
	public void run(){
		try {
			sleep(timeToWait);
			channel.timerFired(reason);
		} catch (Exception e) {
			// TODO Auto-generated catch block
		}
	}
}
