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

package com.aurora.store.view.ui.apps

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.aurora.extensions.requiresObbDir
import com.aurora.gplayapi.data.models.App
import com.aurora.store.AuroraApp
import com.aurora.store.MobileNavigationDirections
import com.aurora.store.R
import com.aurora.store.data.event.BusEvent
import com.aurora.store.data.event.InstallerEvent
import com.aurora.store.data.model.MinimalApp
import com.aurora.store.data.model.PermissionType
import com.aurora.store.data.providers.PermissionProvider.Companion.isGranted
import com.aurora.store.data.room.download.Download
import com.aurora.store.data.room.update.Update
import com.aurora.store.databinding.FragmentUpdatesBinding
import com.aurora.store.util.PackageUtil
import com.aurora.store.view.epoxy.views.app.AppUpdateViewModel_
import com.aurora.store.view.epoxy.views.app.NoAppViewModel_
import com.aurora.store.view.epoxy.views.shimmer.AppListViewShimmerModel_
import com.aurora.store.view.ui.commons.BaseFragment
import com.aurora.store.viewmodel.apps.WhitelistAppsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AppsContainerFragment : BaseFragment<FragmentUpdatesBinding>() {

    private val viewModel: WhitelistAppsViewModel by viewModels()

    private var isFirstResume = true

    private data class AppsState(
        val categorizedApps: Map<String, List<App>>,
        val downloads: List<Download>,
        val loading: Boolean,
        val requiresAuth: Boolean
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enable swipe refresh for apps page
        binding.swipeRefreshLayout.isEnabled = true
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.fetchWhitelistApps(forceLoading = true)
        }

        // Toolbar
        binding.toolbar.apply {
            title = getString(R.string.title_apps)
            inflateMenu(R.menu.menu_main)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_more -> {
                        findNavController().navigate(
                            MobileNavigationDirections.actionGlobalMoreDialogFragment()
                        )
                        true
                    }
                    else -> false
                }
            }
        }

        // Observe whitelist apps combined with download status and auth requirement
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.categorizedApps,
                viewModel.downloadsList,
                viewModel.isLoading,
                viewModel.requiresAuth
            ) { categorizedApps, downloads, loading, requiresAuth ->
                AppsState(categorizedApps, downloads, loading, requiresAuth)
            }.collect { state ->
                updateController(state.categorizedApps, state.downloads, state.loading, state.requiresAuth)
            }
        }

        // Listen for whitelist updates and refresh
        viewLifecycleOwner.lifecycleScope.launch {
            AuroraApp.events.busEvent.collect { event ->
                if (event is BusEvent.WhitelistUpdated) {
                    Log.d("AppsContainerFragment", "Whitelist updated, refreshing apps")
                    viewModel.fetchWhitelistApps(forceLoading = false)
                }
            }
        }

        // Listen for installation/uninstallation events and refresh UI
        viewLifecycleOwner.lifecycleScope.launch {
            AuroraApp.events.installerEvent.collect { event ->
                when (event) {
                    is InstallerEvent.Installed -> {
                        Log.d("AppsContainerFragment", "=== App installed: ${event.packageName} ===")
                        // Longer delay to ensure PackageManager has fully updated
                        kotlinx.coroutines.delay(1000)
                        Log.d("AppsContainerFragment", "Delay complete, refreshing apps list")
                        // Refresh without loading spinner - just update button states
                        viewModel.fetchWhitelistApps(forceLoading = false)
                        // Force Epoxy to rebuild models after a short delay
                        kotlinx.coroutines.delay(100)
                        binding.recycler.requestModelBuild()
                        Log.d("AppsContainerFragment", "Forced model rebuild")
                    }
                    is InstallerEvent.Uninstalled -> {
                        Log.d("AppsContainerFragment", "=== App uninstalled: ${event.packageName} ===")
                        // Longer delay to ensure PackageManager has fully updated
                        kotlinx.coroutines.delay(1000)
                        Log.d("AppsContainerFragment", "Delay complete, refreshing apps list")
                        // Refresh without loading spinner - just update button states
                        viewModel.fetchWhitelistApps(forceLoading = false)
                        // Force Epoxy to rebuild models after a short delay
                        kotlinx.coroutines.delay(100)
                        binding.recycler.requestModelBuild()
                        Log.d("AppsContainerFragment", "Forced model rebuild")
                    }
                    else -> {} // Ignore other events
                }
            }
        }

        // Initial fetch
        viewModel.fetchWhitelistApps()
    }

    override fun onResume() {
        super.onResume()
        // Skip first resume (happens right after onViewCreated)
        // Only refresh on subsequent resumes (e.g., when returning from splash/login)
        if (isFirstResume) {
            isFirstResume = false
        } else {
            // Refresh when fragment becomes visible to catch any updates that happened while away
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(100) // Delay to ensure auth state has propagated
                viewModel.fetchWhitelistApps(forceLoading = false)
            }
        }
    }

    private fun updateController(categorizedApps: Map<String, List<App>>, downloads: List<Download>, loading: Boolean, requiresAuth: Boolean) {
        // Stop refresh animation when loading is complete
        if (!loading) {
            binding.swipeRefreshLayout.isRefreshing = false
        }

        binding.recycler.withModels {
            setFilterDuplicates(false) // Disable to ensure button state changes are reflected

            // Show login prompt if Play Store apps exist but not logged in (required)
            if (requiresAuth) {
                add(
                    NoAppViewModel_()
                        .id("requires_auth")
                        .icon(R.drawable.ic_account)
                        .message(R.string.auth_required_for_play_store_apps)
                        .showAction(true)
                        .actionMessage(R.string.action_login)
                        .actionCallback { _ ->
                            // Navigate to splash/login screen with argument to restart app after login
                            val bundle = Bundle().apply {
                                putInt("destinationId", R.id.appsContainerFragment)
                            }
                            findNavController().navigate(R.id.splashFragment, bundle)
                        }
                )
            }

            if (loading) {
                // Show loading shimmer
                for (i in 1..10) {
                    add(
                        AppListViewShimmerModel_()
                            .id(i)
                    )
                }
            } else if (categorizedApps.isEmpty()) {
                // Show empty state
                add(
                    NoAppViewModel_()
                        .id("no_apps")
                        .icon(R.drawable.ic_apps)
                        .message(R.string.no_apps_available)
                )
            } else {
                // Filter out installed apps - they should only appear in Updates tab
                val filteredCategorizedApps = categorizedApps.mapValues { (_, apps) ->
                    apps.filter { app -> !PackageUtil.isInstalled(requireContext(), app.packageName) }
                }.filterValues { it.isNotEmpty() }

                if (filteredCategorizedApps.isEmpty()) {
                    // All apps are installed - show appropriate message
                    add(
                        NoAppViewModel_()
                            .id("all_installed")
                            .icon(R.drawable.ic_apps)
                            .message(R.string.all_apps_installed)
                    )
                } else {
                    // Display apps grouped by category
                    filteredCategorizedApps.forEach { (category, apps) ->
                        // Add category header (only if there are multiple categories)
                        if (filteredCategorizedApps.size > 1) {
                            add(
                                com.aurora.store.view.epoxy.views.HeaderViewModel_()
                                    .id("category_$category")
                                    .title(category)
                            )
                        }

                        // Add apps in this category (only non-installed apps)
                        apps.forEach { app ->
                            val download = downloads.find { it.packageName == app.packageName }

                            // Convert App to Update for display
                            val update = Update.fromApp(requireContext(), app)

                            // Use a unique ID that includes download status
                            val modelId = "${app.packageName}_download:${download?.downloadStatus?.name ?: "none"}"

                            Log.d("AppsContainerFragment", "Building model for ${app.packageName}: id=$modelId")

                            add(
                                AppUpdateViewModel_()
                                    .id(modelId)
                                    .update(update)
                                    .download(download)
                                    .buttonText(getString(R.string.action_install))
                                    .positiveAction { _ ->
                                        Log.d("AppsContainerFragment", "Install clicked for ${app.packageName}")
                                        installApp(app)
                                    }
                                    .negativeAction { _ -> cancelApp(app) }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun installApp(app: App) {
        if (app.fileList.requiresObbDir()) {
            if (isGranted(requireContext(), PermissionType.STORAGE_MANAGER)) {
                viewModel.download(app)
            } else {
                permissionProvider.request(PermissionType.STORAGE_MANAGER) {
                    if (it) viewModel.download(app)
                }
            }
        } else {
            viewModel.download(app)
        }
    }

    private fun cancelApp(app: App) {
        viewModel.cancelDownload(app.packageName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
