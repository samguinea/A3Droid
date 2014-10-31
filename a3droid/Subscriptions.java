package a3.a3droid;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class contains the logic and the data structures to manage subscriptions.
 * It resides both on A3Channel and on Service, but data structures are not shared by them.
 * 
 * Subscriptions are used to send multicast messages to the channels which are interested in them:
 * in order to receive a certain kind of message, the channel must subscribe to it.
 * 
 * At its connection/reconnection time, the channel sends the list of its subscriptions to the Service.
 * The Service stores a list of interested channel for each kind of message.
 * Channels can subscribe and unsubscribe themselves runtime by sending appropriate messages.
 * 
 * While sending a multicast message, the supervisor sends the message to the Service.
 * The Service, basing on the reason of the message, retrieves the list of the channels interested in it,
 * and try sending it to all of them.
 * If a transmission to a channel fails, it is supposed that such channel isn't in the group anymore,
 * so its subscriptions are deleted.
 * 
 * Subscriptions are not needed when the list of the destinations of the message is known by the supervisor,
 * because there's no need to retrieve it on the Service.
 * 
 * @author Francesco
 *
 */
public class Subscriptions {

	/**The channel this Subscriptions belongs to.*/
	private UserInterface channel;
	
	/**The list of the kind of messages the channel "channel" is interested in receiving.
	 * It is sent to the supervisor when the channel "channel" joins the session,
	 * in order for it to receive the multicast messages it is interested in.*/
	private ArrayList<Integer> mySubscriptions;
	
	/**For each kind of message, the list of the addresses of the interested channels.*/
	private HashMap<Integer, ArrayList<String>> groupSubscriptions;

	public Subscriptions(UserInterface ui){
		channel = ui;
		mySubscriptions = new ArrayList<Integer>();
		groupSubscriptions = new HashMap<Integer, ArrayList<String>>();
	}

	/**
	 * Handles the incoming messages about managing subscriptions.
	 * @param message The incoming message.
	 */
	public void onMessage(A3Message message){

		try{
			String address = message.senderAddress;

			switch(message.reason){
			case Constants.SUBSCRIPTION:
	
				/* This message is like "senderAddress Constants.SUBSCRIPTION reason1 reason2 ..." or "".
				 * It is sent by a channel in order to receive the multicast messages it is interested in,
				 * when it joins the session or when it becomes interested in new kinds of message.
				 * 
				 * If I receive this message, I am the supervisor:
				 * for every kind of message the channel "senderAddress" is interested in,
				 * I must retrieve the corresponding list of destinations and add "senderAddress" to it.
				 */
				String newSubscriptions = message.object;
	
				if(!newSubscriptions.equals("")){
					String[] splittedSubscriptions = newSubscriptions.split(Constants.A3_SEPARATOR);
					ArrayList<String> temp;
					int reason;
	
					synchronized(groupSubscriptions){
						for(int i = 0; i < splittedSubscriptions.length; i++){
	
							reason = Integer.valueOf(splittedSubscriptions[i]);
	
							if(groupSubscriptions.containsKey(reason)){
								temp = groupSubscriptions.get(reason);
								if(!temp.contains(address))
									temp.add(address);
							}
							else{
								temp = new ArrayList<String>();
								temp.add(address);
								groupSubscriptions.put(reason, temp);
							}
						}
						showOnScreen(groupSubscriptions.toString());
					}
				}
	
				break;
	
			case Constants.UNSUBSCRIPTION:
	
				/* This message is like "senderAddress Constants.UNSUBSCRIPTION reason".
				 * It is sent by a channel in order to no longer receive the multicast messages of type "reason".
				 * 
				 * If I receive this message, I am the supervisor:
				 * I must retrieve the list of destinations of the messages of type "reason"
				 * and remove "senderAddress" from it.
				 */
				String unsubscription = message.object;
	
				int reason = Integer.valueOf(unsubscription);
	
				synchronized(groupSubscriptions){
					if(groupSubscriptions.containsKey(reason)){
						ArrayList<String> temp = groupSubscriptions.get(reason);
						if(temp.contains(address))
							temp.remove(address);
					}
				}
				showOnScreen(groupSubscriptions.toString());
			}
		}catch(Exception e){}
	}

	/**
	 * Creates the string representation of the type Subscriptions.
	 * The obtained string is like "reason1 reason2 ..." or "".
	 */
	@Override
	public synchronized String toString(){

		String result = "";

		if(!mySubscriptions.isEmpty()){
			result = result + mySubscriptions.get(0);
			for(int i = 1; i < mySubscriptions.size(); i ++){
				result = result + Constants.A3_SEPARATOR + mySubscriptions.get(i);
			}
		}
		return result;
	}

	/**
	 * @param reason The kind of the message to be sent.
	 * @return The list of the channels interested in messages of type "reason".
	 */
	public synchronized ArrayList<String> getSubscriptions(int reason){
		if(groupSubscriptions.containsKey(reason))
			return groupSubscriptions.get(reason);
		return new ArrayList<String>();
	}

	/**
	 * Removes all the subscriptions of the channel "removedMember", because it left the group.
	 * @param removedMember The address of the channel which left the group.
	 */
	public synchronized void cancelSubscriptions(String removedMember) {
		// TODO Auto-generated method stub
		
		ArrayList<String> temp;
		
		for(int i : groupSubscriptions.keySet()){
			temp = groupSubscriptions.get(i);
			temp.remove(removedMember);
			if(temp.isEmpty())
				groupSubscriptions.remove(i);
		}
		showOnScreen(groupSubscriptions.toString());
		
	}
	
	/**
	 * It adds a new subscription to the list "mySubscriptions" used on the channel.
	 * @param reason The subscription to add.
	 */
	public synchronized void subscribe(int reason){

		if(!mySubscriptions.contains(reason)){
			mySubscriptions.add(reason);
		}
	}

	/**
	 * It removes a subscription from the list "mySubscriptions" used on the channel.
	 * @param reason The subscription to remove.
	 */
	public synchronized void unsubscribe(int reason) {
		// TODO Auto-generated method stub
		if(mySubscriptions.contains(reason)){
			mySubscriptions.remove((Object)reason);
		}
	}
	
	private void showOnScreen(String string) {
		// TODO Auto-generated method stub
		channel.showOnScreen(string);
	}
}
