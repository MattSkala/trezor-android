package com.satoshilabs.trezor.intents.ui.data

import android.os.Parcelable
import com.google.protobuf.Message
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import com.satoshilabs.trezor.lib.protobuf.TrezorType
import kotlinx.android.parcel.Parcelize

abstract class TrezorRequest(open val message: Message) : Parcelable

@Parcelize
class InitializeRequest : TrezorRequest(TrezorMessage.Initialize.getDefaultInstance()), Parcelable

@Parcelize
class GetPublicKeyRequest(override val message: TrezorMessage.GetPublicKey) :
        TrezorRequest(message), Parcelable

@Parcelize
class GetAddressRequest(override val message: TrezorMessage.GetAddress) :
        TrezorRequest(message), Parcelable

@Parcelize
class CheckAddressRequest(override val message: TrezorMessage.GetAddress, val address: String) :
        TrezorRequest(message), Parcelable

@Parcelize
class SignTxRequest(override val message: TrezorType.TransactionType,
                    val inputTxs: Map<String, TrezorType.TransactionType>) :
        TrezorRequest(message), Parcelable

@Parcelize
class CipherKeyValueRequest(override val message: TrezorMessage.CipherKeyValue) :
        TrezorRequest(message), Parcelable