package com.mikimn.fixture.hello;

import android.os.Parcel;
import android.os.Parcelable;

/**
 * A Parcelable defined only in this APK, like the beans and fragment state a real app puts in its
 * saved-instance-state Bundle. Reading it back needs a class loader that can see this APK.
 */
public class FxPayload implements Parcelable {
    public final String value;

    public FxPayload(String value) { this.value = value; }

    private FxPayload(Parcel in) { value = in.readString(); }

    @Override public int describeContents() { return 0; }

    @Override public void writeToParcel(Parcel dest, int flags) { dest.writeString(value); }

    public static final Creator<FxPayload> CREATOR = new Creator<FxPayload>() {
        @Override public FxPayload createFromParcel(Parcel in) { return new FxPayload(in); }
        @Override public FxPayload[] newArray(int size) { return new FxPayload[size]; }
    };
}
