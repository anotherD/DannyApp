package com.danny.dannyapp.ui.tournament

import android.content.Context
import android.graphics.Typeface
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.ui.model.MatchUiModel
import com.danny.dannyapp.ui.model.PrizeMoneyItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Helper object providing dialogs for tournament detail views, including prize money overview,
 * user match option selection, manual score input, set winner options, and engine placeholders.
 */
object TournamentDialogs {

    /**
     * Shows a dialog displaying the total prize fund and round-by-round prize money breakdown.
     */
    fun showPrizeMoneyDialog(
        context: Context,
        prizeMoneyItems: List<PrizeMoneyItem>,
        event: TournamentEvent?
    ) {
        if (prizeMoneyItems.isEmpty()) return
        val totalPrize = if (event != null && event.effectiveTotalPrizeFund > 0) {
            context.getString(R.string.tournament_total_prize_fund_format, event.effectiveTotalPrizeFund.toLong())
        } else {
            context.getString(R.string.tournament_qualifying_no_prize)
        }

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val tvTotalPrizeHeader = TextView(context).apply {
            text = totalPrize
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(ContextCompat.getColor(context, R.color.rot_hell))
            setPadding(0, 0, 0, 16)
        }
        layout.addView(tvTotalPrizeHeader)

        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PrizeMoneyAdapter().apply {
                submitList(prizeMoneyItems)
            }
        }
        layout.addView(recyclerView)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.tournament_preview_prizes_title))
            .setView(layout)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    /**
     * Shows options dialog for a user match (e.g. Set Winner, Manual Score, Play Match).
     */
    fun showUserMatchOptionsDialog(
        context: Context,
        matchModel: MatchUiModel,
        debugMode: Boolean,
        onSetWinner: (MatchUiModel) -> Unit,
        onManualScore: (MatchUiModel) -> Unit,
        onLiveGame: () -> Unit
    ) {
        val options = mutableListOf<String>()

        if (debugMode) {
            options.add(context.getString(R.string.user_match_option_set_winner))
            options.add(context.getString(R.string.user_match_option_manual_score))
        }
        options.add(context.getString(R.string.user_match_option_live_game))

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.user_match_control_title))
            .setItems(options.toTypedArray()) { _, which ->
                val selectedOption = options[which]
                when (selectedOption) {
                    context.getString(R.string.user_match_option_set_winner) -> onSetWinner(matchModel)
                    context.getString(R.string.user_match_option_manual_score) -> onManualScore(matchModel)
                    context.getString(R.string.user_match_option_live_game) -> onLiveGame()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Shows set winner dialog allowing immediate selection of winner.
     */
    fun showSetWinnerDialog(
        context: Context,
        matchModel: MatchUiModel,
        onWinnerSelected: (matchId: Long, userWins: Boolean) -> Unit
    ) {
        val options = arrayOf(
            context.getString(R.string.user_match_choice_win),
            context.getString(R.string.user_match_choice_loss)
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.user_match_set_winner_title))
            .setItems(options) { _, which ->
                val userWins = (which == 0)
                onWinnerSelected(matchModel.match.id, userWins)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /**
     * Shows manual score input dialog for entering player scores directly.
     */
    fun showManualScoreInputDialog(
        context: Context,
        matchModel: MatchUiModel,
        coroutineScope: CoroutineScope,
        onSaveScore: suspend (matchId: Long, p1Score: Int, p2Score: Int) -> String?
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }

        val etP1 = EditText(context).apply {
            hint = context.getString(R.string.user_match_player_score_hint, matchModel.player1.name)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val etP2 = EditText(context).apply {
            hint = context.getString(R.string.user_match_player_score_hint, matchModel.player2.name)
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        layout.addView(etP1)
        layout.addView(etP2)

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.user_match_option_manual_score))
            .setView(layout)
            .setPositiveButton(context.getString(R.string.dialog_save), null)
            .setNegativeButton(context.getString(R.string.dialog_cancel), null)
            .create()

        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                val p1Score = etP1.text.toString().toIntOrNull()
                val p2Score = etP2.text.toString().toIntOrNull()

                if (p1Score == null || p2Score == null || p1Score < 0 || p2Score < 0) {
                    Toast.makeText(context, context.getString(R.string.user_match_invalid_numbers), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                coroutineScope.launch {
                    val errorMsg = onSaveScore(matchModel.match.id, p1Score, p2Score)
                    if (errorMsg != null) {
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    } else {
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    /**
     * Placeholder dialog for live game engine integration.
     */
    fun showLiveGameEnginePlaceholder(context: Context) {
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.user_match_option_live_game))
            .setMessage(context.getString(R.string.user_match_live_placeholder))
            .setPositiveButton(context.getString(R.string.dialog_ok), null)
            .show()
    }
}
