package com.danny.dannyapp.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.Country
import com.danny.dannyapp.data.db.UserProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ProfilesActivity : BaseActivity() {

    private val viewModel: ProfilesViewModel by viewModels()
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var rvProfiles: RecyclerView
    private lateinit var tvEmptyProfiles: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)
        setupToolbar(getString(R.string.profiles), showBack = true, rootViewId = R.id.profilesMain)

        rvProfiles = findViewById(R.id.rvProfiles)
        tvEmptyProfiles = findViewById(R.id.tvEmptyProfiles)
        val btnCreateNewProfile: Button = findViewById(R.id.btnCreateNewProfile)

        rvProfiles.layoutManager = LinearLayoutManager(this)
        profileAdapter = ProfileAdapter(
            onItemClick = { profile ->
                showEditProfileDialog(profile)
            },
            onItemLongClick = { profile ->
                showDeleteProfileDialog(profile)
            }
        )
        rvProfiles.adapter = profileAdapter

        btnCreateNewProfile.setOnClickListener {
            showNewProfileDialog()
        }

        observeProfiles()
    }

    private fun observeProfiles() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.profilesState.collect { profiles ->
                    profileAdapter.submitList(profiles)
                    if (profiles.isEmpty()) {
                        rvProfiles.visibility = RecyclerView.GONE
                        tvEmptyProfiles.visibility = TextView.VISIBLE
                    } else {
                        rvProfiles.visibility = RecyclerView.VISIBLE
                        tvEmptyProfiles.visibility = TextView.GONE
                    }
                }
            }
        }
    }

    private fun showDeleteProfileDialog(profile: UserProfile) {
        lifecycleScope.launch {
            val careerCount = viewModel.getCareerCountForProfile(profile.id)
            val message = if (careerCount > 0) {
                getString(R.string.profile_delete_warning_with_careers, profile.name, careerCount)
            } else {
                getString(R.string.profile_delete_message, profile.name)
            }
            MaterialAlertDialogBuilder(this@ProfilesActivity)
                .setTitle(getString(R.string.profile_delete_title))
                .setMessage(message)
                .setPositiveButton(getString(R.string.profile_delete_confirm)) { _, _ ->
                    lifecycleScope.launch {
                        val success = viewModel.deleteUserProfile(profile.id)
                        if (success) {
                            Toast.makeText(this@ProfilesActivity, getString(R.string.profile_deleted_msg, profile.name), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showNewProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_profile, null)
        val etProfileName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        val etPlayerName = dialogView.findViewById<TextInputEditText>(R.id.etPlayerName)
        val actvCountry = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCountry)
        val etProfileDescription = dialogView.findViewById<TextInputEditText>(R.id.etProfileDescription)

        val countries = Country.getAllCountries()
        val countryDisplayList = countries.map { "${it.flagEmoji}  ${it.displayName}" }
        val countryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countryDisplayList)
        actvCountry.setAdapter(countryAdapter)

        // Default to Germany (DEU) if present
        var selectedCountryCode = "DEU"
        val defaultCountryIndex = countries.indexOfFirst { it.code.equals("DEU", ignoreCase = true) }
        if (defaultCountryIndex >= 0) {
            actvCountry.setText(countryDisplayList[defaultCountryIndex], false)
            selectedCountryCode = countries[defaultCountryIndex].code
        }

        actvCountry.setOnItemClickListener { _, _, position, _ ->
            selectedCountryCode = countries.getOrNull(position)?.code ?: "DEU"
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.profiles_create_positive_button), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setOnClickListener {
                val profileName = etProfileName.text.toString().trim()
                if (profileName.isEmpty()) {
                    Toast.makeText(this, getString(R.string.profiles_create_error_empty_name), Toast.LENGTH_SHORT).show()
                } else {
                    val playerName = etPlayerName.text.toString().trim().ifEmpty { profileName }
                    val profileDescription = etProfileDescription.text.toString().trim()
                    lifecycleScope.launch {
                        val result = viewModel.saveUserProfile(profileName, playerName, selectedCountryCode, profileDescription)
                        when (result) {
                            is SaveProfileResult.Success -> {
                                Toast.makeText(this@ProfilesActivity, getString(R.string.profile_created_msg, result.profileName), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                            is SaveProfileResult.DuplicateName -> {
                                Toast.makeText(this@ProfilesActivity, getString(R.string.profiles_create_error_duplicate_name, result.profileName), Toast.LENGTH_SHORT).show()
                            }
                            is SaveProfileResult.Error -> {
                                Toast.makeText(this@ProfilesActivity, getString(R.string.profile_create_error_general, result.message), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }

    private fun showEditProfileDialog(profile: UserProfile) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_profile, null)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        tvDialogTitle?.text = getString(R.string.profile_edit_title)

        val etProfileName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        val etPlayerName = dialogView.findViewById<TextInputEditText>(R.id.etPlayerName)
        val actvCountry = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCountry)
        val etProfileDescription = dialogView.findViewById<TextInputEditText>(R.id.etProfileDescription)

        etProfileName.setText(profile.name)
        etPlayerName.setText(profile.playerName)
        etProfileDescription.setText(profile.description)

        val countries = Country.getAllCountries()
        val countryDisplayList = countries.map { "${it.flagEmoji}  ${it.displayName}" }
        val countryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countryDisplayList)
        actvCountry.setAdapter(countryAdapter)

        var selectedCountryCode = profile.countryCode
        val currentCountryIndex = countries.indexOfFirst { it.code.equals(profile.countryCode, ignoreCase = true) }
        if (currentCountryIndex >= 0) {
            actvCountry.setText(countryDisplayList[currentCountryIndex], false)
        }

        actvCountry.setOnItemClickListener { _, _, position, _ ->
            selectedCountryCode = countries.getOrNull(position)?.code ?: "DEU"
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.profile_edit_positive_button), null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setOnClickListener {
                val profileName = etProfileName.text.toString().trim()
                if (profileName.isEmpty()) {
                    Toast.makeText(this, getString(R.string.profiles_create_error_empty_name), Toast.LENGTH_SHORT).show()
                } else {
                    val playerName = etPlayerName.text.toString().trim().ifEmpty { profileName }
                    val profileDescription = etProfileDescription.text.toString().trim()
                    lifecycleScope.launch {
                        val result = viewModel.updateUserProfile(profile, profileName, playerName, selectedCountryCode, profileDescription)
                        when (result) {
                            is SaveProfileResult.Success -> {
                                Toast.makeText(this@ProfilesActivity, getString(R.string.profile_updated_msg, result.profileName), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                            is SaveProfileResult.DuplicateName -> {
                                Toast.makeText(this@ProfilesActivity, getString(R.string.profiles_create_error_duplicate_name, result.profileName), Toast.LENGTH_SHORT).show()
                            }
                            is SaveProfileResult.Error -> {
                                Toast.makeText(this@ProfilesActivity, getString(R.string.profile_create_error_general, result.message), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }
}
