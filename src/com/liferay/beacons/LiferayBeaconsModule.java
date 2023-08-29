/**
 * Copyright 2015 Liferay, Inc. All rights reserved.
 * http://www.liferay.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * @author James Falkner
 */

package com.liferay.beacons;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.RemoteException;
//import com.radiusnetworks.ibeacon.*;
import org.altbeacon.beacon.*;
import org.altbeacon.beacon.powersave.*;
//import org.altbeacon.beacon.logging
import org.altbeacon.beacon.Identifier;
import org.altbeacon.beacon.service.RunningAverageRssiFilter;
import org.altbeacon.beacon.service.ArmaRssiFilter;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
//import android.util.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.util.TiConvert;

import java.util.Iterator;

import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
//import java.util.Hashtable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Events: enteredRegion, exitedRegion, determinedRegionState, beaconProximity
 */
@Kroll.module(name="LiferayBeacons", id="com.liferay.beacons")
public class LiferayBeaconsModule extends KrollModule implements BeaconConsumer
{

	//private static BeaconManager BeaconManager;
	private BeaconManager beaconManager;// = BeaconManager.getInstanceForApplication(this);
	private static BackgroundPowerSaver BackgroundPowerSaver;
	//private static LogManager LogM;

	// Standard Debugging variables
	private static final String TAG = "LiferayBeaconsModule";

	private boolean autoRange = true;
	private boolean ready = false;

	//Bei welchem Rssi wird der Beacon an Titanium gesandt
	private int triggerRssi = -54;

	//Der naechste gerangte Beacon;
	//private Beacon closestBeacon = null;

	private double nearest_beacon_rssi;
	private KrollDict nearest_beacon_now;

	//For SingleScans
 	//private Hashtable<String, Region> rangable_regions;
	private HashMap<String, Region> rangable_regions;

	private long minSingleScanLength;
	private long maxSingleScanLength;
	private long singleScanStartTime;

	private boolean debug = false;

	/*
	private Hashtable<String, ScannedBeacon> visible_beacons;

	public Hashtable<String, ScannedBeacon> getVisibleBeacons(){
		return visible_beacons;
	}*/

	private HashMap<String, ScannedBeacon> visible_beacons;

	public HashMap<String, ScannedBeacon> getVisibleBeacons(){
		return visible_beacons;
	}

	public LiferayBeaconsModule() {
		super();
		Log.i(TAG, "[MODULE LIFECYCLE EVENT] constructor");
		//visible_beacons = new Hashtable<String, ScannedBeacon>();
		//rangable_regions = new Hashtable<String, Region>();

		visible_beacons = new HashMap<String, ScannedBeacon>();
		rangable_regions = new HashMap<String, Region>();
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		Log.i(TAG, "onAppCreate: Liferay Android Beacons 1.1");
		//BackgroundPowerSaver = new BackgroundPowerSaver(app);
	}

	/**
	 */
	@Kroll.method
	public void setDebug(boolean flag) {
		debug = flag;
		BeaconManager.setDebug(flag);
	}

	/**
	 * See if Bluetooth 4.0 & LE is available on device
	 *
	 * @return true if iBeacons can be used, false otherwise
	 */
	@Kroll.method
	public boolean checkAvailability() {
		try {
			return beaconManager.checkAvailability();
		} catch (Exception ex) {
			return false;
		}
	}

	/**
	 * Get Instance of BLE
	 *
	 * @return true if iBeacons can be used, false otherwise
	 */
	@Kroll.method
	public void instantiateManager() {
		beaconManager = BeaconManager.getInstanceForApplication(TiApplication.getAppCurrentActivity());
		beaconManager.setForegroundScanPeriod(1000);
		beaconManager.setForegroundBetweenScanPeriod(0);
		//beaconManager.setBackgroundScanPeriod(10000);
		//beaconManager.setBackgroundBetweenScanPeriod(60 * 1000);
		//beaconManager.setMaxTrackingAge(8400);
		beaconManager.bind(this);
		debug = false;
		BeaconManager.setDebug(false);
	}

	/**
	 * change RssiFilter
	 * filter can be: runningAverage or arma
	 */
	@Kroll.method
 	public void setRssiFilter(String filter, @Kroll.argument(optional=true) long ms) {
		long defMs = 5000;
		if(ms > 0 ){
			defMs = ms;//TiConvert.toDouble(ms);
		}
 		setRssiFilterClass(filter, ms);
 	}

	private void setRssiFilterClass(String filter, long ms) {

		switch(filter) {
			case "runningAverage":
				BeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
				RunningAverageRssiFilter.setSampleExpirationMilliseconds(ms);
				break;
			case "arma":
					BeaconManager.setRssiFilterImplClass(ArmaRssiFilter.class);
					break;
			default:
				BeaconManager.setRssiFilterImplClass(RunningAverageRssiFilter.class);
				RunningAverageRssiFilter.setSampleExpirationMilliseconds(ms);
				break;
		}
	}

	@Kroll.method
	public void clearRangableRegions()
	{
		rangable_regions.clear();
	}

	@Kroll.method
	public void setupRangableRegion(Object region)
	{
		if (!checkAvailability()) {
			Log.i(TAG, "Bluetooth LE not available or no permissions on this device");
			return;
		}

			HashMap<String,Object> dict = (HashMap<String,Object>)region;
			String identifier = TiConvert.toString(dict, "identifier");
			Identifier uuid = (dict.get("uuid") != null) ? Identifier.fromUuid(UUID.fromString(TiConvert.toString(dict, "uuid"))) : null;
			//Identifier uuid = null;
			//Integer major = (dict.get("major") != null) ? TiConvert.toInt(dict, "major") : null;
			Identifier major = (dict.get("major") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "major")) : null;
			Identifier minor = (dict.get("minor") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "minor")) : null;

			Region r = new Region(identifier, uuid, major, minor);
			rangable_regions.put(identifier, r);
	}

	/**
	 * @param regions Array of Regions to Scan for
	 * @param minScanLength minimal length of Scan before a detected Beacon is sent to titanium
	 * @param maxScanLength Maximal Length of Scan, if nothing found, null ist sent
	 * @param foregroundScanPeriod
	 */
	@Kroll.method
	public void scanForClosestBeacon(long minScanLength, long maxScanLength, int foregroundScanPeriod, int foregroundBetweenScanPeriod, int tRssi)
	{
		if (!beaconManager.isBound(this)) {
			beaconManager.bind(this);
		}
		minSingleScanLength = minScanLength;
		maxSingleScanLength = maxScanLength;
		singleScanStartTime = System.currentTimeMillis();
		triggerRssi = tRssi;

		visible_beacons.clear();

		nearest_beacon_rssi = 0.0;
		nearest_beacon_now = null;

		beaconManager.setForegroundScanPeriod(foregroundScanPeriod);
		beaconManager.setForegroundBetweenScanPeriod(foregroundBetweenScanPeriod);

		try{
			beaconManager.updateScanPeriods();
		} catch(RemoteException re) {
			re.printStackTrace();
		}

		Collection<Region> alreadyRangedReagons = beaconManager.getRangedRegions();
		for(String key : rangable_regions.keySet()) {
			if(alreadyRangedReagons.contains(rangable_regions.get(key))) {
				Log.i(TAG, "Region is already Ranged");
			} else {
				try{
					Log.i(TAG, "Start Ranging region " + rangable_regions.get(key).toString());
					beaconManager.startRangingBeaconsInRegion(rangable_regions.get(key));
				} catch (RemoteException ex) {
					Log.i(TAG, "Cannot start Ranging region " + rangable_regions.get(key).toString());
				}
			}

		}

	}

	/**
	 * return closestBEacon
	 */
	@Kroll.method
	public KrollDict getClosestBeacon()
	{

		if(nearest_beacon_now != null) {
			return nearest_beacon_now;
		} else {
			return null;
		}

	}

	/**
	 * See if Module is Ready
	 *
	 * @return true/false
	 */
	@Kroll.method
	public boolean isReady() {
		return this.ready;
	}

	/**
	 * Throttles down iBeacon library when app placed in background (but you have to
	 * detect this yourself, this module does not know when apps are put in background).
	 *
	 * @param flag Whether to enable background mode or not.
	 */
	@Kroll.method
	public void setBackgroundMode(boolean flag)
	{
		Log.i(TAG, "setBackgroundMode: " + flag);

		if (!checkAvailability()) {
			Log.i(TAG, "Bluetooth LE not available or no permissions on this device");
			return;
		}
		beaconManager.setBackgroundMode(flag);


	}

	/**
	 * Turns on auto ranging. When auto ranging is on, upon entering a region, this
	 * module will automatically begin ranging for beacons within that region, and
	 * stop ranging for beacons when the region is exited. Note ranging requires more
	 * battery power so care should be taken with this setting.
	 */
	@Kroll.method
	public void enableAutoRanging()
	{
		setAutoRange(true);
	}

	/**
	 * Turns off auto ranging. See description of enableAutoRanging for more details.
	 *
	 * @see #enableAutoRanging()
	 */
	@Kroll.method
	public void disableAutoRanging()
	{
		setAutoRange(false);
	}

	/**
	 * Turns auto ranging on or off. See description of enableAutoRanging for more details.
	 *
	 * @param autoRange if true, turns on auto ranging. Otherwise, turns it off.
	 *
	 * @see #enableAutoRanging()
	 *
	 */
	@Kroll.method
	public void setAutoRange(boolean autoRange)
	{
		Log.i(TAG, "setAutoRange: " + autoRange);
		this.autoRange = autoRange;

	}

	/**
	 * Set the scan periods for the bluetooth scanner.
	 *
	 * @param scanPeriods the scan periods.
	 */
	@Kroll.method
	public void setScanPeriods(Object scanPeriods)
	{
		HashMap<String, Object> dict = (HashMap<String, Object>)scanPeriods;

		int foregroundScanPeriod = TiConvert.toInt(dict, "foregroundScanPeriod");
		int foregroundBetweenScanPeriod = TiConvert.toInt(dict, "foregroundBetweenScanPeriod");
		int backgroundScanPeriod = TiConvert.toInt(dict, "backgroundScanPeriod");
		int backgroundBetweenScanPeriod = TiConvert.toInt(dict, "backgroundBetweenScanPeriod");

		beaconManager.setForegroundScanPeriod(foregroundScanPeriod);
		beaconManager.setForegroundBetweenScanPeriod(foregroundBetweenScanPeriod);
		beaconManager.setBackgroundScanPeriod(backgroundScanPeriod);
		beaconManager.setBackgroundBetweenScanPeriod(backgroundBetweenScanPeriod);

		try{
			beaconManager.updateScanPeriods();
		} catch(RemoteException re) {
			re.printStackTrace();
		}
	}

	/**
	 * Start monitoring a region.
	 * @param region the region to monitor, expected to be a property dictionary from javascript code.
	 */
	@Kroll.method
	public void startMonitoringForRegion(Object region)
	{
		Log.i(TAG, "startMonitoringForRegion: " + region);

		if (!checkAvailability()) {
			Log.i(TAG, "Bluetooth LE not available or no permissions on this device");
			return;
		}
		try {
			HashMap<String, Object> dict = (HashMap<String, Object>)region;

			String identifier = TiConvert.toString(dict, "identifier");
			//String uuid = TiConvert.toString(dict, "uuid").toLowerCase();
			Identifier uuid = (dict.get("uuid") != null) ? Identifier.fromUuid(UUID.fromString(dict.get("uuid").toString())) : null;
			//Identifier uuid = null;
			//Integer major = (dict.get("major") != null) ? TiConvert.toInt(dict, "major") : null;
			Identifier major = (dict.get("major") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "major")) : null;
			Identifier minor = (dict.get("minor") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "minor")) : null;

			//Region r = new Region(identifier, uuid, major, minor);
			Region r = new Region(identifier, null, null, null);

			Log.i(TAG, "Beginning to monitor region " + r);
			beaconManager.startMonitoringBeaconsInRegion(r);
		} catch (RemoteException ex) {
			Log.e(TAG, "Cannot start monitoring region " + TiConvert.toString(region, "identifier"), ex);
		}
	}


	/**
	 * Compatibility method for popular iOS FOSS iBeacon library.
	 *
	 * @see #startRangingForRegion(Object)
	 *
	 * @param region the region to range, expected to be a property dictionary from javascript code.
	 */
	@Kroll.method
	public void setNormalRanging() {
		beaconManager.removeAllRangeNotifiers();
		beaconManager.addRangeNotifier(normalRangeNotifier);
	}

	@Kroll.method
	public void setSingleRanging() {
		beaconManager.removeAllRangeNotifiers();
		beaconManager.addRangeNotifier(singleRangeNotifier);
	}

	/**
	 * Start ranging a region. You can only range regions into which you have entered.
	 *
	 * @param region the region to range, expected to be a property dictionary from javascript code.
	 */
	@Kroll.method
	public void startRangingForRegion(Object region)
	{
		Log.i(TAG, "startRangingForRegion: " + region);

		if (!checkAvailability()) {
			Log.i(TAG, "Bluetooth LE not available or no permissions on this device");
			return;
		}
		try {
			HashMap<String, Object> dict = (HashMap<String, Object>)region;
			String identifier = TiConvert.toString(dict, "identifier");
			Identifier uuid = (dict.get("uuid") != null) ? Identifier.fromUuid(UUID.fromString(TiConvert.toString(dict, "uuid"))) : null;
			Identifier major = (dict.get("major") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "major")) : null;
			Identifier minor = (dict.get("minor") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "minor")) : null;

			Region r = new Region(identifier, uuid, major, minor);
			//Region r = new Region(identifier, null, null, null);
			beaconManager.startRangingBeaconsInRegion(r);
		} catch (RemoteException ex) {
			Log.e(TAG, "Cannot start ranging region " + TiConvert.toString(region, "identifier"), ex);
		}
	}


	/**
	 * Stop monitoring everything.
	 */
	@Kroll.method
	public void stopMonitoringAllRegions()
	{

		Log.i(TAG, "stopMonitoringAllRegions");

		for (Region r : beaconManager.getMonitoredRegions()) {
			try {
				beaconManager.stopMonitoringBeaconsInRegion(r);
			} catch (RemoteException ex) {
				Log.e(TAG, "Cannot stop monitoring region " + r.getUniqueId(), ex);
			}
		}

	}

	/**
	 * Stop ranging for everything.
	 */
	@Kroll.method
	public void stopRangingForAllBeacons()
	{
		Log.i(TAG, "stopRangingForAllBeacons");

		for (Region r : beaconManager.getRangedRegions()) {
			try {
				beaconManager.stopRangingBeaconsInRegion(r);
			} catch (RemoteException ex) {
				Log.e(TAG, "Cannot stop ranging region " + r.getUniqueId(), ex);
			}
		}
	}


	@Override
	public void onStart(Activity activity)
	{
		// This method is called when the module is loaded and the root context is started
		Log.i(TAG, "[MODULE LIFECYCLE EVENT] start");
		beaconManager.bind(this);
		super.onStart(activity);
	}

	@Override
	public void onStop(Activity activity)
	{
		// This method is called when the root context is stopped

		Log.i(TAG, "[MODULE LIFECYCLE EVENT] stop");

		if (!beaconManager.isBound(this)) {
			beaconManager.bind(this);
		}
		super.onStop(activity);
	}

	@Override
	public void onPause(Activity activity)
	{
		// This method is called when the root context is being suspended

		Log.i(TAG, "[MODULE LIFECYCLE EVENT] pause");
		if (!beaconManager.isBound(this)) {
			beaconManager.bind(this);
		}

		super.onPause(activity);
	}

	@Override
	public void onResume(Activity activity)
	{
		// This method is called when the root context is being resumed

		Log.i(TAG, "[MODULE LIFECYCLE EVENT] resume");
		if (!beaconManager.isBound(this)) {
			beaconManager.bind(this);
		}

		super.onResume(activity);
	}

	@Override
	public void onDestroy(Activity activity)
	{
		// This method is called when the root context is being destroyed

		Log.i(TAG, "[MODULE LIFECYCLE EVENT] destroy");
		beaconManager.unbind(this);

		super.onDestroy(activity);
	}

	public void onBeaconServiceConnect() {

		Log.i(TAG, "onBeaconServiceConnect");
		//beaconManager.setAndroidLScanningDisabled(true);
		setRssiFilterClass("arma", 3500l);
		this.ready = true; //so we know the module is ready to setup event listeners
		beaconManager.getBeaconParsers().add(new BeaconParser()
           .setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
	}

	//Range Notifiers
	//This Range Notifier Return all Beacons to Titanium
	RangeNotifier normalRangeNotifier = new RangeNotifier() {
		public void didRangeBeaconsInRegion(Collection<Beacon> Beacons, Region region) {

			List<KrollDict> finalBeacons = new ArrayList<KrollDict>(Beacons.size());

			for (Beacon beacon : Beacons) {
				KrollDict beaconDict = new KrollDict();
				beaconDict.put("identifier", region.getUniqueId());
				beaconDict.put("uuid", beacon.getId1().toString());
				beaconDict.put("major", beacon.getId2().toInt());
				beaconDict.put("minor", beacon.getId3().toInt());
				beaconDict.put("distance", (float) beacon.getDistance());
				beaconDict.put("averagerssi", beacon.getRunningAverageRssi());
				beaconDict.put("rssi", beacon.getRssi());
				beaconDict.put("power", beacon.getTxPower());
				finalBeacons.add(beaconDict);

			}

			KrollDict e = new KrollDict();
			e.put("identifier", region.getUniqueId());
			e.put("beacons", finalBeacons.toArray());
			//e.put("unchangedBeacons", Beacons.size());
			fireEvent("beaconRanges", e);
		}
	};


	//This Range Notifier Returns only the Beacon that matches the set Rssi
	RangeNotifier singleRangeNotifier = new RangeNotifier() {
		public void didRangeBeaconsInRegion(Collection<Beacon> Beacons, Region region) {
			for(Beacon beacon : Beacons) {
				String key = beacon.getId1()+"-"+beacon.getId2()+"-"+beacon.getId3();
				ScannedBeacon savedB = getVisibleBeacons().get(key); //(ScannedBeacon) getVisibleBeacons().get(key);
				if(savedB != null) {
					savedB.setNewBeaconDetection(beacon);
				} else {
					ScannedBeacon scannedB = new ScannedBeacon(beacon);
					getVisibleBeacons().put(key, scannedB);
					scannedB.setNewBeaconDetection(beacon);
				}
			}

			if (System.currentTimeMillis() - singleScanStartTime <= minSingleScanLength) {
				//Scan noch nocht soweit
				KrollDict scProg = new KrollDict();
				scProg.put("seconds", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(maxSingleScanLength - (System.currentTimeMillis() - singleScanStartTime))));
				fireEvent("scanProgress", scProg);
			} else {
				int i = 0;
				for (Iterator<ScannedBeacon> scanned_beacons = getVisibleBeacons().values().iterator(); scanned_beacons.hasNext();) {
					ScannedBeacon cB = scanned_beacons.next();
					double aRssiBeacon = cB.getAverageRssi();
					if(i == 0) {
						nearest_beacon_rssi = aRssiBeacon;
						nearest_beacon_now = cB.getBeaconData();
					} else if (aRssiBeacon > nearest_beacon_rssi) {
						nearest_beacon_rssi = aRssiBeacon;
						nearest_beacon_now = cB.getBeaconData();
					}
					i++;
				}

				if(System.currentTimeMillis() - singleScanStartTime >= maxSingleScanLength) {
					//Wenn die maximale Scanzeit erreicht ist

					if(debug) {
						KrollDict debugOutput = makeDebugOutput();
						debugOutput.put("nearest_beacon_rssi", String.valueOf(nearest_beacon_rssi));
						if(nearest_beacon_now == null) {
							debugOutput.put("nearest_beacon_now", "No Beacon found!");
						} else {
							debugOutput.put("nearest_beacon_now", nearest_beacon_now.get("beacon_id"));
						}

						debugOutput.put("reason", "maxScanTime reached");
						debugOutput.put("total_beacons_found", getVisibleBeacons().size());
						fireEvent("debugBeacon", debugOutput);
					}

					if(nearest_beacon_now == null) {
						KrollDict nBeacon = new KrollDict();
						nBeacon.put("beacon_id", "null");
						fireEvent("singleBeacon", nBeacon);
					} else {
						fireEvent("singleBeacon", nearest_beacon_now);
					}
					stopRangingForAllBeacons();
					//Stop scanning

				} else if(System.currentTimeMillis() - singleScanStartTime > minSingleScanLength) {
					if(nearest_beacon_now != null && (int) Math.round((Double) nearest_beacon_now.get("rssi")) > triggerRssi) {

						if(debug) {
							KrollDict debugOutput = makeDebugOutput();
							debugOutput.put("nearest_beacon_rssi", String.valueOf(nearest_beacon_rssi));
							debugOutput.put("nearest_beacon_now", nearest_beacon_now.get("beacon_id"));
							debugOutput.put("reason", "Rssi Treshold triggered");
							debugOutput.put("total_beacons_found", getVisibleBeacons().size());
							fireEvent("debugBeacon", debugOutput);
						}
						fireEvent("singleBeacon", nearest_beacon_now);
						stopRangingForAllBeacons();
					} else {
						KrollDict scProg = new KrollDict();
						scProg.put("seconds", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(maxSingleScanLength - (System.currentTimeMillis() - singleScanStartTime))));
						fireEvent("scanProgress", scProg);
					}
				}
			}
		}
	};


	private KrollDict makeDebugOutput () {
		class BeaconKrollDictComparator implements Comparator<KrollDict> {
			@Override
			public int compare(KrollDict d1, KrollDict d2) {
				return Double.compare( (Double) d2.get("rssi"), (Double) d1.get("rssi"));
			}
		}

		List<KrollDict> scannedBeacons = new ArrayList<KrollDict>(getVisibleBeacons().size());

		for (ScannedBeacon value : getVisibleBeacons().values()) {
		    scannedBeacons.add(value.getBeaconData());
		}


		Collections.sort(scannedBeacons, new BeaconKrollDictComparator());

		KrollDict e = new KrollDict();
		e.put("beacons", scannedBeacons.toArray());
		return e;
	}

	// methods to bind and unbind

	public Context getApplicationContext() {
		while (super.getActivity() == null){
			Log.i(TAG, "Activity is null");
		}
		return super.getActivity().getApplicationContext();
	}

	public void unbindService(ServiceConnection serviceConnection) {
		Log.i(TAG, "unbindService");
		super.getActivity().unbindService(serviceConnection);
	}

	public boolean bindService(Intent intent, ServiceConnection serviceConnection, int i) {
		Log.i(TAG, "bindService");
		return super.getActivity().bindService(intent, serviceConnection, i);
	}
}
