package com.satoshilabs.trezor.intents.ui.data

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import com.satoshilabs.trezor.lib.protobuf.TrezorType
import java.io.Serializable

abstract class TrezorRequest(val state: ByteString?) : Serializable

class GenericRequest @JvmOverloads constructor(
        val message: Message,
        state: ByteString? = null
) : TrezorRequest(state)

class CheckAddressRequest @JvmOverloads constructor(
        val message: TrezorMessage.GetAddress,
        val address: String,
        state: ByteString? = null
) : TrezorRequest(state)

class SignTxRequest @JvmOverloads constructor(
        val tx: TrezorType.TransactionType,
        val referencedTxs: Map<String, TrezorType.TransactionType>,
        state: ByteString? = null
) : TrezorRequest(state)
