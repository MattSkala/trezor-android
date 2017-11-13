package com.satoshilabs.trezor.lib.ui.data

import android.annotation.SuppressLint
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

public abstract class TrezorRequest : Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
public class GetPublicKeyRequest(val path: IntArray) : TrezorRequest(), Parcelable
