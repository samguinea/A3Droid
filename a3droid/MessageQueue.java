package a3.a3droid;

import java.util.ArrayList;

/**This class resides on A3Channel and it is a queue where to store the messages to the supervisor.*/
public class MessageQueue {

	/**The list of the messages to send.*/
	private ArrayList<A3Message> messages;
	
	public MessageQueue(){
		messages = new ArrayList<A3Message>();
	}
	
	/**It adds a message to the queue.
	 * @param message The message to be added to the queue.
	 */
	public synchronized void enqueue(A3Message message){
		messages.add(message);
		notify();
	}
	
	/**It removes a message from the queue.
	 * @param message The message to be removed from the queue.
	 */
	public synchronized void dequeue(){
		messages.remove(0);
	}
	
	/**It blocks if there are no messages in the queue.
	 * @return The first message in the queue.
	 */
	public synchronized A3Message get(){
		while(messages.isEmpty()){
			try {
				wait();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
			}
		}
		return messages.get(0);
	}
	
	@Override
	public synchronized String toString(){
		return "\n" + messages.toString() + "\n";
	}
}
