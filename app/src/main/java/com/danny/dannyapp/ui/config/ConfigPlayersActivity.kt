package com.danny.dannyapp.ui.config

import android.os.Bundle
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.danny.dannyapp.R
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.ui.BaseActivity
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ConfigPlayersActivity : BaseActivity() {

    private lateinit var playerRepository: PlayerRepository
    private lateinit var playerAdapter: ConfigPlayerAdapter
    private lateinit var rvPlayers: RecyclerView
    private lateinit var tvEmptyPlayers: TextView
    private lateinit var toggleGroupSort: MaterialButtonToggleGroup

    private var observePlayersJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config_players)
        setupToolbar(
            titleText = getString(R.string.config_players),
            subtitleText = getString(R.string.config_title),
            showBack = true,
            rootViewId = R.id.playersMain,
        )

        playerRepository = PlayerRepository(this)

        rvPlayers = findViewById(R.id.rvPlayers)
        tvEmptyPlayers = findViewById(R.id.tvEmptyPlayers)
        toggleGroupSort = findViewById(R.id.toggleGroupSort)

        rvPlayers.layoutManager = LinearLayoutManager(this)
        playerAdapter = ConfigPlayerAdapter()
        rvPlayers.adapter = playerAdapter

        toggleGroupSort.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                observePlayers(sortBySkill = checkedId == R.id.btnSortSkill)
            }
        }

        observePlayers(sortBySkill = true)
    }

    private fun observePlayers(sortBySkill: Boolean) {
        observePlayersJob?.cancel()
        observePlayersJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val playerFlow = if (sortBySkill) {
                    playerRepository.playersSortedBySkill
                } else {
                    playerRepository.playersSortedByName
                }
                playerFlow.collect { players ->
                    playerAdapter.submitList(players)
                    if (players.isEmpty()) {
                        rvPlayers.visibility = RecyclerView.GONE
                        tvEmptyPlayers.visibility = TextView.VISIBLE
                    } else {
                        rvPlayers.visibility = RecyclerView.VISIBLE
                        tvEmptyPlayers.visibility = TextView.GONE
                    }
                }
            }
        }
    }
}
