package a3.a3droid;

public class Constants {

	public static final short CONTACT_PORT = 42;
	public static final String PREFIX = "a3.group.";
	public static final String A3_SEPARATOR = " ";
	public static final String PACKAGE_NAME = "a3.a3droid";
	public static final String MERGE_SUFFIX = ".merge";

	public static final int SESSION_LOST = 0;
	public static final int NEW_SUPERVISOR = 1;
	public static final int TIMER_FIRED = 2;
	public static final int MEMBER_ADDED = 3;
	public static final int MEMBER_REMOVED = 4;
	public static final int SUPERVISOR_ELECTION = 5;
	public static final int SUPERVISOR_FITNESS_FUNCTION_REQUEST = 6;
	public static final int SUPERVISOR_FITNESS_FUNCTION_REPLY = 7;
	public static final int WAIT_SUPERVISOR_FITNESS_FUNCTION_REQUEST = 8;
	public static final int WAIT_SUPERVISOR_FITNESS_FUNCTION = 9;	
	public static final int WAIT_NEW_SUPERVISOR = 10;
	
	public static final int SUBSCRIPTION = 11;
	public static final int UNSUBSCRIPTION = 12;
	
	public static final int ADD_TO_HIERARCHY = 13;
	public static final int REMOVE_FROM_HIERARCHY = 14;
	public static final int HIERARCHY = 15;
	
	public static final int PEERS_REQUEST = 16;
	public static final int HIERARCHY_REQUEST = 17;
	public static final int REVERSE_STACK = 18;
	public static final int STACK_REPLY = 19;
	public static final int PEERS_REPLY = 20;
	public static final int HIERARCHY_REPLY = 21;
	public static final int GET_HIERARCHY = 22;
	public static final int STACK_REQUEST = 23;
	
	public static final int MERGE = 24;
	public static final int SPLIT = 25;
	
	public static final int BOOLEAN_SPLIT_FITNESS_FUNCTION = 26;
	public static final int INTEGER_SPLIT_FITNESS_FUNCTION = 27;
	public static final int NEW_SPLITTED_GROUP = 28;
	public static final int NEW_GROUP = 29;
	protected static final int WAIT_MERGE = 30;
}
