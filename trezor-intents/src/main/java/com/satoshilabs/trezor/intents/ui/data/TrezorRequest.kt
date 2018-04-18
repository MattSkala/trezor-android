package com.satoshilabs.trezor.intents.ui.data

import com.google.protobuf.Message
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import com.satoshilabs.trezor.lib.protobuf.TrezorType
import java.io.Serializable

open class TrezorRequest(open val message: Message) : Serializable

class InitializeRequest(override val message: TrezorMessage.Initialize) :
        TrezorRequest(message)

class GetPublicKeyRequest(override val message: TrezorMessage.GetPublicKey) :
        TrezorRequest(message)

class GetAddressRequest(override val message: TrezorMessage.GetAddress) :
        TrezorRequest(message)

class CheckAddressRequest(override val message: TrezorMessage.GetAddress, val address: String) :
        TrezorRequest(message)

class SignTxRequest(override val message: TrezorType.TransactionType,
                    val inputTxs: Map<String, TrezorType.TransactionType>) :
        TrezorRequest(message)

class CipherKeyValueRequest(override val message: TrezorMessage.CipherKeyValue) :
        TrezorRequest(message)