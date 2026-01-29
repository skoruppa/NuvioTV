package com.nuvio.tv.ui.screens.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.domain.model.Video
import com.nuvio.tv.domain.repository.MetaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MetaDetailsViewModel @Inject constructor(
    private val metaRepository: MetaRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String = savedStateHandle["itemId"] ?: ""
    private val itemType: String = savedStateHandle["itemType"] ?: ""

    private val _uiState = MutableStateFlow(MetaDetailsUiState())
    val uiState: StateFlow<MetaDetailsUiState> = _uiState.asStateFlow()

    init {
        loadMeta()
    }

    fun onEvent(event: MetaDetailsEvent) {
        when (event) {
            is MetaDetailsEvent.OnSeasonSelected -> selectSeason(event.season)
            is MetaDetailsEvent.OnEpisodeClick -> { /* Navigate to stream */ }
            MetaDetailsEvent.OnPlayClick -> { /* Start playback */ }
            MetaDetailsEvent.OnRetry -> loadMeta()
            MetaDetailsEvent.OnBackPress -> { /* Handle in screen */ }
        }
    }

    private fun loadMeta() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            metaRepository.getMetaFromAllAddons(
                type = itemType,
                id = itemId
            ).collect { result ->
                when (result) {
                    is NetworkResult.Success -> {
                        val meta = result.data
                        val seasons = meta.videos
                            .mapNotNull { it.season }
                            .distinct()
                            .sorted()

                        // Prefer first regular season (> 0), fallback to season 0 (specials)
                        val selectedSeason = seasons.firstOrNull { it > 0 } ?: seasons.firstOrNull() ?: 1
                        val episodesForSeason = getEpisodesForSeason(meta.videos, selectedSeason)

                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                meta = meta,
                                seasons = seasons,
                                selectedSeason = selectedSeason,
                                episodesForSeason = episodesForSeason,
                                error = null
                            )
                        }
                    }
                    is NetworkResult.Error -> {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = result.message
                            )
                        }
                    }
                    NetworkResult.Loading -> {
                        _uiState.update { it.copy(isLoading = true) }
                    }
                }
            }
        }
    }

    private fun selectSeason(season: Int) {
        val episodes = _uiState.value.meta?.videos?.let { getEpisodesForSeason(it, season) } ?: emptyList()
        _uiState.update {
            it.copy(
                selectedSeason = season,
                episodesForSeason = episodes
            )
        }
    }

    private fun getEpisodesForSeason(videos: List<Video>, season: Int): List<Video> {
        return videos
            .filter { it.season == season }
            .sortedBy { it.episode }
    }

    fun getNextEpisodeInfo(): String? {
        val meta = _uiState.value.meta ?: return null
        val episodes = _uiState.value.episodesForSeason
        // For now, return the first episode info
        return episodes.firstOrNull()?.let { video ->
            "S${video.season}, E${video.episode}"
        }
    }
}
