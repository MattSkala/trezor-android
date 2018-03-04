package com.satoshilabs.trezor.intents.ui.data

import android.annotation.SuppressLint
import android.os.Parcelable
import com.google.protobuf.Message
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import kotlinx.android.parcel.Parcelize

abstract class TrezorRequest(open val message: Message) : Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
class InitializeRequest : TrezorRequest(TrezorMessage.Initialize.getDefaultInstance()), Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
class GetPublicKeyRequest(override val message: TrezorMessage.GetPublicKey) : TrezorRequest(message), Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
class GetAddressRequest(override val message: TrezorMessage.GetAddress) : TrezorRequest(message), Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
class CheckAddressRequest(override val message: TrezorMessage.GetAddress, val address: String) : TrezorRequest(message), Parcelable
