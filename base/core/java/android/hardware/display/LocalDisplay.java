/*
 * Copyright (C) 2012 The Android Open Source Project
 * Copyright (C) 2013 Freescale Semiconductor, Inc.
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

package android.hardware.display;

import android.os.Parcel;
import android.os.Parcelable;

import libcore.util.Objects;


/**
 * Describes the properties of a Local display.
 * <p>
 * This object is immutable.
 * </p>
 *
 * @hide
 */
public final class LocalDisplay implements Parcelable {
    public static final int SETTING_MODE_FULL_SCREEN = 0x1000;
    public static final int SETTING_MODE_KEEP_PRIMARY_RATE = 0x2000;
    public static final int SETTING_MODE_KEEP_16_9_RATE = 0x4000;
    public static final int SETTING_MODE_KEEP_4_3_RATE = 0x8000;

    /** Display state: Not connected. */
    public static final String DISPLAY_CONNECT_STATE = "android.intent.action.DISPLAY_CONNECT_STATE";
    public static final String EXTRA_DISPLAY_CONNECT = "display_connect";
    public static final String EXTRA_DISPLAY_ID = "display_id";
    public static final int DISPLAY_STATE_NOT_CONNECTED = 0;
    /** Display state: Connecting to active display. */
    public static final int DISPLAY_STATE_CONNECTING = 1;
    /** Display state: Connected to active display. */
    public static final int DISPLAY_STATE_CONNECTED = 2;

    private int mXOverScan;
    private int mYOverScan;
    private int mKeepRate;
    private int mResolution;
    private int mRotAngle;
    private int mNavBarVisible;
    private String mDeviceName;
    private boolean mConnected = false;

    public LocalDisplay(int xOverScan, int yOverScan, int keepRate, int resolution, int rotAngle, int navBarVisible, String deviceName) {
        if (deviceName == null) {
            throw new IllegalArgumentException("deviceName must not be null");
        }
        mXOverScan = xOverScan;
        mYOverScan = yOverScan;
        mKeepRate = keepRate;
        mResolution = resolution;
        mRotAngle = rotAngle;
        mNavBarVisible = navBarVisible;
        mDeviceName = deviceName;
    }

    public int getXOverScan() {
        return mXOverScan;
    }

    public void setXOverScan(int xOverScan) {
        mXOverScan = xOverScan;
    }

    public int getYOverScan() {
        return mYOverScan;
    }

    public void setYOverScan(int yOverScan) {
        mYOverScan = yOverScan;
    }

    public int getKeepRate() {
        return mKeepRate;
    }

    public void setKeepRate(int keepRate) {
        mKeepRate = keepRate;
    }

    public void setResolution(int resolution) {
        mResolution = resolution;
    }

    public int getResolution() {
        return mResolution;
    }

    public void setRotAngle(int rotAngle) {
        mRotAngle = rotAngle;
    }

    public int getRotAngle() {
        return mRotAngle;
    }

    public void setNavBarVisible(int navBarVisible) {
        mNavBarVisible = navBarVisible;
    }

    public int getNavBarVisible() {
        return mNavBarVisible;
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mXOverScan);
        dest.writeInt(mYOverScan);
        dest.writeInt(mKeepRate);
        dest.writeInt(mResolution);
        dest.writeInt(mRotAngle);
        dest.writeInt(mNavBarVisible);
        dest.writeString(mDeviceName);
    }
    @Override
    public int describeContents() {
        return 0;
    }
}

