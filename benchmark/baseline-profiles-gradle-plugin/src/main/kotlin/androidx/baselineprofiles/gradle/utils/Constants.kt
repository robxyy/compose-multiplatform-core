/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.baselineprofiles.gradle.utils

import com.android.build.api.AndroidPluginVersion
import org.gradle.api.attributes.Attribute

// Minimum AGP version required
internal val MIN_AGP_VERSION_REQUIRED = AndroidPluginVersion(8, 0, 0).beta(1)
internal val MAX_AGP_VERSION_REQUIRED = AndroidPluginVersion(8, 2, 0)

// Prefix for the build type baseline profiles
internal const val BUILD_TYPE_BASELINE_PROFILE_PREFIX = "nonMinified"

// Configuration consumed by this plugin that carries the baseline profile HRF file.
internal const val CONFIGURATION_NAME_BASELINE_PROFILES = "baselineProfiles"

// Custom category attribute to match the baseline profile configuration
internal const val ATTRIBUTE_CATEGORY_BASELINE_PROFILE = "baselineProfiles"

// Base folder for artifacts generated by the tasks
internal const val INTERMEDIATES_BASE_FOLDER = "intermediates/baselineprofiles"

internal val ATTRIBUTE_FLAVOR =
    Attribute.of("androidx.baselineprofiles.gradle.attributes.Flavor", String::class.java)
internal val ATTRIBUTE_BUILD_TYPE =
    Attribute.of("androidx.baselineprofiles.gradle.attributes.BuildType", String::class.java)
