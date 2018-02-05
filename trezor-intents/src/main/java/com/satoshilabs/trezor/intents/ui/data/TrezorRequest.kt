package com.satoshilabs.trezor.intents.ui.data

import android.annotation.SuppressLint
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

abstract class TrezorRequest : Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
class InitializeRequest : TrezorRequest(), Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
class GetPublicKeyRequest(val path: IntArray, val initialize: Boolean = true) : TrezorRequest(), Parcelable
