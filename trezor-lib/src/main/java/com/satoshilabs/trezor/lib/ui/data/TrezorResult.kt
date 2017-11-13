package com.satoshilabs.trezor.lib.ui.data

import android.annotation.SuppressLint
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

public abstract class TrezorResult : Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
public class GetPublicKeyResult(val xPubKey: String) : TrezorResult(), Parcelable
