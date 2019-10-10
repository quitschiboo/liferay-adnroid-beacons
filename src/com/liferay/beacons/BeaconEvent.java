package com.liferay.beacons;

import org.altbeacon.beacon.*;
import java.util.Date;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;

public class BeaconEvent {
  private static final String TAG = "LiferayBeaconsModule";

  private Date event_time;
  private String uuid="";
  private String major="";
  private String minor="";
  private Double distance;
  private int rssi;

  public BeaconEvent(Beacon beacon) {
    Log.i(TAG, "[BeaconEvent] constructor: "+beacon.toString());
    event_time = new Date();
    uuid = beacon.getId1().toString();
    major = beacon.getId2().toString();
    minor = beacon.getId3().toString();
    distance = beacon.getDistance();
    rssi = beacon.getRssi();
  }

  public Double getDistance(){
      return distance;
  }

  public int getRssi() {
    return rssi;
  }

  public Date getEventTime() {
    return event_time;
  }

  public KrollDict getBeaconData () {
    KrollDict retVal = new KrollDict();
		retVal.put("uuid", uuid);
		retVal.put("major", major);
		retVal.put("minor", minor);
		retVal.put("distance", distance);
		retVal.put("rssi", rssi);
    return retVal;
  }

  public String toString () {
    return uuid+"-"+major+"-"+minor;
  }

}
