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

package com.aurora.store.data.model

import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.File as PlayFile
import com.aurora.gplayapi.data.models.FileType

/**
 * Represents an external app not available on Google Play Store
 * Format: "AppName|packageName|version|apkUrl|iconUrl|category"
 */
data class ExternalApp(
    val displayName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val iconUrl: String?,
    val category: String?
) {
    /**
     * Convert ExternalApp to gplayapi App object for compatibility
     * with existing UI and download infrastructure
     */
    fun toApp(): App {
        return App(
            packageName = packageName,
            displayName = displayName,
            versionName = versionName,
            versionCode = versionCode,
            iconArtwork = iconUrl?.let {
                com.aurora.gplayapi.data.models.Artwork(
                    url = it
                )
            } ?: com.aurora.gplayapi.data.models.Artwork(),
            fileList = listOf(
                PlayFile(
                    url = apkUrl,
                    name = "$packageName.apk",
                    size = 0, // Unknown size until download starts
                    type = FileType.BASE
                )
            ),
            // Mark as external app so we can identify it later
            shortDescription = "External App"
        )
    }

    companion object {
        /**
         * Parse external app from whitelist entry
         * Format: "AppName|packageName|version|apkUrl|iconUrl|category"
         * Example: "Uber|com.uber.app|1.2.3|https://example.com/uber.apk|https://example.com/icon.png|Transport"
         */
        fun fromWhitelistEntry(entry: String): ExternalApp? {
            val parts = entry.split("|")
            if (parts.size < 4) return null // Minimum: name, package, version, apkUrl

            return try {
                val displayName = parts[0].trim()
                val packageName = parts[1].trim()
                val versionName = parts[2].trim()
                val apkUrl = parts[3].trim()
                val iconUrl = parts.getOrNull(4)?.trim()?.takeIf { it.isNotEmpty() }
                val category = parts.getOrNull(5)?.trim()?.takeIf { it.isNotEmpty() }

                // Parse version code from version name (e.g., "1.2.3" -> 10203)
                val versionCode = parseVersionCode(versionName)

                ExternalApp(
                    displayName = displayName,
                    packageName = packageName,
                    versionName = versionName,
                    versionCode = versionCode,
                    apkUrl = apkUrl,
                    iconUrl = iconUrl,
                    category = category
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Convert version string to version code for comparison
         * Example: "1.2.3" -> 10203, "5.4.3" -> 50403
         */
        private fun parseVersionCode(versionName: String): Long {
            return try {
                val parts = versionName.split(".")
                val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
                (major * 10000L + minor * 100L + patch)
            } catch (e: Exception) {
                0L
            }
        }

        /**
         * Check if whitelist entry is an external app
         */
        fun isExternalApp(entry: String): Boolean {
            return entry.contains("|")
        }
    }
}
