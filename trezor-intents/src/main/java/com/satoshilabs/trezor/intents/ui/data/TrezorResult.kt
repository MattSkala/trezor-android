package com.satoshilabs.trezor.intents.ui.data

import com.google.protobuf.ByteString
import com.google.protobuf.Message
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import java.io.Serializable

abstract class TrezorResult(val state: ByteString?) : Serializable

class GenericResult(val message: Message, state: ByteString?) : TrezorResult(state)

class SignTxResult(val signedTx: String, state: ByteString?) : TrezorResult(state)

class FailureResult(val errorType: ErrorType, val message: TrezorMessage.Failure? = null) :
        TrezorResult(null)