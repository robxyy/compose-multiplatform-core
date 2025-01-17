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

package androidx.appactions.interaction.capabilities.core.properties

/** The property which describes a string parameter for {@code ActionCapability}. */
class StringProperty internal constructor(
    override val possibleValues: List<StringProperty.PossibleValue>,
    override val isRequired: Boolean,
    override val isValueMatchRequired: Boolean,
    override val isProhibited: Boolean,
) : ParamProperty<StringProperty.PossibleValue> {
    /** Represents a single possible value for StringProperty. */
    class PossibleValue internal constructor(
        val name: String,
        val alternateNames: List<String>,
    ) {
        companion object {
            @JvmStatic
            fun of(name: String, vararg alternateNames: String) = PossibleValue(
                name,
                alternateNames.toList(),
            )
        }
    }

    /** Builder for {@link StringProperty}. */
    class Builder {
        private val possibleValues = mutableListOf<PossibleValue>()
        private var isRequired = false
        private var isValueMatchRequired = false
        private var isProhibited = false

        /**
         * Adds a possible string value for this property.
         *
         * @param name           the possible string value.
         * @param alternateNames the alternate names for this value.
         */
        fun addPossibleValue(name: String, vararg alternateNames: String) = apply {
            possibleValues.add(PossibleValue.of(name, *alternateNames))
        }

        /** Sets whether or not this property requires a value for fulfillment. */
        fun setRequired(isRequired: Boolean) = apply {
            this.isRequired = isRequired
        }

        /**
         * Sets whether or not this property requires that the value for this property must match
         * one of
         * the string values in the defined possible values.
         */
        fun setValueMatchRequired(isValueMatchRequired: Boolean) = apply {
            this.isValueMatchRequired = isValueMatchRequired
        }

        /**
         * Sets whether the String property is prohibited in the response.
         *
         * @param isProhibited Whether this property is prohibited in the response.
         */
        fun setProhibited(isProhibited: Boolean) = apply {
            this.isProhibited = isProhibited
        }

        /** Builds the property for this string parameter. */
        fun build() =
            StringProperty(
                possibleValues.toList(),
                isRequired,
                isValueMatchRequired,
                isProhibited,
            )
    }
}
