/*
 * SPDX-FileCopyrightText: 2025 The Calyx Institute
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.compose.navigation

import android.os.Parcelable
import androidx.navigation3.runtime.NavKey
import com.aurora.store.data.model.PermissionType
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Destinations for navigation in compose
 */
@Parcelize
@Serializable
sealed class Screen : NavKey, Parcelable {

    companion object {
        const val PARCEL_KEY = "SCREEN"
    }

    @Serializable
    data object Whitelist : Screen()

    
    @Serializable
    data class AppDetails(val packageName: String) : Screen()

    @Serializable
    data class PermissionRationale(
        val requiredPermissions: Set<PermissionType> = emptySet()
    ) : Screen()

    @Serializable
    data object Downloads : Screen()

    @Serializable
    data object Accounts : Screen()

    @Serializable
    data object About : Screen()

    }
