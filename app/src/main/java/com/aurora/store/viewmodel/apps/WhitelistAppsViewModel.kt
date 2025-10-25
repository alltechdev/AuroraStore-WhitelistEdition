/*
 * Aurora Store
 *  Copyright (C) 2021, Rahul Kumar Patel <whyorean@gmail.com>
 *
 *  Aurora Store is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  Aurora Store is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Aurora Store.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.aurora.store.viewmodel.apps

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.aurora.store.data.helper.DownloadHelper
import com.aurora.store.data.model.ExternalApp
import com.aurora.store.data.providers.WhitelistProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WhitelistAppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whitelistProvider: WhitelistProvider,
    private val appDetailsHelper: AppDetailsHelper,
    private val downloadHelper: DownloadHelper
) : ViewModel() {

    private val TAG = WhitelistAppsViewModel::class.java.simpleName

    private val _apps = MutableStateFlow<List<App>>(emptyList())
    val apps: StateFlow<List<App>> = _apps.asStateFlow()

    private val _unfilteredCategorizedApps = MutableStateFlow<Map<String, List<App>>>(emptyMap())
    private val _categorizedApps = MutableStateFlow<Map<String, List<App>>>(emptyMap())
    val categorizedApps: StateFlow<Map<String, List<App>>> = _categorizedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val downloadsList = downloadHelper.downloadsList

    fun download(app: App) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadHelper.enqueueApp(app)
        }
    }

    fun cancelDownload(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadHelper.cancelDownload(packageName)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        filterApps(query)
    }

    private fun filterApps(query: String) {
        if (query.isBlank()) {
            _categorizedApps.value = _unfilteredCategorizedApps.value
        } else {
            val filtered = _unfilteredCategorizedApps.value.mapValues { (_, apps) ->
                apps.filter {
                    it.displayName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
                }
            }.filterValues { it.isNotEmpty() }
            _categorizedApps.value = filtered
        }
    }

    fun fetchWhitelistApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true

                // Get external apps first
                val externalApps = whitelistProvider.getExternalApps()
                Log.d(TAG, "Found ${externalApps.size} external apps")

                // Get Play Store package names (exclude external apps)
                val playStorePackages = whitelistProvider.getPackageNames()
                    .filter { pkg -> externalApps.none { it.packageName == pkg } }
                    .toList()

                Log.d(TAG, "Fetching details for ${playStorePackages.size} Play Store apps")

                // Fetch app details from Play Store for whitelisted packages
                val playStoreApps = if (playStorePackages.isNotEmpty()) {
                    appDetailsHelper.getAppByPackageName(playStorePackages)
                        .filter { it.displayName.isNotEmpty() }
                } else {
                    emptyList()
                }

                // Convert external apps to App objects
                val externalAppsList = externalApps.map { it.toApp(context) }

                // Combine both lists
                val allApps = (playStoreApps + externalAppsList)
                    .sortedBy { it.displayName.lowercase() }

                Log.d(TAG, "Successfully fetched ${allApps.size} total apps (${playStoreApps.size} Play Store, ${externalAppsList.size} external)")

                // Group apps by category
                val categoryMap = whitelistProvider.getWhitelistByCategory()
                val categorized = mutableMapOf<String, MutableList<App>>()

                allApps.forEach { app ->
                    // Find which category this package belongs to
                    val category = categoryMap.entries.firstOrNull { entry ->
                        entry.value.contains(app.packageName)
                    }?.key ?: "Other"

                    categorized.getOrPut(category) { mutableListOf() }.add(app)
                }

                // Sort categories: put "Other" last, alphabetize rest
                val sortedCategories = categorized.entries
                    .sortedWith(compareBy<Map.Entry<String, List<App>>> { it.key == "Other" }
                        .thenBy { it.key })
                    .associate { it.key to it.value }

                _apps.value = allApps
                _unfilteredCategorizedApps.value = sortedCategories
                // Apply current search filter
                filterApps(_searchQuery.value)
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch whitelisted apps", e)
                _apps.value = emptyList()
                _unfilteredCategorizedApps.value = emptyMap()
                _categorizedApps.value = emptyMap()
                _isLoading.value = false
            }
        }
    }
}
