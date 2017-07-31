/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.display;

import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.Trace;
import android.util.Slog;
import android.util.SparseArray;
import android.view.Display;
import android.view.DisplayEventReceiver;
import android.view.Surface;
import android.view.SurfaceControl;

import android.hardware.display.LocalDisplay;
import android.content.Intent;
import android.os.UserHandle;
import android.util.Slog;
import android.view.DisplayCommand;

import java.io.PrintWriter;
import java.util.Arrays;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * A display adapter for the local displays managed by Surface Flinger.
 * <p>
 * Display adapters are guarded by the {@link DisplayManagerService.SyncRoot} lock.
 * </p>
 */
final class LocalDisplayAdapter extends DisplayAdapter {
    private static final String TAG = "LocalDisplayAdapter";

    private static final int[] BUILT_IN_DISPLAY_IDS_TO_SCAN = new int[] {
            SurfaceControl.BUILT_IN_DISPLAY_ID_MAIN,
            SurfaceControl.BUILT_IN_DISPLAY_ID_HDMI,
    };

    private final SparseArray<LocalDisplayDevice> mDevices =
            new SparseArray<LocalDisplayDevice>();
    private HotplugDisplayEventReceiver mHotplugReceiver;

    private final PersistentDataStore mPersistentDataStore;

    // Called with SyncRoot lock held.
    public LocalDisplayAdapter(DisplayManagerService.SyncRoot syncRoot,
            Context context, Handler handler, Listener listener,
            PersistentDataStore persistentDataStore) {
        super(syncRoot, context, handler, listener, TAG);
        mPersistentDataStore = persistentDataStore;
        mDisplayCommand = new DisplayCommand();
    }

    private DisplayCommand mDisplayCommand;
    private LocalDisplay mDisplay;

    @Override
    public void registerLocked() {
        super.registerLocked();

        mHotplugReceiver = new HotplugDisplayEventReceiver(getHandler().getLooper());

        for (int builtInDisplayId : BUILT_IN_DISPLAY_IDS_TO_SCAN) {
            tryConnectDisplayLocked(builtInDisplayId);
        }
    }

    private void tryConnectDisplayLocked(int builtInDisplayId) {
        IBinder displayToken = SurfaceControl.getBuiltInDisplay(builtInDisplayId);
        if (displayToken != null) {
            SurfaceControl.PhysicalDisplayInfo[] configs =
                    SurfaceControl.getDisplayConfigs(displayToken);
            if (configs == null) {
                // There are no valid configs for this device, so we can't use it
                Slog.w(TAG, "No valid configs found for display device " +
                        builtInDisplayId);
                return;
            }
            int activeConfig = SurfaceControl.getActiveConfig(displayToken);
            if (activeConfig < 0) {
                // There is no active config, and for now we don't have the
                // policy to set one.
                Slog.w(TAG, "No active config found for display device " +
                        builtInDisplayId);
                return;
            }
            LocalDisplayDevice device = mDevices.get(builtInDisplayId);
            if (device == null) {
                // Display was added.
                device = new LocalDisplayDevice(displayToken, builtInDisplayId,
                        configs, activeConfig);
                mDevices.put(builtInDisplayId, device);
                sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_ADDED);
            } else if (device.updatePhysicalDisplayInfoLocked(configs, activeConfig)) {
                // Display properties changed.
                sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_CHANGED);
            }
        } else {
            // The display is no longer available. Ignore the attempt to add it.
            // If it was connected but has already been disconnected, we'll get a
            // disconnect event that will remove it from mDevices.
        }
    }

    private void tryDisconnectDisplayLocked(int builtInDisplayId) {
        LocalDisplayDevice device = mDevices.get(builtInDisplayId);
        if (device != null) {
            // Display was removed.
            mDevices.remove(builtInDisplayId);
            sendDisplayDeviceEventLocked(device, DISPLAY_DEVICE_EVENT_REMOVED);
        }
    }

    static int getPowerModeForState(int state) {
        switch (state) {
            case Display.STATE_OFF:
                return SurfaceControl.POWER_MODE_OFF;
            case Display.STATE_DOZE:
                return SurfaceControl.POWER_MODE_DOZE;
            case Display.STATE_DOZE_SUSPEND:
                return SurfaceControl.POWER_MODE_DOZE_SUSPEND;
            default:
                return SurfaceControl.POWER_MODE_NORMAL;
        }
    }

    private final class LocalDisplayDevice extends DisplayDevice {
        private final int mBuiltInDisplayId;
        private final SurfaceControl.PhysicalDisplayInfo mPhys;
        private final int mDefaultPhysicalDisplayInfo;

        private DisplayDeviceInfo mInfo;
        private boolean mHavePendingChanges;
        private int mState = Display.STATE_UNKNOWN;
        private float[] mSupportedRefreshRates;
        private int[] mRefreshRateConfigIndices;
        private float mLastRequestedRefreshRate;

        public LocalDisplayDevice(IBinder displayToken, int builtInDisplayId,
                SurfaceControl.PhysicalDisplayInfo[] physicalDisplayInfos, int activeDisplayInfo) {
            super(LocalDisplayAdapter.this, displayToken);
            mBuiltInDisplayId = builtInDisplayId;
            mPhys = new SurfaceControl.PhysicalDisplayInfo(
                    physicalDisplayInfos[activeDisplayInfo]);
            mDefaultPhysicalDisplayInfo = activeDisplayInfo;
            updateSupportedRefreshRatesLocked(physicalDisplayInfos, mPhys);
        }

        public boolean updatePhysicalDisplayInfoLocked(
                SurfaceControl.PhysicalDisplayInfo[] physicalDisplayInfos, int activeDisplayInfo) {
            SurfaceControl.PhysicalDisplayInfo newPhys = physicalDisplayInfos[activeDisplayInfo];
            if (!mPhys.equals(newPhys)) {
                mPhys.copyFrom(newPhys);
                updateSupportedRefreshRatesLocked(physicalDisplayInfos, mPhys);
                mHavePendingChanges = true;
                return true;
            }
            return false;
        }

        @Override
        public void applyPendingDisplayDeviceInfoChangesLocked() {
            if (mHavePendingChanges) {
                mInfo = null;
                mHavePendingChanges = false;
            }
        }

        @Override
        public DisplayDeviceInfo getDisplayDeviceInfoLocked() {
            if (mInfo == null) {
                mInfo = new DisplayDeviceInfo();
                mInfo.width = mPhys.width;
                mInfo.height = mPhys.height;
                mInfo.refreshRate = mPhys.refreshRate;
                mInfo.supportedRefreshRates = mSupportedRefreshRates;
                mInfo.appVsyncOffsetNanos = mPhys.appVsyncOffsetNanos;
                mInfo.presentationDeadlineNanos = mPhys.presentationDeadlineNanos;
                mInfo.state = mState;

                // Assume that all built-in displays that have secure output (eg. HDCP) also
                // support compositing from gralloc protected buffers.
                if (mPhys.secure) {
                    mInfo.flags = DisplayDeviceInfo.FLAG_SECURE
                            | DisplayDeviceInfo.FLAG_SUPPORTS_PROTECTED_BUFFERS;
                }

                if (mBuiltInDisplayId == SurfaceControl.BUILT_IN_DISPLAY_ID_MAIN) {
                    mInfo.name = getContext().getResources().getString(
                            com.android.internal.R.string.display_manager_built_in_display_name);
                    mInfo.flags |= DisplayDeviceInfo.FLAG_DEFAULT_DISPLAY
                            | DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT;
                    mInfo.type = Display.TYPE_BUILT_IN;
                    mInfo.densityDpi = (int)(mPhys.density * 160 + 0.5f);
                    mInfo.xDpi = mPhys.xDpi;
                    mInfo.yDpi = mPhys.yDpi;
                    mInfo.touch = DisplayDeviceInfo.TOUCH_INTERNAL;

                    //{{
                    //by FriendlyARM
                    int rotSetting = SystemProperties.getInt("persist.demo.screenrotangle", 0);
                    int lRot = Surface.ROTATION_0, pRot = Surface.ROTATION_90;
                    if (rotSetting == 0) {
                        lRot = Surface.ROTATION_0;
                    } else if (rotSetting == 90) {
                        pRot = Surface.ROTATION_90;
                    } else if (rotSetting == 180) {
                        lRot = Surface.ROTATION_180;
                    } else if (rotSetting == 270) {
                        pRot = Surface.ROTATION_270;
                    }
                    if ("portrait".equals(SystemProperties.get("persist.demo.screenrotation"))) {
                        mInfo.rotation = pRot;
                    } else {
                        mInfo.rotation = lRot;
                    }
                    //}}

                } else {
                    mInfo.type = Display.TYPE_HDMI;
                    mInfo.flags |= DisplayDeviceInfo.FLAG_PRESENTATION;
                    mInfo.name = getContext().getResources().getString(
                            com.android.internal.R.string.display_manager_hdmi_display_name);
                    mInfo.touch = DisplayDeviceInfo.TOUCH_EXTERNAL;
                    mInfo.setAssumedDensityForExternalDisplay(mPhys.width, mPhys.height);

                    // For demonstration purposes, allow rotation of the external display.
                    // In the future we might allow the user to configure this directly.
                    if ("portrait".equals(SystemProperties.get("persist.demo.hdmirotation"))) {
                        mInfo.rotation = Surface.ROTATION_270;
                    }

                    // For demonstration purposes, allow rotation of the external display
                    // to follow the built-in display.
                    //if (SystemProperties.getBoolean("persist.demo.hdmirotates", false)) {
                    //    mInfo.flags |= DisplayDeviceInfo.FLAG_ROTATES_WITH_CONTENT;
                    //}

                    int rotSetting = SystemProperties.getInt("ro.sf.hwrotation", 0);
                    if (rotSetting == 0) {
                        mInfo.rotation = Surface.ROTATION_0;
                    } else if (rotSetting == 90) {
                        mInfo.rotation = Surface.ROTATION_90;
                    } else if (rotSetting == 180) {
                        mInfo.rotation = Surface.ROTATION_180;
                    } else if (rotSetting == 270) {
                        mInfo.rotation = Surface.ROTATION_270;
                    }
                }
            }
            return mInfo;
        }

        @Override
        public Runnable requestDisplayStateLocked(final int state) {
            if (mState != state) {
                final int displayId = mBuiltInDisplayId;
                final IBinder token = getDisplayTokenLocked();
                final int mode = getPowerModeForState(state);
                mState = state;
                updateDeviceInfoLocked();

                // Defer actually setting the display power mode until we have exited
                // the critical section since it can take hundreds of milliseconds
                // to complete.
                return new Runnable() {
                    @Override
                    public void run() {
                        Trace.traceBegin(Trace.TRACE_TAG_POWER, "requestDisplayState("
                                + Display.stateToString(state) + ", id=" + displayId + ")");
                        try {
                            SurfaceControl.setDisplayPowerMode(token, mode);
                        } finally {
                            Trace.traceEnd(Trace.TRACE_TAG_POWER);
                        }
                    }
                };
            }
            return null;
        }

        @Override
        public void requestRefreshRateLocked(float refreshRate) {
            if (mLastRequestedRefreshRate == refreshRate) {
                return;
            }
            mLastRequestedRefreshRate = refreshRate;
            if (refreshRate != 0) {
                final int N = mSupportedRefreshRates.length;
                for (int i = 0; i < N; i++) {
                    if (refreshRate == mSupportedRefreshRates[i]) {
                        final int configIndex = mRefreshRateConfigIndices[i];
                        SurfaceControl.setActiveConfig(getDisplayTokenLocked(), configIndex);
                        return;
                    }
                }
                Slog.w(TAG, "Requested refresh rate " + refreshRate + " is unsupported.");
            }
            SurfaceControl.setActiveConfig(getDisplayTokenLocked(), mDefaultPhysicalDisplayInfo);
        }

        @Override
        public void dumpLocked(PrintWriter pw) {
            super.dumpLocked(pw);
            pw.println("mBuiltInDisplayId=" + mBuiltInDisplayId);
            pw.println("mPhys=" + mPhys);
            pw.println("mState=" + Display.stateToString(mState));
        }

        private void updateDeviceInfoLocked() {
            mInfo = null;
            sendDisplayDeviceEventLocked(this, DISPLAY_DEVICE_EVENT_CHANGED);
        }

        private void updateSupportedRefreshRatesLocked(
                SurfaceControl.PhysicalDisplayInfo[] physicalDisplayInfos,
                SurfaceControl.PhysicalDisplayInfo activePhys) {
            final int N = physicalDisplayInfos.length;
            int idx = 0;
            mSupportedRefreshRates = new float[N];
            mRefreshRateConfigIndices = new int[N];
            for (int i = 0; i < N; i++) {
                final SurfaceControl.PhysicalDisplayInfo phys = physicalDisplayInfos[i];
                if (activePhys.width == phys.width
                        && activePhys.height == phys.height
                        && activePhys.density == phys.density
                        && activePhys.xDpi == phys.xDpi
                        && activePhys.yDpi == phys.yDpi) {
                    mSupportedRefreshRates[idx] = phys.refreshRate;
                    mRefreshRateConfigIndices[idx++] = i;
                }
            }
            if (idx != N) {
                mSupportedRefreshRates = Arrays.copyOfRange(mSupportedRefreshRates, 0, idx);
                mRefreshRateConfigIndices = Arrays.copyOfRange(mRefreshRateConfigIndices, 0, idx);
            }
        }
    }

    private final class HotplugDisplayEventReceiver extends DisplayEventReceiver {
        public HotplugDisplayEventReceiver(Looper looper) {
            super(looper);
        }

        @Override
        public void onHotplug(long timestampNanos, int builtInDisplayId, boolean connected) {
            synchronized (getSyncRoot()) {
                if (connected) {
                    tryConnectDisplayLocked(builtInDisplayId);
                    Intent intent = new Intent(LocalDisplay.DISPLAY_CONNECT_STATE);
                    intent.putExtra(LocalDisplay.EXTRA_DISPLAY_ID, builtInDisplayId);
                    intent.putExtra(LocalDisplay.EXTRA_DISPLAY_CONNECT, connected);
                    getContext().sendBroadcast(intent);
                } else {
                    tryDisconnectDisplayLocked(builtInDisplayId);
                }
            }
            updateLocalDisplay();
        }
    }

    public void updateLocalDisplay() {
        if (mPersistentDataStore == null)
            return;
        mDisplay = mPersistentDataStore.getLocalDisplay();
        if (mDisplay != null) {
            Slog.d(TAG, "Chage HDMI setting: " + mDisplay.getXOverScan() + ", " +  mDisplay.getYOverScan());
            mDisplayCommand.setOverScan(
                    mDisplay.getXOverScan(),
                    mDisplay.getYOverScan());
            mDisplayCommand.setKeepRate(
                    mDisplay.getKeepRate());
            mDisplayCommand.setHDMIResolution(
                    mDisplay.getResolution());
            mDisplayCommand.setRotAngle(
                    mDisplay.getRotAngle());
            mDisplayCommand.setNavBarVisible(
                    mDisplay.getNavBarVisible());
        }
    }

    private LocalDisplay getLocalDisplay() {
        if (mDisplay != null)
            return mDisplay;
        mDisplay = mPersistentDataStore.getLocalDisplay();
        if (mDisplay == null) {
            mDisplay = new LocalDisplay(0, 0,
                    // FIXME: correct the device name
                    LocalDisplay.SETTING_MODE_FULL_SCREEN, 10806011, 1,/* rot */  1, /* show navbar */ this.getName());
            Slog.d(TAG, "create LocalDisplay: " + mDisplay.getDeviceName());
            mPersistentDataStore.setLocalDisplay(mDisplay);
        }
        return mDisplay;
    }

    public void setHDMIXOverScan(int xOverScan) {
        getLocalDisplay().setXOverScan(xOverScan);
        mDisplayCommand.setOverScan(xOverScan, mDisplayCommand.getYOverScan());
        mPersistentDataStore.rememberLocalDisplay();
    }

    public int getHDMIKeepRate() {
        return mDisplayCommand.getKeepRate();
    }
    public int getHDMIResolution() {
        return mDisplayCommand.getResolution();
    }

    public int getHDMIRotAngle() {
        return mDisplayCommand.getRotAngle();
    }

    public int getHDMINavBarVisible() {
        return mDisplayCommand.getNavBarVisible();
    }

    public int getHDMIXOverScan() {
        return mDisplayCommand.getXOverScan();
    }
    public int getHDMIYOverScan() {
        return mDisplayCommand.getYOverScan();
    }

    public String getHDMIName() {
        return "hdmi";
    }

    public void setHDMIYOverScan(int yOverScan) {
        getLocalDisplay().setYOverScan(yOverScan);
        mDisplayCommand.setOverScan(mDisplayCommand.getXOverScan(), yOverScan);
        mPersistentDataStore.rememberLocalDisplay();
    }

/*
    private String getHdmiFbPath() {
        String mFbPath = "/sys/class/graphics/fb";
        char[] buffer = new char[1024];
        boolean isFound = false;

        for (int index = 0; index < 4; index++) {
            String dev_name_path = mFbPath + index + "/fsl_disp_dev_property";
            try {
                FileReader file = new FileReader(dev_name_path);
                int len = file.read(buffer, 0, 1024);
                file.close();
            } catch (FileNotFoundException e) {
                Slog.w(TAG, "file not found: " + dev_name_path);
            } catch (Exception e) {
                Slog.w(TAG, "", e);
            }
            String name = new String(buffer);
            if (name.toLowerCase().contains("hdmi")) {
                isFound = true;
                mFbPath = mFbPath + index;
                break;
            }
        }
        if (isFound)
            return mFbPath;
        else
            return null;
    }
*/

    public String getHDMIMode() {
        //FIXME:
/*
        char[] buffer = new char[1024];
        String mFbPath = getHdmiFbPath();
        if (mFbPath != null) {
            String modePath = mFbPath + "/mode";
            try {
                FileReader file = new FileReader(modePath);
                int len = file.read(buffer, 0, 1024);
                file.close();
                String mode = new String(buffer);
                return mode;
            } catch (FileNotFoundException e) {
                Slog.w(TAG, "file not found: " + modePath);
            } catch (Exception e) {
                Slog.w(TAG, "", e);
            }
        }
*/
        return "";
    }

    public boolean getHDMIConnectState() {
        boolean plugged = false;
        if (new File("/sys/devices/virtual/switch/hdmi/state").exists()) {
            final String filename = "/sys/class/switch/hdmi/state";
            FileReader reader = null;
            try {
                reader = new FileReader(filename);
                char[] buf = new char[15];
                int n = reader.read(buf);
                if (n > 1) {
                    plugged = 0 != Integer.parseInt(new String(buf, 0, n-1));
                }
            } catch (IOException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } catch (NumberFormatException ex) {
                Slog.w(TAG, "Couldn't read hdmi state from " + filename + ": " + ex);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                    }
                }
            }
        } 
        return plugged;
    }

    public void setHDMIKeepRate(int keepRate) {
        getLocalDisplay().setKeepRate(keepRate);
        mDisplayCommand.setKeepRate(keepRate);
        mPersistentDataStore.rememberLocalDisplay();
    }

    public void setHDMIResolution(int resolution) {
        getLocalDisplay().setResolution(resolution);
        mDisplayCommand.setHDMIResolution(resolution);
        mPersistentDataStore.rememberLocalDisplay();
    }

    public void setHDMIRotAngle(int rotAngle) {
        getLocalDisplay().setRotAngle(rotAngle);
        mDisplayCommand.setRotAngle(rotAngle);
        mPersistentDataStore.rememberLocalDisplay();
    }

    public void setHDMINavBarVisible(int v) {
        getLocalDisplay().setNavBarVisible(v);
        mDisplayCommand.setNavBarVisible(v);
        mPersistentDataStore.rememberLocalDisplay();
    }

}
