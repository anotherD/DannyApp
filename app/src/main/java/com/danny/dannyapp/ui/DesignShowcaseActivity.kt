package com.danny.dannyapp.ui

import android.os.Bundle
import com.danny.dannyapp.R

class DesignShowcaseActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_design_showcase)
        setupToolbar("Design & Farben Showcase", showBack = true, rootViewId = R.id.designMain)
    }
}
