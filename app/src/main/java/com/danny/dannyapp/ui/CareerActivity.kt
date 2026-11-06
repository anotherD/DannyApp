package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import com.danny.dannyapp.R

class CareerActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_career)
        setupToolbar(getString(R.string.career), showBack = true, rootViewId = R.id.careerMain)

        val btnStartNewCareer: Button = findViewById(R.id.btnStartNewCareer)
        btnStartNewCareer.setOnClickListener {
            Toast.makeText(this, "Neue Karriere gestartet!", Toast.LENGTH_SHORT).show()
        }
    }
}
