package a3.a3droid;

import org.alljoyn.bus.BusAttachment;
import org.alljoyn.bus.BusListener;
import org.alljoyn.bus.BusObject;
import org.alljoyn.bus.Mutable;
import org.alljoyn.bus.SessionListener;
import org.alljoyn.bus.SessionOpts;
import org.alljoyn.bus.SessionPortListener;
import org.alljoyn.bus.Status;
import org.alljoyn.bus.annotation.BusMethod;

/**This class is designed to stay on an A3Channel, in order to receive unicast messages from the supervisor.
 * In order to connect to a specific client in a multipoint session,
 * AllJoyn requires that every client is uniquely identified by a well-known-name:
 * such name is obtained by adding an hash of the channel address to the name of the group.
 * The well-known-name must be published on the bus, so this class is actually an AllJoyn service.
 * @author Francesco
 *
 */
public class A3UnicastReceiver extends Thread implements A3UnicastInterface, BusObject {

	/**The name published on the bus, through which the channel is addressable in unicast mode.*/
	private String groupName;
	
	/**The channel this receiver belongs to.*/
	private A3Channel channel;
	
	/**The connection to the AllJoyn bus.*/
	private BusAttachment mBus;
	
	/**
	 * @param name The name published on the bus, through which the channel is addressable in unicast mode.
	 * @param a3channel The channel this receiver belongs to.
	 */
	public A3UnicastReceiver(String name, A3Channel a3channel){
		groupName = name;
		channel = a3channel;
		start();
	}
	
	@Override
	@BusMethod
	public void receiveUnicast(A3Message message) {
		// TODO Auto-generated method stub

		channel.ReceiveBroadcast(message);
	}

	/** It is used in order to publish the name on the AllJoyn bus.*/
	public void connect(){
	
		mBus = new BusAttachment(getClass().getPackage().getName(), BusAttachment.RemoteMessage.Receive);
		mBus.registerBusListener(new BusListener());
	
		Status status = mBus.registerBusObject(this, "/SimpleService");
		if (status != Status.OK) {
			return;
		}
	
		status = mBus.connect();
		if (status != Status.OK) {
			return;
		}
	
		Mutable.ShortValue contactPort = new Mutable.ShortValue(Constants.CONTACT_PORT);
		SessionOpts sessionOpts = new SessionOpts();
		sessionOpts.traffic = SessionOpts.TRAFFIC_MESSAGES;
		sessionOpts.isMultipoint = false;
		sessionOpts.proximity = SessionOpts.PROXIMITY_ANY;
	
		sessionOpts.transports = SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD;
	
		status = mBus.bindSessionPort(contactPort, sessionOpts, new SessionPortListener() {
	
			@Override
			public boolean acceptSessionJoiner(short sessionPort, String joiner, SessionOpts sessionOpts) {
				if (sessionPort == Constants.CONTACT_PORT) {
					return true;
				} else {
					return false;
				}
			}
	
			public void sessionJoined(short sessionPort, int id, String joiner) {
	
				mBus.setSessionListener(id, new SessionListener() {
	
					@Override
					public void sessionLost(int sessionId, int reason) {}
				});
			}
		});
	
		if (status != Status.OK)
			return;
	
		int flag = BusAttachment.ALLJOYN_REQUESTNAME_FLAG_REPLACE_EXISTING | BusAttachment.ALLJOYN_REQUESTNAME_FLAG_DO_NOT_QUEUE;
	
		status = mBus.requestName(groupName, flag);
		if (status == Status.OK) {
			status = mBus.advertiseName(groupName, sessionOpts.transports);
			if (status != Status.OK) {
				status = mBus.releaseName(groupName);
				return;
			}
		}
	}

	/**It is used in order to unpublish the name on the bus.*/
	public void disconnect(){
	
		try{	
			mBus.cancelAdvertiseName(groupName, (short)(SessionOpts.TRANSPORT_ANY + SessionOpts.TRANSPORT_WFD));	
		} catch (Exception e){}
	
		try{
			mBus.unbindSessionPort(Constants.CONTACT_PORT);
		} catch (Exception e){}
	
		try{
			mBus.releaseName(groupName);
		} catch (Exception e){}
	
		try{
			mBus.unregisterBusObject(this);
		} catch (Exception e){}
	
		try{
			mBus.disconnect();
		}
		catch (Exception e){}
	}

}
