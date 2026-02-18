package com.unamentis.modules.knowledgebowl.ui.packs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.R
import com.unamentis.modules.knowledgebowl.data.model.KBPack
import com.unamentis.modules.knowledgebowl.data.remote.KBPackService
import com.unamentis.modules.knowledgebowl.ui.theme.KBTheme
import com.unamentis.modules.knowledgebowl.ui.theme.color
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Pack Picker screen.
 */
@HiltViewModel
class KBPackPickerViewModel
    @Inject
    constructor(
        private val packService: KBPackService,
    ) : ViewModel() {
        val packs: StateFlow<List<KBPack>> = packService.packs
        val isLoading: StateFlow<Boolean> = packService.isLoading

        private val _selectedPackId = MutableStateFlow<String?>(null)
        val selectedPackId: StateFlow<String?> = _selectedPackId.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        init {
            fetchPacks()
        }

        fun fetchPacks() {
            viewModelScope.launch {
                packService.fetchPacks()
                val error = packService.error.value
                _errorMessage.value = error?.message
            }
        }

        fun selectPack(packId: String?) {
            _selectedPackId.value = packId
        }
    }

/**
 * Pack picker screen for selecting question packs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KBPackPickerScreen(
    onNavigateBack: () -> Unit = {},
    onPackSelected: (String?) -> Unit = {},
    viewModel: KBPackPickerViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val packs by viewModel.packs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedPackId by viewModel.selectedPackId.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.kb_pack_picker_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_go_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchPacks() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.kb_pack_picker_refresh),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // "All Questions" option
            item {
                PackOptionCard(
                    name = stringResource(R.string.kb_pack_all_questions),
                    description = stringResource(R.string.kb_pack_all_questions_desc),
                    isSelected = selectedPackId == null,
                    onClick = {
                        viewModel.selectPack(null)
                        onPackSelected(null)
                    },
                )
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Error state
            if (errorMessage != null) {
                item {
                    ErrorCard(
                        message = errorMessage!!,
                        onRetry = { viewModel.fetchPacks() },
                    )
                }
            }

            // Server packs
            if (packs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.kb_pack_server_packs),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(packs) { pack ->
                    PackRow(
                        pack = pack,
                        isSelected = selectedPackId == pack.id,
                        onClick = {
                            viewModel.selectPack(pack.id)
                            onPackSelected(pack.id)
                        },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun PackOptionCard(
    name: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$name. $description" },
        colors =
            CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else KBTheme.bgSecondary(),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = KBTheme.textSecondary(),
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun PackRow(
    pack: KBPack,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else KBTheme.bgSecondary(),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pack.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.kb_pack_question_count, pack.questionCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = KBTheme.textSecondary(),
                    )
                    // Domain indicators
                    for (domain in pack.topDomains.take(MAX_DOMAIN_ICONS)) {
                        Box(
                            modifier =
                                androidx.compose.ui.Modifier
                                    .size(8.dp)
                                    .padding(0.dp)
                                    .then(
                                        Modifier.semantics { contentDescription = domain.displayName },
                                    ),
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .size(8.dp)
                                        .then(
                                            Modifier.background(
                                                domain.color(),
                                                shape = androidx.compose.foundation.shape.CircleShape,
                                            ),
                                        ),
                            )
                        }
                    }
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private const val MAX_DOMAIN_ICONS = 4

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetry) {
                Text(stringResource(R.string.kb_pack_picker_retry))
            }
        }
    }
}
