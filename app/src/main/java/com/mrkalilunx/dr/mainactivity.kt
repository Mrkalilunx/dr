package com.mrkalilunx.dr

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class mainactivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this)
        tv.text = "Hello, dr"
        tv.textSize = 24f
        setContentView(tv)
    }
}
