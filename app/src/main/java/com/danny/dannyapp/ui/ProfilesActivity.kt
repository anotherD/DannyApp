package com.danny.dannyapp.ui

import android.database.sqlite.SQLiteConstraintException
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.UserProfile
import com.danny.dannyapp.data.db.UserProfileRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ProfilesActivity : BaseActivity() {

    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var profileAdapter: ProfileAdapter
    private lateinit var rvProfiles: RecyclerView
    private lateinit var tvEmptyProfiles: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profiles)
        setupToolbar(getString(R.string.profiles), showBack = true, rootViewId = R.id.profilesMain)

        userProfileRepository = UserProfileRepository(this)

        rvProfiles = findViewById(R.id.rvProfiles)
        tvEmptyProfiles = findViewById(R.id.tvEmptyProfiles)
        val btnCreateNewProfile: Button = findViewById(R.id.btnCreateNewProfile)

        rvProfiles.layoutManager = LinearLayoutManager(this)
        profileAdapter = ProfileAdapter(
            onItemClick = { profile ->
                Toast.makeText(this, getString(R.string.profile_selected_msg, profile.name), Toast.LENGTH_SHORT).show()
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
                userProfileRepository.userProfilesFlow.collect { profiles ->
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
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.profile_delete_title))
            .setMessage(getString(R.string.profile_delete_message, profile.name))
            .setPositiveButton(getString(R.string.profile_delete_confirm)) { _, _ ->
                lifecycleScope.launch {
                    userProfileRepository.deleteUserProfile(profile.id)
                    Toast.makeText(this@ProfilesActivity, getString(R.string.profile_deleted_msg, profile.name), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showNewProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_profile, null)
        val etProfileName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        val etProfileDescription = dialogView.findViewById<TextInputEditText>(R.id.etProfileDescription)

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
                    val profileDescription = etProfileDescription.text.toString().trim()
                    lifecycleScope.launch {
                        val existing = userProfileRepository.getProfileByName(profileName)
                        if (existing != null) {
                            Toast.makeText(this@ProfilesActivity, getString(R.string.profiles_create_error_duplicate_name, profileName), Toast.LENGTH_SHORT).show()
                        } else {
                            val newProfile = UserProfile(
                                name = profileName,
                                description = profileDescription
                            )
                            try {
                                userProfileRepository.saveUserProfile(newProfile)
                                Toast.makeText(this@ProfilesActivity, getString(R.string.profile_created_msg, profileName), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            } catch (e: Exception) {
                                val isDuplicate = e is SQLiteConstraintException ||
                                    e.message?.contains("UNIQUE constraint", ignoreCase = true) == true

                                val errorMessage = if (isDuplicate) {
                                    getString(R.string.profiles_create_error_duplicate_name, profileName)
                                } else {
                                    e.printStackTrace()
                                    getString(R.string.profile_create_error_general, e.localizedMessage ?: "Unbekannter Fehler")
                                }
                                Toast.makeText(this@ProfilesActivity, errorMessage, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }
}
