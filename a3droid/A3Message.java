package a3.a3droid;

import org.alljoyn.bus.annotation.Position;

/**This class represents the messages that are exchanged by the nodes through the channels.
 * In order for AllJoyn to correctly marshal and unmarshal this data structure,
 * fields must be public and a constructor without parameters must exist.
 * Being the fields public, their getters and their setters are not needed.
 */
public class A3Message {

	/**The address of the channel which sends this message.*/
	@Position(0)
	public String senderAddress;
	
	/**The kind of this message.*/
	@Position(1)
	public int reason;
	
	/**The data in this message.*/
	@Position(2)
	public String object;

	/**This must exists because AllJoyn needs it, but is never used in these API.*/
	public A3Message(){}
	
	/**
	 * @param reason The kind of this message.
	 * @param object The data in this message.
	 */
	public A3Message(int reason, String object){
		this.reason = reason;
		this.object = object;
		senderAddress = "";
	}
	
	@Override
	public String toString(){
		String reasonString;
		
		switch(reason){
		case Constants.NEW_SUPERVISOR: reasonString = "NEW_SUPERVISOR"; break;
		case Constants.SUBSCRIPTION: reasonString = "SUBSCRIPTION"; break;
		case Constants.UNSUBSCRIPTION: reasonString = "UNSUBSCRIPTION"; break;
		case Constants.ADD_TO_HIERARCHY: reasonString = "ADD_TO_HIERARCHY"; break;
		case Constants.REMOVE_FROM_HIERARCHY: reasonString = "REMOVE_FROM_HIERARCHY"; break;
		case Constants.HIERARCHY: reasonString = "HIERARCHY"; break;
		case Constants.PEERS_REQUEST: reasonString = "PEERS_REQUEST"; break;
		case Constants.HIERARCHY_REQUEST: reasonString = "HIERARCHY_REQUEST"; break;
		case Constants.REVERSE_STACK: reasonString = "REVERSE_STACK"; break;
		case Constants.STACK_REPLY: reasonString = "STACK_REPLY"; break;
		case Constants.PEERS_REPLY: reasonString = "PEERS_REPLY"; break;
		case Constants.HIERARCHY_REPLY: reasonString = "HIERARCHY_REPLY"; break;
		case Constants.GET_HIERARCHY: reasonString = "GET_HIERARCHY"; break;
		case Constants.STACK_REQUEST: reasonString = "STACK_REQUEST"; break;
		case Constants.MERGE: reasonString = "MERGE"; break;
		case Constants.SPLIT: reasonString = "SPLIT"; break;
		case Constants.SUPERVISOR_ELECTION: reasonString = "SUPERVISOR_ELECTION"; break;
		case Constants.SUPERVISOR_FITNESS_FUNCTION_REQUEST: reasonString = "SUPERVISOR_FITNESS_FUNCTION_REQUEST"; break;
		case Constants.BOOLEAN_SPLIT_FITNESS_FUNCTION: reasonString = "BOOLEAN_SPLIT_FITNESS_FUNCTION"; break;
		case Constants.INTEGER_SPLIT_FITNESS_FUNCTION: reasonString = "INTEGER_SPLIT_FITNESS_FUNCTION"; break;
		case Constants.NEW_SPLITTED_GROUP: reasonString = "NEW_SPLITTED_GROUP"; break;
		case Constants.NEW_GROUP: reasonString = "NEW_GROUP"; break;
		case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION: reasonString = "WAIT_SUPERVISOR_FITNESS_FUNCTION"; break;
		case Constants.WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST: reasonString = "WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST"; break;
		case Constants.WAIT_NEW_SUPERVISOR: reasonString = "WAIT_NEW_SUPERVISOR"; break;
		case Constants.SUPERVISOR_FITNESS_FUNCTION_REPLY: reasonString = "SUPERVISOR_FITNESS_FUNCTION_REPLY"; break;
		default: reasonString = String.valueOf(reason); break;
		}
		return senderAddress + " " + reasonString + " " + object;
	}
}
