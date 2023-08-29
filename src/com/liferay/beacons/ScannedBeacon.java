package com.liferay.beacons;

import org.altbeacon.beacon.*;
import java.util.Date;
import java.util.ArrayList;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.common.Log;
import java.lang.Comparable;

public class ScannedBeacon  implements Comparable<ScannedBeacon> {
  private static final String TAG = "LiferayBeaconsModule";
  private String beacon_id;
  private ArrayList<Double> rssis;
  private String uuid="";
  private String major="";
  private String minor="";
  private int scanCount;

  public ScannedBeacon(Beacon beacon) {
    Log.i(TAG, "[ScannedBeacon] constructor: "+beacon.toString());
    beacon_id = beacon.getId1()+"-"+beacon.getId2()+"-"+beacon.getId3();//beacon.toString();
    uuid = beacon.getId1().toString();
    major = beacon.getId2().toString();
    minor = beacon.getId3().toString();
    scanCount = 0;
    rssis = new ArrayList<Double>();
    //distance = beacon.getDistance();
    //rssi = beacon.getRssi();
  }

  public void setNewBeaconDetection (Beacon beacon) {
    scanCount++;
    rssis.add(Double.valueOf(beacon.getRssi()));
    Log.i(TAG, "[ScannedBeacon] setNewBeaconDetection ScanCount: "+scanCount);
    Log.i(TAG, "[ScannedBeacon] setNewBeaconDetection Average Rssi: "+ String.valueOf(this.getAverageRssi()));
  }

  public double getAverageRssi() {
    double aRssi = 0.0d;
    for (int i = 0; i < rssis.size(); i++) {
      aRssi += rssis.get(i);
    }
    return aRssi/scanCount;
  }

  public KrollDict getBeaconData () {
    KrollDict retVal = new KrollDict();
		retVal.put("uuid", uuid);
		retVal.put("major", major);
		retVal.put("minor", minor);
		retVal.put("rssi", getAverageRssi());
    retVal.put("beacon_id", beacon_id);
    return retVal;
  }

  @Override
  public int compareTo(ScannedBeacon sb){
    return Double.compare(this.getAverageRssi(), sb.getAverageRssi());
  }

  public String toString() {
    return beacon_id;
  }

  public String getBeaconID() {
    return beacon_id;
  }

}
