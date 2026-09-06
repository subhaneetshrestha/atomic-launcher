package io.github.subhaneetshrestha.atomic.core.theme

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Tolerant JSON for documents people edit by hand and older or newer app versions write:
 * unknown keys are ignored, unknown enum values fall back to the default, absent optionals stay
 * null, and every value is written out so a document is self-describing.
 */
@OptIn(ExperimentalSerializationApi::class)
internal val ThemeJson: Json =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
        prettyPrintIndent = "  "
        allowTrailingComma = true
        allowComments = true
    }
