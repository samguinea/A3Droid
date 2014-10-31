package a3.a3droid;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This class represent a device, with the roles it can play in each group.
 * It contains the methods to connect to groups, to disconnect from them,
 * to send messages to their members and to manage groups hierarchy.
 * @author Francesco
 *
 */
public class A3Node extends Thread implements UserInterface{

	/**Channel state that indicates that the channel is connected to group "wait",
	 * because it would play a role that it can't play in its group.
	 */
	private static final int WAITING = 0;

	/**Channel state that indicates that the channel is connected to its group.*/
	private static final int CONNECTED = 1;

	/**Channel state that indicates that the channel is still connecting to its group,
	 * and that it is waiting to know the role it must play.
	 * If it discovers that it can't play that role, it disconnects and its state becomes "WAITING".
	 */
	private static final int CONNECTING = 2;

	/**Channel state that indicates that the channel isn't in the "channels" list,
	 * so it must be created.
	 */
	private static final int DOES_NOT_EXIST = 3;

	/**The user interface to interact with.*/
	private UserInterface ui;

	/**The list of the channels to communicate with the groups this node is connected to.
	 * There are also channels that are disconnected because they are in "wait" group.
	 * In such case, a channel to the group "wait" is connected and in this list.*/
	private ArrayList<A3Channel> channels;

	/**The state of the channels in list "channels".
	 * State can be "WAITING", "CONNECTED", "CONNECTING", "DOES_NOT_EXIST".
	 */
	private HashMap<String, Integer> channelsStatus;

	/**The list of roles this node can assume.
	 * I suppose that it can't change at runtime.
	 */
	private final ArrayList<String> roles;

	/**The list of the descriptors of the groups that can be present in the system.
	 * The groups splitted by other groups have their same descriptors.
	 */
	private final ArrayList<GroupDescriptor> groupDescriptors;

	/**
	 * 
	 * @param ui The user interface to interact with.
	 * @param roles The list of roles this node can assume.
	 * @param groupDescriptors The list of the descriptors of the groups that can be present in the system.
	 */
	public A3Node (UserInterface ui, ArrayList<String> roles, ArrayList<GroupDescriptor> groupDescriptors){

		super("node");
		this.ui = ui;
		channels = new ArrayList<A3Channel>();
		channelsStatus = new HashMap<String, Integer>();
		groupDescriptors.add(new WaitGroupDescriptor());
		this.groupDescriptors = groupDescriptors;

		if(roles == null)
			roles = new ArrayList<String>();

		roles.add(Constants.PACKAGE_NAME + ".WaitSupervisorRole");
		roles.add(Constants.PACKAGE_NAME + ".WaitFollowerRole");
		this.roles = roles;

		start();
	}

	/**Looks for a channel in the "channels" list.
	 * 
	 * @param groupName The name of the group to communicate with (i.e. to which the channel is connected).
	 * @return The channel connected to the group "groupName".
	 * @throws Exception No channel is connected to the group "groupName".
	 */
	public A3Channel getChannel(String groupName) throws Exception{

		A3Channel channel;

		synchronized(channels){
			for(int i = 0; i < channels.size(); i++){
				channel = channels.get(i);

				if(channel.getGroupName().equals(groupName))
					return channel;
			}
		}
		throw new Exception("NO CHANNEL WITH NAME " + groupName + ".");
	}

	/**Looks for a role in the "roles" list.
	 * 
	 * @param roleId The id of the role to look for.
	 * @return The role with "roleId" as id.
	 * @throws Exception No role has "roleId" as id.
	 */
	public A3Role getRole(String roleId) throws Exception{
		String role;

		synchronized(roles){
			for(int i = 0; i < roles.size(); i++){
				role = roles.get(i);
				if(role.equals(roleId))
					return (A3Role) Class.forName(roleId).getConstructor().newInstance();
			}
		}
		throw new Exception("NO ROLE WITH NAME " + roleId + ".");
	}

	/**Looks for a group descriptor in the "groupDescriptors" list.
	 * 
	 * @param groupName The name of the group whose descriptor is requested.
	 * @return The descriptor of the group "groupName".
	 * @throws Exception The descriptor of the group "groupName" doesn't exist,
	 * i.e. the group "groupName" and its subgroups don't exist in the system.
	 */
	public GroupDescriptor getGroupDescriptor(String groupName) throws Exception{
		GroupDescriptor descriptor;
		String name;

		synchronized(groupDescriptors){
			for(int i = 0; i < groupDescriptors.size(); i++){
				descriptor = groupDescriptors.get(i);
				name = descriptor.getName();

				/* Groups splitted by main groups have the same descriptor as their main groups
				 * and their names are extensions of the main group names.
				 */
				if(groupName.equals(name) || groupName.startsWith(name + "_"))
					return descriptor;
			}
		}
		throw new Exception("NO GROUP WITH NAME " + groupName + ".");
	}

	/**Determines if this node is the supervisor of the specified group.
	 * 
	 * @param groupName The name of the group.
	 * @return true This node is the supervisor of the group "groupName", false otherwise.
	 */
	public boolean isSupervisor(String groupName){

		A3Channel channel;

		try {
			channel = getChannel(groupName);
		} catch (Exception e) {
			return false;
		}
		return channel.isSupervisor();
	}

	/**Determines if this node has both the roles to connect to the specified group.
	 * 
	 * @param groupName The name of the group.
	 * @return true if this node has both the roles to connect to the group "groupName", false otherwise.
	 */
	public boolean hasRolesForGroup(String groupName){

		GroupDescriptor descriptor;

		try{
			descriptor = getGroupDescriptor(groupName);
			getRole(descriptor.getFollowerRoleId());
			getRole(descriptor.getSupervisorRoleId());
			return true;
		}catch(Exception e2){
			return false;
		}
	}

	/**It connects this node to the specified group, if it isn't already connected to it,
	 * or if a channel to the specified group is not in "WAITING" status.
	 * 
	 * @param groupName The name of the group to connect this node to.
	 * @param isResultRelevant
	 * It is used in order not to block the user interface
	 * while waiting to discover if the status of the new channel will be "CONNECTED" or "WAITING".
	 * Its value is true if it is important to know the result, so it is important to block while waiting it,
	 * false otherwise.
	 * User interface must always call "connect('groupName', false)" to avoid problems.
	 * @param forApplication true if the connection is requested by the application,
	 * false if it is requested by the system.
	 * @return true if the status of the channel to group "groupName" is "CONNECTED", false if it is "WAITING".
	 */
	public boolean connect(String groupName, boolean isResultRelevant, boolean forApplication){

		A3Channel channel = null;
		GroupDescriptor descriptor;
		A3FollowerRole followerRole = null;
		A3SupervisorRole supervisorRole = null;
		int status;

		synchronized(channelsStatus){
			if(channelsStatus.containsKey(groupName)){
				status = channelsStatus.get(groupName);
				try {
					channel = getChannel(groupName);
					if(forApplication)
						channel.setConnectedForApplication(true);
					else
						channel.setConnectedForSystem(true);
				} catch (Exception e) {}
			}
			else
				status = DOES_NOT_EXIST;
		}

		switch(status){
		case WAITING: return false;
		case CONNECTED: return true;
		case CONNECTING: 
			if(isResultRelevant){
				synchronized(channelsStatus){

					while((status = channelsStatus.get(groupName)) == CONNECTING){
						try {
							channelsStatus.wait();

						} catch (InterruptedException e) {}
					}
				}
				if(status == WAITING)
					return false;
				else
					return true;
			}
			else
				return true;

		case DOES_NOT_EXIST:

			boolean hasFollowerRole = false, hasSupervisorRole = false, followerOnly = false, supervisorOnly = false;

			try{
				descriptor = getGroupDescriptor(groupName);
			}catch(Exception ex){
				return false;
			}
			
			try{
				followerRole = (A3FollowerRole) getRole(descriptor.getFollowerRoleId());
				hasFollowerRole = true;
			}catch(Exception ex){
				hasFollowerRole = false;
			}
			try{
				supervisorRole = (A3SupervisorRole) getRole(descriptor.getSupervisorRoleId());
				hasSupervisorRole = true;
			}catch(Exception ex){
				hasSupervisorRole = false;
			}	

			if(!(hasFollowerRole || hasSupervisorRole))
				return false;
			if(hasFollowerRole && !hasSupervisorRole){
				followerOnly = true;
				supervisorOnly = false;
			}
			else if(!hasFollowerRole && hasSupervisorRole){
				followerOnly = false;
				supervisorOnly = true;
			}

			try{
				channel = new A3Channel(this, ui, descriptor);

				if(forApplication)
					channel.setConnectedForApplication(true);
				else
					channel.setConnectedForSystem(true);
				
				if(hasFollowerRole){
					followerRole.setChannel(channel);
					followerRole.setNode(this);
				}
				if(hasSupervisorRole){
					supervisorRole.setChannel(channel);
					supervisorRole.setNode(this);
				}

				synchronized(channels){
					channels.add(channel);
				}
				synchronized(channelsStatus){
					channelsStatus.put(groupName, CONNECTING);
					showOnScreen("channelsStatus = " + channelsStatus);
				}

				channel.connect(groupName, followerRole, supervisorRole, followerOnly, supervisorOnly);

				return connect(groupName, isResultRelevant, forApplication);

			}catch(Exception ex){
				return false;
			}

		}
		return false;
	}

	/**It disconnects this node from the specified group.
	 * This is possible only if the channel is used neither by the application, nor by the system.
	 * 
	 * @param groupName The name of the group to disconnect this node from.
	 * @param forApplication true if the disconnection is requested by the application,
	 * false if it is requested by the system.
	 */
	public synchronized void disconnect(String groupName, boolean forApplication){

		A3Channel channel;

		try{
			channel = getChannel(groupName);
			if(forApplication)
				channel.setConnectedForApplication(false);
			else
				channel.setConnectedForSystem(false);
			showOnScreen(groupName + " usage:\n application: " + channel.isConnectedForApplication() +
					"\n system: " + channel.isConnectedForSystem());
			if(!(channel.isConnectedForApplication() || channel.isConnectedForSystem())){
				channel.disconnect();
				channels.remove(channel);
				channelsStatus.remove(groupName);
				disconnectWaitChannel();
			}
		}
		catch(Exception e){}
	}

	private void disconnectWaitChannel() {
		// TODO Auto-generated method stub

		boolean noWaitChannels;

		synchronized(channelsStatus){
			noWaitChannels = !channelsStatus.containsValue(WAITING);
		}

		if(noWaitChannels)
			disconnect("wait", false);

		showOnScreen("channelsStatus = " + channelsStatus);
	}

	/**It passes a message to the user interface.
	 * @param message The message to pass to the user interface.
	 */
	@Override
	public void showOnScreen(String message) {
		// TODO Auto-generated method stub
		ui.showOnScreen(message);
	}

	/**It sends a message to the supervisor of the specified group.
	 * 
	 * @param message The message to be sent.
	 * @param groupName The name of the group whose supervisor to send the message to.
	 */
	public void sendToSupervisor(A3Message message, String groupName) {

		// TODO Auto-generated method stub
		A3Channel channel;

		try{

			channel = getChannel(groupName);
			channel.sendToSupervisor(message);

		}catch(Exception e){}
	}

	/**It sends a message to all the members of the specified group.
	 * 
	 * @param message The message to be sent.
	 * @param groupName The name of the group whose members to send the message to.
	 */
	public void sendBroadcast(A3Message message, String groupName){
		A3Channel channel;

		try{

			channel = getChannel(groupName);
			channel.sendBroadcast(message);

		}catch(Exception e){}
	}

	/**It sends a message to the specified node in the specified group.
	 * 
	 * @param message The message to be sent.
	 * @param groupName The name of the group the node "receiverAddress" belongs to.
	 * @param receiverAddress The node to which to send the message.
	 */
	public void sendUnicast(A3Message message, String groupName, String receiverAddress){
		A3Channel channel;

		try{

			channel = getChannel(groupName);
			channel.sendUnicast(message, receiverAddress);

		}catch(Exception e){}
	}

	/**It sends a message in the specified group, to the nodes which are interested in receiving it.
	 * 
	 * @param message The message to be sent.
	 * @param groupName The name of the group whose members to send the message to.
	 */
	public void sendMulticast(A3Message message, String groupName){
		A3Channel channel;

		try{

			channel = getChannel(groupName);
			channel.sendMulticast(message);

		}catch(Exception e){}
	}

	/**It communicates the Service of the group "groupName" that this node is interested in receiving
	 * messages of kind "reason".
	 * 
	 * @param reason The kind of the messages this node is interested in.
	 * @param groupName The name of the group whose Service to communicate the interest of this node.
	 */
	public void subscribe(int reason, String groupName) {
		// TODO Auto-generated method stub
		A3Channel channel;

		try{

			channel = getChannel(groupName);
			channel.subscribe(reason);

		}catch(Exception e){}
	}

	/**It communicates the Service of the group "groupName" that this node is no more interested in receiving
	 * messages of kind "reason".
	 * 
	 * @param reason The kind of the messages this node is no more interested in.
	 * @param groupName The name of the group whose Service to communicate the missing interest of this node.
	 */
	public void unsubscribe(int reason, String groupName) {
		// TODO Auto-generated method stub

		A3Channel channel;

		try{

			channel = getChannel(groupName);
			channel.unsubscribe(reason);

		}catch(Exception e){}
	}

	/**
	 * If this node has the proper roles,
	 * this method creates a hierarchical relationship between the specified groups.
	 * This happens by connecting this node to the parent group
	 * and by adding the latter to the hierarchy of the child group.
	 * 
	 * @param parentGroupName The name of the parent group.
	 * @param childGroupName The name of the son group.
	 * @return true, if "parentGroupName" became parent of "childGroupName", false otherwise.
	 */
	public boolean actualStack(String parentGroupName, String childGroupName) {
		// TODO Auto-generated method stub

		boolean result = connect(parentGroupName, true, false);
		A3Message message;

		if(result){
			message = new A3Message(Constants.ADD_TO_HIERARCHY, parentGroupName);
			sendBroadcast(message, childGroupName);
		}

		else
			disconnect(parentGroupName, false);

		return result;
	}

	/**
	 * It tries to create a hierarchical relationship between the specified groups.
	 * This is possible only if this node is the supervisor of at least one of the two groups
	 * and if it has the right roles to connect to the other.
	 * If this node is connected to the child group,
	 * the effects of this method are the ones of "actualStack(parentGroupName, childGroupName)".
	 * If this node is connected to the parent group,
	 * it connects to the child group, if it can,
	 * and sends it the order to execute "actualStack(parentGroupName, childGroupName)".
	 * The success or the failure of the stack operation
	 * is notified by method "stackReply(String, String, boolean)".
	 * 
	 * @param parentGroupName The name of the parent group.
	 * @param childGroupName The name of the son group.
	 */
	public void stack(String parentGroupName, String childGroupName) {
		// TODO Auto-generated method stub

		if(!(parentGroupName.equals("") || childGroupName.equals(""))){

			if(isSupervisor(childGroupName)){
				stackReply(parentGroupName, childGroupName, actualStack(parentGroupName, childGroupName));
			}

			else{
				if(isSupervisor(parentGroupName)){

					if(connect(childGroupName, true, false)){
						A3Message message = new A3Message(Constants.STACK_REQUEST, parentGroupName);
						sendToSupervisor(message, childGroupName);
					}
					else
						stackReply(parentGroupName, childGroupName, false);
				}

				else{
					stackReply(parentGroupName, childGroupName, false);
				}
			}
		}
		else
			stackReply(parentGroupName, childGroupName, false);

	}

	/**
	 * It tries to set the group "groupName1" as parent of group "groupName2" and vice versa.
	 * This is only possible if each of the two groups have the right roles to connect to the other,
	 * and if this node is the supervisor of at least one of the two groups.
	 * If this happens, this method send the other group a message asking for a peers operation.
	 * The supervisor of the other group executes "actualStack(String, String)"
	 * and notifies the result to this node.
	 * Here, the method "peersReply(String, String, boolean)" is called (see it for details).
	 * 
	 * @param groupName1 The name of a group involved in the peers operation.
	 * @param groupName2 The name of the other group involved in the peers operation.
	 */
	public void peers(String groupName1, String groupName2){
		String myGroupName;
		String otherGroupName;

		if(!(groupName1.equals("") || groupName2.equals(""))){

			if(isSupervisor(groupName1)){
				myGroupName = groupName1;
				otherGroupName = groupName2;
			}
			else{
				if(isSupervisor(groupName2)){
					myGroupName = groupName2;
					otherGroupName = groupName1;
				}
				else{
					peersReply(groupName1, groupName2, false);
					return;
				}
			}
			if(connect(otherGroupName, true, false)){
				A3Message message = new A3Message(Constants.PEERS_REQUEST, myGroupName);
				sendToSupervisor(message, otherGroupName);
			}
			else{
				disconnect(otherGroupName, false);
				peersReply(myGroupName, otherGroupName, false);
			}
		}
		else
			peersReply(groupName1, groupName2, false);
	}

	/**
	 * It tries to create a group containing the supervisors of the two other specified groups.
	 * This is only possible if each of the two groups have the right roles to connect to the parent group,
	 * if this node is the supervisor of at least one of the two groups
	 * and if this node has the right roles to connect to the other.
	 * This node, if it can connect to both the parent group and the other one,
	 * sends a message to the other one asking for a hierarchy operation.
	 * The other node executes the method "actualStack(String, String)" to connect to the parent group,
	 * and notifies this node with the result.
	 * Here, the method "hierarchyReply(String, String, String, boolean)" is called (see it for details).
	 * 
	 * @param parentName The name of the parent group.
	 * @param groupName1 The name of a son group involved in the hierarchy operation.
	 * @param groupName2 The name of the other son group involved in the hierarchy operation.
	 */
	public void hierarchy(String parentName, String groupName1, String groupName2){

		String myGroupName, otherGroupName;

		if(!(parentName.equals("") || groupName1.equals("") || groupName2.equals(""))){

			if(isSupervisor(groupName1)){
				myGroupName = groupName1;
				otherGroupName = groupName2;
			}
			else{
				if(isSupervisor(groupName2)){
					myGroupName = groupName2;
					otherGroupName = groupName1;
				}
				else{
					hierarchyReply(parentName, groupName1, groupName2, false);
					return;
				}
			}

			if(hasRolesForGroup(parentName) && connect(otherGroupName, true, false)){
				A3Message message = new A3Message(Constants.HIERARCHY_REQUEST,
						parentName + Constants.A3_SEPARATOR + myGroupName);
				sendToSupervisor(message, otherGroupName);
			}
			else{
				disconnect(otherGroupName, false);
				hierarchyReply(parentName, groupName1, groupName2, false);
			}
		}
		else
			hierarchyReply(parentName, groupName1, groupName2, false);
	}

	/**
	 * It disconnects this node from the group "oldGroupName" and connects it to the group "newGroupName",
	 * if it has the right roles.
	 * 
	 * @param newGroupName The name of the group to connect to.
	 * @param oldGroupName The name of the group to disconnect from.
	 */
	public void actualMerge(String newGroupName, String oldGroupName) {
		// TODO Auto-generated method stub
		disconnect(oldGroupName, true);
		connect(newGroupName, false, true);
	}

	/**
	 * It transfers the nodes in group "groupName2" to group "groupName1" and destroys group "groupName2".
	 * The nodes which don't have the right roles to connect to "groupName1"
	 * won't be there after this operation.
	 * 
	 * @param groupName1 The name of the group in which to transfer the nodes in group "groupName2".
	 * @param groupName2 The group which nodes are transfered in "groupName1". It is destroyed.
	 */
	public void merge(String groupName1, String groupName2) {
		// TODO Auto-generated method stub

		boolean ok = false;

		if(!(groupName1.equals("") || groupName2.equals(""))){

			if(isSupervisor(groupName1)){

				if(connect(groupName2, true, false)){
					A3Message message = new A3Message(Constants.MERGE, groupName1);
					sendToSupervisor(message, groupName2);
					ok = true;
					disconnect(groupName2, false);
				}
				else
					disconnect(groupName2, false);
			}
			else{
				if(isSupervisor(groupName2)){
					A3Message message = new A3Message(Constants.MERGE, groupName1);
					sendBroadcast(message, groupName2);
					ok = true;
					/* I don't need to execute "disconnect(groupName2, false);" here,
					 * because I will disconnect from group "groupName2"
					 * when I will receive message "MERGE groupName2".
					 */
				}
			}
		}

		mergeReply(groupName1, groupName2, ok);
	}

	/**
	 * It destroys the hierarchical relationship between the two specified groups,
	 * by telling all the nodes of the child group to remove the parent group from their hierarchies
	 * and by disconnecting the channel to the parent group if it isn't connected for other reasons.
	 * 
	 * @param parentGroupName The name of the group to disconnect from.
	 * @param childGroupName The name of the group to disconnect from group "parentGroupName".
	 */
	public void actualReverseStack(String parentGroupName, String childGroupName) {
		// TODO Auto-generated method stub
		A3Message message = new A3Message(Constants.REMOVE_FROM_HIERARCHY, parentGroupName);
		sendBroadcast(message, childGroupName);
		disconnect(parentGroupName, false);
	}

	/**
	 * It tries to destroy the hierarchical relationship between the two specified groups.
	 * This is only possible if this node is the supervisor of at least one of the two groups.
	 * If this node is the supervisor of the group "childGroupName",
	 * the effects of this method are the ones of method "actualReverseStack(parentGroupName, childGroupName)".
	 * If this node is the supervisor of the group "parentGroupName",
	 * and if it has the right roles to connect to the group "childGroupName",
	 * this node send the latter the order to execute "actualReverseStack(parentGroupName, childGroupName)".
	 * Such operation is always possible, so notifying its result is unuseful.
	 * 
	 * @param parentGroupName The name of the group to disconnect from.
	 * @param childGroupName The name of the group to disconnect from group "parentGroupName".
	 */
	public void reverseStack(String parentGroupName, String childGroupName){
		boolean ok = false;
		if(!(parentGroupName.equals("") || childGroupName.equals(""))){

			if(isSupervisor(parentGroupName)){
				try {
					ok = connect(childGroupName, true, false);
					if(ok){
						A3Message message = new A3Message(Constants.REVERSE_STACK, parentGroupName);
						sendToSupervisor(message, childGroupName);
						disconnect(childGroupName, false);
					}
					else
						disconnect(childGroupName, false);
				} catch (Exception e) {}
			}

			else{

				if(isSupervisor(childGroupName)){
					actualReverseStack(parentGroupName, childGroupName);
					ok = true;
				}
			}
		}
		reverseStackReply(parentGroupName, childGroupName, ok);
	}

	/**
	 * It tries to destroy the peers hierarchical relationship between groups "groupName1" and "groupName2".
	 * This is possible only if this node is the supervisor of at least one of the specified groups.
	 * It sends the other group the order to execute "actualReverseStack(String, String)"
	 * and calls "actualReverseStack(String, String)" on this node too.
	 * 
	 * @param groupName1 The name of a group involved in the reverse peers operation.
	 * @param groupName2 The name of the other group involved in the reverse peers operation.
	 */
	public void reversePeers(String groupName1, String groupName2){

		String myGroupName, otherGroupName;
		boolean ok = false;

		if(!(groupName1.equals("") || groupName2.equals(""))){

			if(isSupervisor(groupName1)){
				myGroupName = groupName1;
				otherGroupName = groupName2;
			}
			else{
				if(isSupervisor(groupName2)){
					myGroupName = groupName2;
					otherGroupName = groupName1;
				}
				else{
					reversePeersReply(groupName1, groupName2, ok);
					return;
				}
			}

			ok = connect(otherGroupName, true, false);
			if(ok){
				A3Message message = new A3Message(Constants.REVERSE_STACK, myGroupName);
				sendToSupervisor(message, otherGroupName);
				actualReverseStack(otherGroupName, myGroupName);
			}
			else
				disconnect(otherGroupName, false);
		}
		reversePeersReply(groupName1, groupName2, ok);
	}

	/**
	 * It tries to destroy the hierarchy hierarchical relationship
	 * between the group "parentGroupName" and the groups "groupName1" and "groupName2".
	 * This is possible only if this node is the supervisor of at least one of the specified son groups
	 * and if it has the right roles to connect to the other one.
	 * It sends the other group the order to execute "actualReverseStack(String, String)"
	 * and calls "actualReverseStack(String, String)" on this node too.
	 * 
	 * @param parentGroupName The name of the parent group involved in the reverse hierarchy operation.
	 * @param groupName1 The name of a son group involved in the reverse hierarchy operation.
	 * @param groupName2 The name of the other son group involved in the reverse hierarchy operation.
	 */
	public void reverseHierarchy(String parentGroupName, String groupName1, String groupName2){

		String myGroupName, otherGroupName;
		boolean ok = false;

		if(!(parentGroupName.equals("") || groupName1.equals("") || groupName2.equals(""))){

			if(isSupervisor(groupName1)){
				myGroupName = groupName1;
				otherGroupName = groupName2;
			}
			else{
				if(isSupervisor(groupName2)){
					myGroupName = groupName2;
					otherGroupName = groupName1;
				}
				else{
					reverseHierarchyReply(parentGroupName, groupName1, groupName2, ok);
					return;
				}
			}

			ok = connect(otherGroupName, true, false);
			if(ok){
				A3Message message = new A3Message(Constants.REVERSE_STACK, parentGroupName);
				sendToSupervisor(message, otherGroupName);
				actualReverseStack(parentGroupName, myGroupName);
				disconnect(otherGroupName, false);
			}
			else
				disconnect(otherGroupName, false);
		}
		reverseHierarchyReply(parentGroupName, groupName1, groupName2, ok);
	}

	/**
	 * It notifies this node with the result of a stack operation.
	 * 
	 * @param parentGroupName The name of the parent group.
	 * @param childGroupName The name of the child group.
	 * @param ok true if the stack operation was successful, false otherwise.
	 */
	public void stackReply(String parentGroupName, String childGroupName, boolean ok) {
		// TODO Auto-generated method stub
		showOnScreen("stack(" + parentGroupName + ", " + childGroupName + "): " + ok);
		disconnect(childGroupName, false);
	}

	/**
	 * It notifies this node with the result of a peers operation.
	 * If this node managed to send the other group a message
	 * and "actualStack(String, String)" on the other node didn't fail,
	 * "actualStack(String, String)" is called on this node.
	 * Its result is notified.
	 * "actualStack(String, String)" on the other node is reversed if it failed on this node.
	 * 
	 * @param parentGroupName The name of the parent group.
	 * @param childGroupName The name of the child group.
	 * @param ok true if the stack operation was successful, false otherwise.
	 */
	public void peersReply(String parentGroupName, String childGroupName, boolean ok) {
		// TODO Auto-generated method stub

		if(ok){

			//I can execute this only if I have became supervisor during an ongoing peers negotiation.
			if(actualStack(parentGroupName, childGroupName)){
				showOnScreen("peers(" + childGroupName + ", " + parentGroupName + "): " + true);
				return;
			}

			else{
				/*The other node executed a stack operation to connect to me,
				 * so I don't need to specify to undo a peers operation.
				 */
				A3Message message = new A3Message(Constants.REVERSE_STACK, childGroupName);
				sendToSupervisor(message, parentGroupName);
				showOnScreen("peers(" + childGroupName + ", " + parentGroupName + "): " + false);
				disconnect(parentGroupName, false);
			}
		}
		else{
			showOnScreen("peers(" + childGroupName + ", " + parentGroupName + "): " + false);
			disconnect(parentGroupName, false);
		}
	}

	/**
	 * It notifies this node with the result of a hierarchy operation.
	 * If this node managed to send the other group a message
	 * and "actualStack(String, String)" on the other node didn't fail,
	 * "actualStack(String, String)" is called on this node.
	 * Its result is notified.
	 * "actualStack(String, String)" on the other node is reversed if it failed on this node.
	 * 
	 * @param parentGroupName The name of the parent group.
	 * @param myGroupName The name of a son group involved in the hierarchy operation.
	 * @param otherGroupName The name of the other son group involved in the hierarchy operation.
	 * @param ok true if the hierarchy operation was successful, false otherwise.
	 */
	public void hierarchyReply(String parentGroupName, String myGroupName, String otherGroupName, boolean ok) {
		// TODO Auto-generated method stub

		if(ok){

			//I can execute this only if I have became supervisor during an ongoing peers negotiation.
			if(actualStack(parentGroupName, myGroupName))
				showOnScreen("hierarchy(" + parentGroupName + ", " + myGroupName + ", " + otherGroupName + "): " + true);

			else{
				/*The other node executed a stack operation to connect to parent,
				 * so I don't need to specify to undo a hierarchy operation.
				 */
				A3Message message = new A3Message(Constants.REVERSE_STACK, parentGroupName);
				sendToSupervisor(message, otherGroupName);
				showOnScreen("hierarchy(" + parentGroupName + ", " + myGroupName + ", " + otherGroupName + "): " + false);
			}
		}
		else
			showOnScreen("hierarchy(" + parentGroupName + ", " + myGroupName + ", " + otherGroupName + "): " + false);

		disconnect(otherGroupName, false);
	}

	/**
	 * It notifies this node with the result of a merge operation.
	 * 
	 * @param groupName1 The name of the group in which nodes in group "groupName2" were transfered.
	 * @param groupName2 The name of the destroyed group.
	 * @param ok true if the merge operation was successful, false otherwise.
	 */
	private void mergeReply(String groupName1, String groupName2, boolean ok) {
		// TODO Auto-generated method stub
		showOnScreen("merge(" + groupName1 + ", " + groupName2 + "): " + ok);
	}

	/**
	 * It notifies this node with the result of a reverse stack operation.
	 * 
	 * @param parentGroupName The name of the parent group.
	 * @param childGroupName The name of the child group.
	 * @param ok true if the reverse stack operation was successful, false otherwise.
	 */
	private void reverseStackReply(String parentGroupName, String childGroupName, boolean ok) {
		// TODO Auto-generated method stub
		showOnScreen("reverseStack(" + parentGroupName + ", " + childGroupName + "): " + ok);
	}

	/**
	 * It notifies this node with the result of a reverse peers operation.
	 * 
	 * @param groupName1 The first group involved in the operation.
	 * @param groupName2 The second group involved in the operation.
	 * @param ok true if the reverse peers operation was successful, false otherwise.
	 */
	private void reversePeersReply(String groupName1, String groupName2, boolean ok) {
		// TODO Auto-generated method stub
		showOnScreen("reversePeers(" + groupName1 + ", " + groupName2 + "): " + ok);
	}

	/**
	 * It notifies this node with the result of a reverse hierarchy operation.
	 * 
	 * @param parentGroupName The parent group involved in the operation.
	 * @param groupName1 The first son group involved in the operation.
	 * @param groupName2 The second son group involved in the operation.
	 * @param ok true if the reverse hierarchy operation was successful, false otherwise.
	 */
	private void reverseHierarchyReply(String parentGroupName, String groupName1, String groupName2, boolean ok) {
		// TODO Auto-generated method stub
		showOnScreen("reverseHierarchy(" + parentGroupName + ", " + groupName1 + ", " + groupName2 + "): " + ok);
	}

	/**It sets to "CONNECTED" the status of the specified channel.
	 * If no more channels are in "WAITING" status, the channel to the "wait" group is destroyed.
	 * 
	 * @param a3Channel The channel whose status is set to "CONNECTED".
	 */
	public void setConnected(A3Channel a3Channel) {
		// TODO Auto-generated method stub
		boolean noWaitChannels;

		synchronized(channelsStatus){

			channelsStatus.put(a3Channel.getGroupName(), CONNECTED);
			showOnScreen("channelsStatus = " + channelsStatus);
			noWaitChannels = !channelsStatus.containsValue(WAITING);

			channelsStatus.notifyAll();

		}

		if(noWaitChannels)
			disconnect("wait", false);
	}

	/**It sets to "WAITING" the status of the specified channel.
	 * If it doesn't exist, it creates the channel to the "wait" group.
	 * 
	 * @param a3Channel The channel whose status is set to "WAITING".
	 */
	public void setWaiting(A3Channel a3Channel) {
		// TODO Auto-generated method stub

		synchronized(channelsStatus){

			channelsStatus.put(a3Channel.getGroupName(), WAITING);
			showOnScreen("channelsStatus = " + channelsStatus);

			channelsStatus.notifyAll();

		}
		connect("wait", false, false);
	}

	/**If this node is the supervisor of the group "groupName",
	 * this method splits a new group from group "groupName"
	 * and transfers there the specified number of nodes previously in "groupName".
	 * Such nodes are selected randomly, but the supervisor of group "groupName" can't be transfered.
	 * The new group has name "groupName_n", with integer n.
	 * 
	 * @param groupName The name of the group whose nodes must be transfered in the new group.
	 * @param nodesToTransfer The number of nodes to transfer from group "groupName" to the new group.
	 */
	public void split(String groupName, int nodesToTransfer){

		if(isSupervisor(groupName)){

			A3Channel channel;

			try{

				channel = getChannel(groupName);
				channel.split(nodesToTransfer);

			}catch(Exception e){}
		}
		else
			showOnScreen("Split failed: I'm not the supervisor.");
	}

	/**If this node is the supervisor of the group "groupName",
	 * this method splits a new group from group "groupName"
	 * and transfers there the specified number of nodes previously in "groupName".
	 * Such nodes are selected basing on the value of an integer fitness function,
	 * but the supervisor of group "groupName" can't be transfered.
	 * The new group has name "groupName_n", with integer n.
	 * 
	 * @param groupName The name of the group whose nodes must be transfered in the new group.
	 * @param nodesToTransfer The number of nodes to transfer from group "groupName" to the new group.
	 */
	public void splitWithIntegerFitnessFunction(String groupName, int nodesToTransfer){

		if(isSupervisor(groupName)){

			A3Channel channel;

			try{

				channel = getChannel(groupName);
				channel.splitWithIntegerFitnessFunction(nodesToTransfer);

			}catch(Exception e){}
		}
		else
			showOnScreen("Split failed: I'm not the supervisor.");
	}

	/**If this node is the supervisor of the group "groupName",
	 * this method splits a new group from group "groupName"
	 * and transfers there the nodes whose boolean fitness function has value true.
	 * The supervisor of group "groupName" can't be transfered.
	 * The new group has name "groupName_n", with integer n.
	 * 
	 * @param groupName The name of the group whose nodes must be transfered in the new group.
	 * @param nodesToTransfer The number of nodes to transfer from group "groupName" to the new group.
	 */
	public void splitWithBooleanFitnessFunction(String groupName){

		if(isSupervisor(groupName)){

			A3Channel channel;

			try{

				channel = getChannel(groupName);
				channel.splitWithBooleanFitnessFunction();

			}catch(Exception e){}
		}
		else
			showOnScreen("Split failed: I'm not the supervisor.");
	}

	/**Called by the user interface to determine if the channel "groupName" is used by the application or not.
	 * 
	 * @param groupName The name of the target channel.
	 * @return true, if the channel "groupName" is used by the application, false otherwise.
	 */
	public boolean isConnectedForApplication(String groupName) {
		// TODO Auto-generated method stub
		A3Channel channel;

		try {
			channel = getChannel(groupName);
			return channel.isConnectedForApplication();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			return false;
		}

	}

	/**
	 * @param groupName The name of the group of the channel whose supervisor fitness function value is needed.
	 * @return The value of the supervisor fitness function value of the channel "groupName".
	 * @throws Exception The channel "groupName" cannot become supervisor of its group.
	 */
	public int getSupervisorFitnessFunction(String groupName) throws Exception {
		// TODO Auto-generated method stub
		A3Channel channel = getChannel(groupName);
		return channel.getSupervisorFitnessFunction();
	}
	
	/**
	 * It starts a supervisor election in the group "groupName".
	 * @param groupName The name of the group in which to start the supervisor election.
	 */
	public void startSupervisorElection(String groupName){
		A3Channel channel;
		try {
			channel = getChannel(groupName);
			channel.startSupervisorElection();
		} catch (Exception e) {}
	}
}