package com.satoshilabs.trezor.intents.ui.data

import android.os.Parcelable
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import kotlinx.android.parcel.Parcelize

abstract class TrezorResult : Parcelable

@Parcelize
class InitializeResult(val message: TrezorMessage.Features) : TrezorResult(), Parcelable

@Parcelize
class GetPublicKeyResult(val message: TrezorMessage.PublicKey) : TrezorResult(), Parcelable

@Parcelize
class GetAddressResult(val message: TrezorMessage.Address) : TrezorResult(), Parcelable

@Parcelize
class CipherKeyValueResult(val message: TrezorMessage.CipheredKeyValue) : TrezorResult(), Parcelable