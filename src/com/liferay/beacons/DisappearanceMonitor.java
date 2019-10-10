package com.liferay.beacons;

import java.util.TimerTask;
import java.util.Iterator;
import java.util.ConcurrentModificationException;
import java.lang.*;
import org.appcelerator.kroll.common.Log;

public class DisappearanceMonitor extends TimerTask {
  private LiferayBeaconsModule owner;
  private static final String TAG = "LiferayBeaconsModule";
  private long expiryTime = 8000;
  public DisappearanceMonitor(LiferayBeaconsModule owner) {
    this.owner = owner;
  }

  public void setExpiryTime(long ms){
    expiryTime = ms;
  }
  @Override
  public void run(){
    for (Iterator<BeaconEvent> beacon_events = owner.getVisibleBeacons().values().iterator(); beacon_events.hasNext();) {
      BeaconEvent beacon_event = beacon_events.next();
      if(System.currentTimeMillis() - beacon_event.getEventTime().getTime() > expiryTime) {
        Log.i(TAG, "[Disappearence Monitor] remove Beacon: "+beacon_event.toString());
        try {
          beacon_events.remove();
        } catch (ConcurrentModificationException e) {
          Log.i(TAG, "[Disappearence Monitor] Exception");
        }
      }
    }
  }
}
