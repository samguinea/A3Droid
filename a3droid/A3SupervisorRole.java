package a3.a3droid;

import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.annotation.BusSignalHandler;

/**
 * This class represents the logic executed on a supervisor.
 * It adds an AllJoyn signal receiver to the role logic, in order to receive messages from followers.
 * @author Francesco
 *
 */
public abstract class A3SupervisorRole extends A3Role implements BusObject, TimerInterface{

	/**The object that collects integer fitness function values for random split.*/
	private FitnessFunctionManager fitnessFunctions;
	
	/**The number of nodes to transfer in the new group.*/
	private int nodesToTransfer;
	
	public A3SupervisorRole(){
		super();
		fitnessFunctions = new FitnessFunctionManager(this);
	}

	@Override
	public abstract void logic();

	@Override
	public abstract void receiveApplicationMessage(A3Message message);

	@BusSignalHandler(iface = Constants.PACKAGE_NAME + ".A3ServiceInterface", signal = "SupervisorReceive")
	public void SupervisorReceive(A3Message message) {
		onMessage(message);
	}

	@Override
	public void handleMessage(A3Message message) {

		super.handleMessage(message);
		try{
			boolean ok = false;
			
			A3Message reply = null;
			String[] object;

			switch(message.reason){

			case Constants.GET_HIERARCHY:

				/* This message is like "senderAddress Constants.GET_HIERARCHY"
				 * Transmitted by a channel when it joins the session.
				 * 
				 * The sending channel needs to know which is the hierarchy by now,
				 * in order to determine if it can become supervisor when needed.
				 * 
				 * I answer the channel with the hierarchy. 
				 * The reply message is like
				 * "Constants.HIERARCHY name1 name2 ... n" or "Constants.HIERARCHY n", if the hierarchy is empty.
				 * n is the number of subgroups splitted by a group.
				 */
				channel.sendUnicast(new A3Message(Constants.HIERARCHY, channel.getHierarchy().toString()), message.senderAddress);
				break;

			case Constants.STACK_REQUEST:
				//"senderAddress Constants.STACK_REQUEST parentGroupName".
				try{
					ok = node.actualStack(message.object, getGroupName());
					reply = new A3Message(Constants.STACK_REPLY, message.object + Constants.A3_SEPARATOR + ok);
					channel.sendUnicast(reply, message.senderAddress);
				} catch (Exception e) {}
				break;
				
			case Constants.PEERS_REQUEST:
				//"senderAddress Constants.PEERS_REQUEST otherGroupName".
				ok = node.actualStack(message.object, getGroupName());
				reply = new A3Message(Constants.PEERS_REPLY, message.object + Constants.A3_SEPARATOR + ok);
				channel.sendUnicast(reply, message.senderAddress);
				break;

			case Constants.HIERARCHY_REQUEST:
				//"senderAddress Constants.HIERARCHY_REQUEST parentGroupName otherGroupName".
				object = message.object.split(Constants.A3_SEPARATOR);
				ok = node.actualStack(object[0], getGroupName());
				reply = new A3Message(Constants.HIERARCHY_REPLY, message.object
						+ Constants.A3_SEPARATOR + ok);
				
				channel.sendUnicast(reply, message.senderAddress);
				break;

			case Constants.REVERSE_STACK:
				//"senderAddress Constants.REVERSE_STACK parentGroupName".
				node.actualReverseStack(message.object, getGroupName());
				break;

			case Constants.INTEGER_SPLIT_FITNESS_FUNCTION:
				
				/* "senderAddress Constants.INTEGER_SPLIT_FITNESS_FUNCTION integerValue".
				 * 
				 * I don't receive Constants.SUPERVISOR_FITNESS_FUNCTIONs,
				 * because it's the Service that collects them.
				 * 
				 * I can't receive Constants.BOOLEAN_SPLIT_FITNESS_FUNCTIONs:
				 * if a node satisfies a boolean fitness function, it changes group
				 * without notifying anyone. 
				 */
				fitnessFunctions.onMessage(message);
				break;

			default: receiveApplicationMessage(message); break;
			}
		} catch (Exception e) {}
	}

	/**It starts collecting integer fitness function values for random split. 
	 */
	public void startSplit(int nodesToTransfer) {
		// TODO Auto-generated method stub
		this.nodesToTransfer = nodesToTransfer;
		fitnessFunctions.startCollectingFitnessFunctions(Constants.SPLIT);
	}

	@Override
	public void timerFired(int reason) {
		// I can only have a split operation, so I don't check the value of reason.
		try {
			String[] selectedNodes = fitnessFunctions.getBest(nodesToTransfer);
			A3Message message = new A3Message(Constants.SPLIT, "");
			
			for(String node : selectedNodes)
				channel.sendUnicast(message, node);
			
		} catch (Exception e) {}
	}
}
