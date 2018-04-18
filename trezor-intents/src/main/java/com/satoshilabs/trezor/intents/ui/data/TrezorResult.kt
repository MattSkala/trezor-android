package com.satoshilabs.trezor.intents.ui.data

import android.os.Parcelable
import com.google.protobuf.Message
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import kotlinx.android.parcel.Parcelize
import java.io.Serializable

open class TrezorResult(open val message: Message) : Serializable

class InitializeResult(override val message: TrezorMessage.Features) : TrezorResult(message)

class GetPublicKeyResult(override val message: TrezorMessage.PublicKey) : TrezorResult(message)

class GetAddressResult(override val message: TrezorMessage.Address) : TrezorResult(message)

class CipherKeyValueResult(override val message: TrezorMessage.CipheredKeyValue) : TrezorResult(message)