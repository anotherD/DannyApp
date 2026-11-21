package com.danny.dannyapp.ui.profile

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.UserProfile
import com.danny.dannyapp.ui.BaseActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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
                        viewModel.deleteUserProfile(profile.id)
                        Toast.makeText(this@ProfilesActivity, getString(R.string.profile_deleted_msg, profile.name), Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun showNewProfileDialog() {
        ProfileDialogs.showProfileDialog(
            context = this,
            existingProfile = null,
            onSave = { name, playerName, countryCode, description ->
                viewModel.saveUserProfile(name, playerName, countryCode, description)
            }
        )
    }

    private fun showEditProfileDialog(profile: UserProfile) {
        ProfileDialogs.showProfileDialog(
            context = this,
            existingProfile = profile,
            onSave = { name, playerName, countryCode, description ->
                viewModel.updateUserProfile(profile, name, playerName, countryCode, description)
            }
        )
    }
}
