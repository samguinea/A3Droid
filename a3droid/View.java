package a3.a3droid;

import java.util.ArrayList;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;

/**This class resides on a Service.
 * It manages the list of the channels currently in the group (called "view")
 * and all the messages and the callbacks to update it runtime.
 * @author Francesco
 *
 */
public class View extends HandlerThread implements TimerInterface{

	/**The Service on which this View resides.*/
	private Service service;

	/**The list of the channel that are currently part of the group.*/
	private ArrayList<String> groupMembers;

	/**The list of the group members that will substitute "groupMembers" after a view update.*/
	private ArrayList<String> temporaryView;

	/**Indicates if a view update is ongoing or not.*/
	private boolean temporaryViewIsActive;

	/**The number of the channels in the group.*/
	private int numberOfNodes;

	private Handler mHandler;

	/**The Service on which this View resides.
	 * @param service
	 */
	public View(Service service) {
		super("View_" + service.getGroupName());
		this.service = service;
		groupMembers = new ArrayList<String>();
		temporaryViewIsActive = false;
		numberOfNodes = 0;
		start();
	}

	public Message obtainMessage() {
		// TODO Auto-generated method stub
		return mHandler.obtainMessage();
	}

	public void sendMessage(Message msg) {
		// TODO Auto-generated method stub
		mHandler.sendMessage(msg);
	}

	@Override
	protected void onLooperPrepared() {
		super.onLooperPrepared();

		mHandler = new Handler(getLooper()) {

			/**This method is used to manage callbacks.
			 * This is done in another thread in order not to block the bus.
			 */
			public void handleMessage(Message msg) {

				String group = (String) msg.obj;

				switch(msg.arg2){
				case Constants.SESSION_LOST:
					break;

				case Constants.MEMBER_ADDED:
					addGroupMember(group);
					break;

				case Constants.MEMBER_REMOVED:
					removeGroupMember(group);
					break;

				default: break;
				}
			}
		};
	}

	/**
	 * It adds the channel "memberName" to the group members' list, because it joined the group.
	 * If a view update is ongoing, the channel's address is added to the temporary view too.
	 * If the channel is the first channel joining the group, it is the new supervisor
	 * and it is notified about it.
	 * @param memberName The address of the channel that joined the group.
	 */
	private void addGroupMember(String memberName) {
		// TODO Auto-generated method stub

		synchronized(groupMembers){
			groupMembers.add(memberName);
		}

		if(temporaryViewIsActive){
			synchronized(temporaryView){
				temporaryView.add(memberName);
			}
		}
		numberOfNodes = numberOfNodes + 1;
		service.showOnScreen("View: " + getView());
	}

	/**
	 * It removes the channel "memberName" from the list of the group members, because it left the group.
	 * It triggers a supervisor election if "memberName" was the supervisor of the group,
	 * or the group destruction if no nodes are present in the group anymore.
	 * If a view update is ongoing, the channel is removed from the temporary view too.
	 * @param memberName The address of the channel which left the group.
	 */
	public synchronized void removeGroupMember(String memberName) {
		// TODO Auto-generated method stub

		synchronized(groupMembers){
			groupMembers.remove(memberName);
		}

		if(temporaryViewIsActive){
			synchronized(temporaryView){
				temporaryView.remove(memberName);
			}
		}
		numberOfNodes = numberOfNodes - 1;

		service.showOnScreen("View: " + getView());

		// If the old supervisor left, then I must elect a new one.
		if(service.getSupervisorId().equals(memberName)){
			service.setSupervisorId("?");
			service.supervisorElection();
		}
	}

	/**
	 * @return The string representation of the list of the group members, in the form "[member1, member2, ...]".
	 */
	public synchronized String getView() {
		// TODO Auto-generated method stub
		return groupMembers.toString();
	}

	/**
	 * It determines if the specified channel is currently in the list of the group members or not.
	 * If a view update is ongoing, the check is performed on the temporary view too.
	 * @param address The address of the channel whose presence in the group has to be checked.
	 * @return true if the address of the channel "address" is currently in the (temporary) view,
	 * false otherwise.
	 */
	public synchronized boolean isInView(String address){

		if(groupMembers.contains(address))
			return true;
		if(temporaryViewIsActive && temporaryView.contains(address))
			return true;
		return false;
	}

	@Override
	public void timerFired(int reason) {
		// TODO Auto-generated method stub
		temporaryViewIsActive = false;
		groupMembers = temporaryView;
	}

	public int getNumberOfNodes() {
		return numberOfNodes;
	}
}
