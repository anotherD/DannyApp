package com.danny.dannyapp.ui.profile

import android.content.Context
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.UserProfile
import com.danny.dannyapp.domain.model.Country
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

object ProfileDialogs {

    fun showProfileDialog(
        context: Context,
        existingProfile: UserProfile? = null,
        onSave: suspend (name: String, playerName: String, countryCode: String, description: String) -> SaveProfileResult,
        onSuccess: ((profileName: String) -> Unit)? = null
    ) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_new_profile, null)
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val etProfileName = dialogView.findViewById<TextInputEditText>(R.id.etProfileName)
        val etPlayerName = dialogView.findViewById<TextInputEditText>(R.id.etPlayerName)
        val actvCountry = dialogView.findViewById<AutoCompleteTextView>(R.id.actvCountry)
        val etProfileDescription = dialogView.findViewById<TextInputEditText>(R.id.etProfileDescription)

        val isEdit = existingProfile != null
        if (isEdit) {
            tvDialogTitle?.text = context.getString(R.string.profile_edit_title)
            etProfileName.setText(existingProfile.name)
            etPlayerName.setText(existingProfile.playerName)
            etProfileDescription.setText(existingProfile.description)
        } else {
            tvDialogTitle?.text = context.getString(R.string.profiles_create_new)
        }

        val countries = Country.getAllCountries()
        val countryDisplayList = countries.map { "${it.flagEmoji}  ${it.displayName}" }
        val countryAdapter = ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, countryDisplayList)
        actvCountry.setAdapter(countryAdapter)

        var selectedCountryCode = existingProfile?.countryCode ?: Country.DEFAULT_USER_CODE
        val countryIndex = if (isEdit) {
            countries.indexOfFirst { it.code.equals(existingProfile.countryCode, ignoreCase = true) }
        } else {
            countries.indexOfFirst { it.code.equals(Country.DEFAULT_USER_CODE, ignoreCase = true) }
        }
        if (countryIndex >= 0) {
            actvCountry.setText(countryDisplayList[countryIndex], false)
            selectedCountryCode = countries[countryIndex].code
        }

        actvCountry.setOnItemClickListener { _, _, position, _ ->
            selectedCountryCode = countries.getOrNull(position)?.code ?: Country.DEFAULT_USER_CODE
        }

        val positiveButtonText = if (isEdit) {
            context.getString(R.string.profile_edit_positive_button)
        } else {
            context.getString(R.string.profiles_create_positive_button)
        }

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(dialogView)
            .setPositiveButton(positiveButtonText, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton?.setOnClickListener {
                val profileName = etProfileName.text.toString().trim()
                if (profileName.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.profiles_create_error_empty_name), Toast.LENGTH_SHORT).show()
                } else {
                    val playerName = etPlayerName.text.toString().trim().ifEmpty { profileName }
                    val profileDescription = etProfileDescription.text.toString().trim()

                    val lifecycleOwner = context as? LifecycleOwner
                    val scope = lifecycleOwner?.lifecycleScope
                    scope?.launch {
                        val result = onSave(profileName, playerName, selectedCountryCode, profileDescription)
                        when (result) {
                            is SaveProfileResult.Success -> {
                                val msgRes = if (isEdit) R.string.profile_updated_msg else R.string.profile_created_msg
                                Toast.makeText(context, context.getString(msgRes, result.profileName), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                                onSuccess?.invoke(result.profileName)
                            }

                            is SaveProfileResult.DuplicateName -> {
                                Toast.makeText(context, context.getString(R.string.profiles_create_error_duplicate_name, result.profileName), Toast.LENGTH_SHORT).show()
                            }

                            is SaveProfileResult.Error -> {
                                Toast.makeText(context, context.getString(R.string.profile_create_error_general, result.message), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }

        dialog.show()
    }
}
