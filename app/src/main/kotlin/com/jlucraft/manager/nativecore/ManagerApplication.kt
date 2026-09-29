package com.jlucraft.manager.nativecore

import android.app.Application

class ManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        UnionNative.initialize(this)
    }
}
