package com.satoshilabs.trezor.intents.ui.data

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import java.io.Serializable

abstract class TrezorResult(val state: ByteString?) : Serializable

class GenericResult(val message: Message, state: ByteString?) : TrezorResult(state)

class SignTxResult(val signedTx: String, state: ByteString?) : TrezorResult(state)
