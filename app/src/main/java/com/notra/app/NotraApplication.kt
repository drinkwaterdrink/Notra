package com.notra.app

import android.app.Application
import com.notra.app.data.AppContainer

class NotraApplication : Application() {
    val container by lazy { AppContainer(this) }
}
