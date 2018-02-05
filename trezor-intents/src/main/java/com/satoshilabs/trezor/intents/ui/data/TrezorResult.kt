package com.satoshilabs.trezor.intents.ui.data

import android.annotation.SuppressLint
import android.os.Parcelable
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage
import kotlinx.android.parcel.Parcelize

public abstract class TrezorResult : Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
public class InitializeResult(val features: TrezorMessage.Features) : TrezorResult(), Parcelable

@SuppressLint("ParcelCreator")
@Parcelize
public class GetPublicKeyResult(val publicKey: TrezorMessage.PublicKey) : TrezorResult(), Parcelable
