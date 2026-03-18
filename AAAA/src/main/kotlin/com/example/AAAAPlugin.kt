package com.example

import com.lagradost.cloudstream3.plugins.*

class AAAAPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(AAAAProvider())
    }
}
