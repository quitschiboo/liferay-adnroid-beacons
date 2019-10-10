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
//import org.altbeacon.beacon.logging.*;
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
import java.util.Timer;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Collection;
import java.util.HashMap;
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
	private int triggerRssi = -65;

	//Der naechste gerangte Beacon;
	//private Beacon closestBeacon = null;
	private Double nearest_beacon_distance;
	private int nearest_beacon_rssi;
	private KrollDict nearest_beacon_now;

	//For SingleScans
 	private Hashtable<String, Region> rangable_regions;

	private long minSingleScanLength;
	private long maxSingleScanLength;
	private long singleScanStartTime;

	private Hashtable<String, BeaconEvent> visible_beacons;
	private Timer expiry_timer;
	private DisappearanceMonitor disMon;

	public Hashtable getVisibleBeacons(){
		return visible_beacons;
	}

	public LiferayBeaconsModule() {
		super();
		Log.i(TAG, "[MODULE LIFECYCLE EVENT] constructor");
		visible_beacons = new Hashtable<String, BeaconEvent>();
		rangable_regions = new Hashtable<String, Region>();

		disMon = new DisappearanceMonitor(this);

	}

	@Kroll.method
	public void setExpiryTime(long ms) {
		disMon.setExpiryTime(ms);
	}

	@Kroll.onAppCreate
	public static void onAppCreate(TiApplication app)
	{
		Log.i(TAG, "onAppCreate: Liferay Android Beacons 0.5");



		//beaconManager = BeaconManager.getInstanceForApplication(app);

		// set some less battery-intensive settings compared to the defaults
		/*
		beaconManager.setForegroundScanPeriod(1200);
		beaconManager.setForegroundBetweenScanPeriod(1200);
		beaconManager.setBackgroundScanPeriod(10000);
		beaconManager.setBackgroundBetweenScanPeriod(60 * 1000);
*/

		// to see debugging from the Radius networks lib, set this to true
		//LogM = new LogManager();

		//LogM.setLogger(Loggers.verboseLogger());

		//BackgroundPowerSaver = new BackgroundPowerSaver(app);

	}

	/**
	 */
	@Kroll.method
	public void setDebug(boolean flag) {
		beaconManager.setDebug(flag);
		/*
		if(flag){
			LogManager.setLogger(Loggers.infoLogger());
		} else {
			LogManager.setLogger(Loggers.empty());
		}*/
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
		//TiApplication appContext = TiApplication.getInstance();
    //Activity activity = appContext.getAppCurrentActivity();

		//beaconManager = BeaconManager.getInstanceForApplication(activity);
		//beaconManager = BeaconManager.getInstanceForApplication(TiApplication.getInstance());
		Log.i(TAG, "instantiateManager in activity: "+activity);
		beaconManager = BeaconManager.getInstanceForApplication(TiApplication.getAppCurrentActivity());
		beaconManager.setForegroundScanPeriod(1200);
		beaconManager.setForegroundBetweenScanPeriod(1200);
		beaconManager.setBackgroundScanPeriod(10000);
		beaconManager.setBackgroundBetweenScanPeriod(60 * 1000);
		beaconManager.setMaxTrackingAge(8400);
		beaconManager.bind(this);
		beaconManager.setDebug(false);

	}

	/**
	 * change RssiFilter
	 * filter can be: runningAverage or arma
	 */
	@Kroll.method
 	public void setRssiFilter(String filter, @Kroll.argument(optional=true) Long ms) {
		long defMs = 5000;
		if(ms != null){
			defMs = ms;//TiConvert.toDouble(ms);
		}
 		setRssiFilterClass(filter, ms);
 	}

	private void setRssiFilterClass(String filter, long ms) {
		Log.i(TAG, "Setting Rssi Filter: "+filter+" with ms: "+ms);
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
		Log.i(TAG, "clear Rangable Regions");
		rangable_regions.clear();
	}

	@Kroll.method
	public void setupRangableRegion(Object region)
	{
		Log.i(TAG, "setup Ranging Region: " + region);

		if (!checkAvailability()) {
			Log.i(TAG, "Bluetooth LE not available or no permissions on this device");
			return;
		}

			HashMap<String, Object> dict = (HashMap<String, Object>)region;
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
		minSingleScanLength = minScanLength;
		maxSingleScanLength = maxScanLength;
		singleScanStartTime = System.currentTimeMillis();
		triggerRssi = tRssi;
		if(expiry_timer!= null) {
			expiry_timer.cancel();
			expiry_timer = null;
		}
		visible_beacons.clear();

		nearest_beacon_distance = 0.0;
		nearest_beacon_rssi = 0;
		nearest_beacon_now = null;

		beaconManager.setForegroundScanPeriod(foregroundScanPeriod);
		beaconManager.setForegroundBetweenScanPeriod(foregroundBetweenScanPeriod);

		try{
			beaconManager.updateScanPeriods();
		} catch(RemoteException re) {
			re.printStackTrace();
		}

		//setSingleRanging();
		Collection alreadyRangedReagons = beaconManager.getRangedRegions();
		for(String key : rangable_regions.keySet()) {
			if(alreadyRangedReagons.contains(rangable_regions.get(key))) {
				Log.e(TAG, "Region is already Ranged");
			} else {
				try{
					Log.e(TAG, "Start Ranging region " + rangable_regions.get(key).toString());
					beaconManager.startRangingBeaconsInRegion(rangable_regions.get(key));
				} catch (RemoteException ex) {
					Log.e(TAG, "Cannot start Ranging region " + rangable_regions.get(key).toString());
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
			Log.i(TAG, "Closest Beacon is: "+nearest_beacon_now.toString());
			return nearest_beacon_now;
		} else {
			Log.i(TAG, "getClosestBeacon: Beacon IS NULL");
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

		Log.i(TAG, "setScanPeriods: " + scanPeriods);

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
		Log.i(TAG, "SET Normal Ranging");
		beaconManager.removeAllRangeNotifiers();
		beaconManager.addRangeNotifier(normalRangeNotifier);
	}

	@Kroll.method
	public void setSingleRanging() {
		Log.i(TAG, "SET Single Ranging");

		//triggerRssi = trig;
		beaconManager.removeAllRangeNotifiers();
		beaconManager.addRangeNotifier(singleRangeNotifier);
	}

	@Kroll.method
	public void setManualRanging() {
		Log.i(TAG, "SET Manual Ranging");
		if(expiry_timer != null) {
			expiry_timer = new Timer();
			expiry_timer.schedule(disMon, 1000, 1000);
		}

		beaconManager.removeAllRangeNotifiers();
		beaconManager.addRangeNotifier(manualRangeNotifier);
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
			//Identifier uuid = null;
			//Integer major = (dict.get("major") != null) ? TiConvert.toInt(dict, "major") : null;
			Identifier major = (dict.get("major") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "major")) : null;
			Identifier minor = (dict.get("minor") != null) ? Identifier.fromInt(TiConvert.toInt(dict, "minor")) : null;

			Region r = new Region(identifier, uuid, major, minor);
			//Region r = new Region(identifier, null, null, null);
			Log.i(TAG, "Beginning to monitor region " + r);
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
				Log.i(TAG, "Stopped monitoring region " + r);
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
		if(expiry_timer != null) {
			expiry_timer.cancel();
			expiry_timer = null;
		}

		for (Region r : beaconManager.getRangedRegions()) {
			try {
				beaconManager.stopRangingBeaconsInRegion(r);
				Log.i(TAG, "Stopped ranging region " + r);
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
		/*
		beaconManager.addMonitorNotifier(new MonitorNotifier() {

			public void didEnterRegion(Region region) {

				Log.i(TAG, "Entered region: " + region);

				try {
					if (autoRange) {
						Log.i(TAG, "Beginning to autoRange region " + region);
						beaconManager.startRangingBeaconsInRegion(region);
					}
					KrollDict e = new KrollDict();
					e.put("identifier", region.getUniqueId());
					fireEvent("enteredRegion", e);
				} catch (RemoteException ex) {
					Log.e(TAG, "Cannot turn on ranging for region " + region.getUniqueId(), ex);
				}
			}

			public void didExitRegion(Region region) {

				Log.i(TAG, "Exited region: " + region);

				try {
					beaconManager.stopRangingBeaconsInRegion(region);
					KrollDict e = new KrollDict();
					e.put("identifier", region.getUniqueId());
					fireEvent("exitedRegion", e);
				} catch (RemoteException ex) {
					Log.e(TAG, "Cannot turn off ranging for region " + region.getUniqueId(), ex);
				}
			}

			public void didDetermineStateForRegion(int state, Region region) {
				if (state == INSIDE) {
					try {
						if (autoRange) {
							Log.i(TAG, "Beginning to autoRange region " + region);
							beaconManager.startRangingBeaconsInRegion(region);
						}
						KrollDict e = new KrollDict();
						e.put("identifier", region.getUniqueId());
						e.put("regionState", "inside");
						fireEvent("determinedRegionState", e);
					} catch (RemoteException e) {
						Log.e(TAG, "Cannot turn on ranging for region during didDetermineState" + region);
					}
				} else if (state == OUTSIDE) {
					try {
						beaconManager.stopRangingBeaconsInRegion(region);
						KrollDict e = new KrollDict();
						e.put("identifier", region.getUniqueId());
						e.put("regionState", "outside");
						fireEvent("determinedRegionState", e);
					} catch (RemoteException e) {
						Log.e(TAG, "Cannot turn off ranging for region during didDetermineState" + region);
					}
				} else {
					Log.i(TAG, "Unknown region state: " + state + " for region: " + region);
				}

			}
		});*/

		//beaconManager.addRangeNotifier(normalRangeNotifier);
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
				//beaconDict.put("uuid", beacon.getProximityUuid());
				//beaconDict.put("major", beacon.getMajor());
				//beaconDict.put("minor", beacon.getMinor());
				beaconDict.put("uuid", beacon.getId1().toString());
				beaconDict.put("major", beacon.getId2().toInt());
				beaconDict.put("minor", beacon.getId3().toInt());
				//beaconDict.put("proximity", getProximityName(beacon.getProximity()));
				beaconDict.put("distance", (float) beacon.getDistance());
				//beaconDict.put("accuracy", beacon.getAccuracy());
				beaconDict.put("averagerssi", beacon.getRunningAverageRssi());
				beaconDict.put("rssi", beacon.getRssi());
				beaconDict.put("power", beacon.getTxPower());
				finalBeacons.add(beaconDict);

				//fireEvent("beaconProximity", beaconDict);
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
				Log.i(TAG, "save to visible_beacons: "+key);
				BeaconEvent savedB = (BeaconEvent) getVisibleBeacons().get(key);
				BeaconEvent beacon_event = new BeaconEvent(beacon);
				if(savedB != null) {
					if(savedB.getRssi() > beacon_event.getRssi()) {
						getVisibleBeacons().put(key, beacon_event);
					}
				} else {
					getVisibleBeacons().put(key, beacon_event);
				}

			}
			Log.i(TAG, "visible_beacons size: "+getVisibleBeacons().size());
			int i = 0;
			for (Iterator<BeaconEvent> beacon_events = getVisibleBeacons().values().iterator(); beacon_events.hasNext();) {
				BeaconEvent cB = beacon_events.next();
				if(i == 0) {
					Log.i(TAG, "Checking first Visible Beacon: "+i);
					nearest_beacon_distance = cB.getDistance();
					nearest_beacon_rssi = cB.getRssi();
					nearest_beacon_now = cB.getBeaconData();
				//} else if (cB.getDistance() < nearest_beacon_distance) {
				} else if (cB.getRssi() > nearest_beacon_rssi) {
					Log.i(TAG, "Checking Visible Beacon: "+i);
					nearest_beacon_distance = cB.getDistance();
					nearest_beacon_rssi = cB.getRssi();
					nearest_beacon_now = cB.getBeaconData();
				}
				i++;
			}
			if(System.currentTimeMillis() - singleScanStartTime > maxSingleScanLength) {
				//Wenn die maximale Scanzeit erreicht ist
				nearest_beacon_now.put("trigger", triggerRssi);
				nearest_beacon_now.put("location", "end");
				fireEvent("singleBeacon", nearest_beacon_now);
				stopRangingForAllBeacons();
				//Stop scanning

			} else if(System.currentTimeMillis() - singleScanStartTime > minSingleScanLength) {
				if(nearest_beacon_now != null && (int) nearest_beacon_now.get("rssi") > triggerRssi) {
					nearest_beacon_now.put("trigger", triggerRssi);
					nearest_beacon_now.put("location", "between");
					fireEvent("singleBeacon", nearest_beacon_now);
					stopRangingForAllBeacons();
				} else {
					KrollDict scProg = new KrollDict();
					scProg.put("seconds", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(maxSingleScanLength - (System.currentTimeMillis() - singleScanStartTime))));
					fireEvent("scanProgress", scProg);
				}
			} else {
				//Scan noch nocht soweit
				KrollDict scProg = new KrollDict();
				scProg.put("seconds", String.valueOf(TimeUnit.MILLISECONDS.toSeconds(maxSingleScanLength - (System.currentTimeMillis() - singleScanStartTime))));
				fireEvent("scanProgress", scProg);
			}
		}
	};

	//This Range saves the closest Beacon to the Variable closestBEacon
	RangeNotifier manualRangeNotifier = new RangeNotifier() {
		public void didRangeBeaconsInRegion(Collection<Beacon> Beacons, Region region) {
			for(Beacon beacon : Beacons) {
				String key = beacon.getId1()+"-"+beacon.getId2()+"-"+beacon.getId3();
				Log.i(TAG, "save to visible_beacons: "+key);
				BeaconEvent beacon_event = new BeaconEvent(beacon);
				getVisibleBeacons().put(key, beacon_event);
			}
			Log.i(TAG, "visible_beacons size: "+getVisibleBeacons().size());
			int i = 0;
			for (Iterator<BeaconEvent> beacon_events = getVisibleBeacons().values().iterator(); beacon_events.hasNext();) {
				BeaconEvent cB = beacon_events.next();
				if(i == 0) {
					Log.i(TAG, "Checking first Visible Beacon: "+i);
					nearest_beacon_distance = cB.getDistance();
					nearest_beacon_rssi = cB.getRssi();
					nearest_beacon_now = cB.getBeaconData();
				//} else if (cB.getDistance() < nearest_beacon_distance) {
				} else if (cB.getRssi() > nearest_beacon_rssi) {
					Log.i(TAG, "Checking Visible Beacon: "+i);
					nearest_beacon_distance = cB.getDistance();
					nearest_beacon_rssi = cB.getRssi();
					nearest_beacon_now = cB.getBeaconData();
				}
				i++;
			}

			/*
			Beacon clB = null;
			Log.i(TAG, "[manualRange notifier] Size of Col: "+Beacons.size());
			for (Beacon beacon : Beacons) {
				if(clB == null){
					Log.i(TAG, "[manualRange notifier] clB was Null "+beacon.getId2()+" - "+beacon.getId3()+" > RSSI: "+beacon.getRunningAverageRssi());
					clB = beacon;
				} else {
					//if(beacon.getRunningAverageRssi() && clB.getRunningAverageRssi()) {
						 //if(clB.getRunningAverageRssi() < beacon.getRunningAverageRssi()) {
						 if(clB.getRunningAverageRssi() < beacon.getRunningAverageRssi()) {
							 Log.i(TAG, "[manualRange notifier] new bigger Rssi "+beacon.getId2()+" - "+beacon.getId3()+" > RSSI: "+beacon.getRunningAverageRssi());
							 clB = beacon;
						 } else {
							 Log.i(TAG, "[manualRange notifier] smaller Rssi "+beacon.getId2()+" - "+beacon.getId3()+" > RSSI: "+beacon.getRunningAverageRssi());
						 }

				}
			}
			if(clB != null){
				Log.i(TAG, "[manualRange notifier] closestBeacon set To: "+clB.getId2()+" - "+clB.getId3()+" > RSSI: "+clB.getRunningAverageRssi());
				closestBeacon = clB;
			} else {
				Log.i(TAG, "[manualRange notifier] clB remains null");
			}*/
		}
	};

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
