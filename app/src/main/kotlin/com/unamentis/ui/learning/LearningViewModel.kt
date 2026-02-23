package com.unamentis.ui.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unamentis.core.module.ModuleProtocol
import com.unamentis.core.module.ModuleRegistry
import com.unamentis.core.module.ModuleService
import com.unamentis.core.module.ModuleSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Modules browser screen.
 *
 * Exposes bundled modules (from ModuleRegistry implementations),
 * downloaded modules (from ModuleRegistry storage), and
 * server-available modules (from ModuleService API).
 *
 * @property moduleRegistry Registry of module implementations and downloads
 * @property moduleService Service for server module discovery and downloads
 */
@HiltViewModel
class LearningViewModel
    @Inject
    constructor(
        private val moduleRegistry: ModuleRegistry,
        private val moduleService: ModuleService,
    ) : ViewModel() {
        /**
         * Bundled module implementations registered at app startup.
         */
        val bundledModules: List<ModuleProtocol> = moduleRegistry.getAllImplementations()

        /**
         * Downloaded modules from server.
         */
        val downloadedModules =
            moduleRegistry.downloadedModules
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    emptyList(),
                )

        /**
         * Modules available on the server for download.
         */
        val availableModules: StateFlow<List<ModuleSummary>> =
            moduleService.availableModules
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    emptyList(),
                )

        /**
         * Whether server modules are currently loading.
         */
        val isLoading: StateFlow<Boolean> =
            moduleService.isLoading
                .stateIn(
                    viewModelScope,
                    SharingStarted.WhileSubscribed(5000),
                    false,
                )

        /**
         * Last error from module service, if any.
         */
        private val _serverError = MutableStateFlow<String?>(null)
        val serverError: StateFlow<String?> = _serverError.asStateFlow()

        init {
            refreshModules()
        }

        /**
         * Refresh available modules from the server.
         */
        fun refreshModules() {
            viewModelScope.launch {
                try {
                    moduleService.refresh()
                    _serverError.value = null
                } catch (e: Exception) {
                    _serverError.value = e.message
                }
            }
        }

        /**
         * Download a module from the server.
         */
        fun downloadModule(moduleId: String) {
            viewModelScope.launch {
                try {
                    moduleService.downloadModule(moduleId)
                    _serverError.value = null
                } catch (e: Exception) {
                    _serverError.value = e.message
                }
            }
        }

        /**
         * Delete a downloaded module.
         */
        fun deleteModule(moduleId: String) {
            moduleService.deleteModule(moduleId)
        }

        /**
         * Check if a module is already downloaded.
         */
        fun isModuleDownloaded(moduleId: String): Boolean {
            return moduleRegistry.isDownloaded(moduleId)
        }
    }
