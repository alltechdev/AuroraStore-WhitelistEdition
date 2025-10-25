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
import com.aurora.store.data.installer.AppInstaller
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Enable swipe refresh for apps page
        binding.swipeRefreshLayout.isEnabled = true
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.fetchWhitelistApps()
        }

        // Toolbar
        binding.toolbar.apply {
            title = getString(R.string.title_apps)
            inflateMenu(R.menu.menu_main)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    R.id.menu_search -> {
                        toggleSearch()
                        true
                    }
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

        // Search input listener
        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            viewModel.setSearchQuery(binding.searchInput.text.toString())
            true
        }
        binding.searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Observe whitelist apps combined with download status
        viewLifecycleOwner.lifecycleScope.launch {
            combine(
                viewModel.categorizedApps,
                viewModel.downloadsList,
                viewModel.isLoading
            ) { categorizedApps, downloads, loading ->
                Triple(categorizedApps, downloads, loading)
            }.collect { (categorizedApps, downloads, loading) ->
                updateController(categorizedApps, downloads, loading)
            }
        }

        // Listen for whitelist updates and refresh
        viewLifecycleOwner.lifecycleScope.launch {
            AuroraApp.events.busEvent.collect { event ->
                if (event is BusEvent.WhitelistUpdated) {
                    binding.swipeRefreshLayout.isRefreshing = true
                    viewModel.fetchWhitelistApps()
                }
            }
        }

        // Listen for installation/uninstallation events and refresh UI
        viewLifecycleOwner.lifecycleScope.launch {
            AuroraApp.events.installerEvent.collect { event ->
                when (event) {
                    is InstallerEvent.Installed,
                    is InstallerEvent.Uninstalled -> {
                        // Trigger UI refresh to update button states
                        viewModel.fetchWhitelistApps()
                    }
                    else -> {} // Ignore other events
                }
            }
        }

        // Initial fetch
        viewModel.fetchWhitelistApps()
    }

    private fun updateController(categorizedApps: Map<String, List<App>>, downloads: List<Download>, loading: Boolean) {
        // Stop refresh animation when loading is complete
        if (!loading) {
            binding.swipeRefreshLayout.isRefreshing = false
        }

        binding.recycler.withModels {
            setFilterDuplicates(true)
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
                // Display apps grouped by category
                categorizedApps.forEach { (category, apps) ->
                    // Add category header (only if there are multiple categories)
                    if (categorizedApps.size > 1) {
                        add(
                            com.aurora.store.view.epoxy.views.HeaderViewModel_()
                                .id("category_$category")
                                .title(category)
                        )
                    }

                    // Add apps in this category
                    apps.forEach { app ->
                        val download = downloads.find { it.packageName == app.packageName }
                        val isInstalled = PackageUtil.isInstalled(requireContext(), app.packageName)

                        // Convert App to Update for display
                        val update = Update.fromApp(requireContext(), app)

                        add(
                            AppUpdateViewModel_()
                                .id(app.packageName)
                                .update(update)
                                .download(download)
                                .buttonText(if (isInstalled) getString(R.string.action_uninstall) else getString(R.string.action_install))
                                .positiveAction { _ ->
                                    if (isInstalled) {
                                        uninstallApp(app)
                                    } else {
                                        installApp(app)
                                    }
                                }
                                .negativeAction { _ -> cancelApp(app) }
                        )
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

    private fun uninstallApp(app: App) {
        // Check if root is available first
        if (isRootAvailable()) {
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm uninstall ${app.packageName}"))
                process.waitFor()
                return
            } catch (e: Exception) {
                // Root failed, fall through to user's selected installer method
            }
        }

        // Fallback to user's selected installation method (session installer, root installer, etc.)
        AppInstaller.uninstall(requireContext(), app.packageName)
    }

    private fun isRootAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su")
            process.outputStream.write("exit\n".toByteArray())
            process.outputStream.flush()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun toggleSearch() {
        if (binding.searchContainer.visibility == View.VISIBLE) {
            // Hide search
            binding.searchContainer.visibility = View.GONE
            binding.searchInput.text?.clear()
            viewModel.setSearchQuery("")
        } else {
            // Show search
            binding.searchContainer.visibility = View.VISIBLE
            binding.searchInput.requestFocus()
            // Show keyboard
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
