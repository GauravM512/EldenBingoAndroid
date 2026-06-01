package com.eldenbingo.android

import android.app.Application
import com.eldenbingo.android.data.network.EldenBingoClient

class EldenBingoApp : Application() {
    val client: EldenBingoClient by lazy { EldenBingoClient() }
}
