package io.github.subhaneetshrestha.atomic.core.collections

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.IOException
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.ParserConfigurationException
import javax.xml.parsers.SAXParserFactory

/** The image addresses a collection document holds, and anything worth telling the user about it. */
data class ParsedCollection(
    val urls: List<String>,
    val warnings: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = urls.isEmpty()
}

/**
 * Reads the four kinds of collection document into a list of image addresses. Parsing never
 * throws: a document that cannot be read comes back empty with a reason, because this runs on a
 * background thread hours after the user typed the address and there is nobody to tell.
 *
 * Everything found is put through [UrlRules], so a document can only ever widen the list, never
 * the rules.
 */
object CollectionParser {
    /** Keys whose string values are addresses, used only when nothing in the document looks like an image. */
    private val ADDRESS_KEYS = setOf("url", "path", "src", "image", "img", "href", "link", "thumb", "thumbnail")

    private const val MAX_DEPTH = 8

    private val lenient = Json { ignoreUnknownKeys = true }

    fun parse(
        kind: SourceKind,
        body: String,
        baseUrl: String,
    ): ParsedCollection =
        when (kind) {
            SourceKind.IMAGE -> ParsedCollection(UrlRules.clean(listOf(baseUrl)))
            SourceKind.TEXT -> text(body, baseUrl)
            SourceKind.JSON -> json(body, baseUrl)
            SourceKind.FEED -> feed(body, baseUrl)
        }

    /** One address per line; `#` starts a comment, blank lines are skipped. */
    private fun text(
        body: String,
        baseUrl: String,
    ): ParsedCollection {
        val found =
            body
                .lineSequence()
                .map { it.substringBefore('#').trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { UrlRules.resolve(baseUrl, it) }
                .toList()
        return ParsedCollection(UrlRules.clean(found), emptyList())
    }

    /**
     * Any JSON shape: the tree is walked for strings that end in an image extension, which is what
     * an image host's own list looks like. Only when there are none does it fall back to strings
     * under keys that name addresses, which is how a document with extensionless URLs is read.
     */
    private fun json(
        body: String,
        baseUrl: String,
    ): ParsedCollection {
        val root =
            try {
                lenient.parseToJsonElement(body)
            } catch (e: SerializationException) {
                return ParsedCollection(emptyList(), listOf("the JSON could not be read: ${e.message}"))
            }
        val images = ArrayList<String>()
        val addresses = ArrayList<String>()
        walk(root, baseUrl, null, 0, images, addresses)
        if (images.isNotEmpty()) return ParsedCollection(UrlRules.clean(images))
        if (addresses.isEmpty()) return ParsedCollection(emptyList(), listOf("no image addresses in the JSON"))
        return ParsedCollection(
            UrlRules.clean(addresses),
            listOf("no address ended in an image extension; used the url, path and src fields instead"),
        )
    }

    private fun walk(
        element: JsonElement,
        baseUrl: String,
        key: String?,
        depth: Int,
        images: MutableList<String>,
        addresses: MutableList<String>,
    ) {
        if (depth > MAX_DEPTH) return
        when (element) {
            is JsonArray -> {
                element.forEach { walk(it, baseUrl, key, depth + 1, images, addresses) }
            }

            is JsonObject -> {
                element.forEach { (name, value) ->
                    walk(value, baseUrl, name.lowercase(), depth + 1, images, addresses)
                }
            }

            is JsonPrimitive -> {
                if (!element.isString) return
                val resolved = UrlRules.resolve(baseUrl, element.content) ?: return
                when {
                    UrlRules.looksLikeImage(resolved) -> images += resolved
                    key in ADDRESS_KEYS -> addresses += resolved
                }
            }
        }
    }

    /**
     * RSS, Media RSS and Atom: the images are the enclosures, not the article links. Namespaces
     * are ignored and tags matched on their local name, because feeds in the wild bind the media
     * prefix to whatever they like.
     */
    private fun feed(
        body: String,
        baseUrl: String,
    ): ParsedCollection {
        // A document type definition can expand to gigabytes from a few hundred bytes, and no feed
        // the launcher wants needs one, so one is grounds to stop rather than to configure around.
        if (DOCTYPE.containsMatchIn(body)) {
            return ParsedCollection(emptyList(), listOf("the feed declares a document type, which is not read"))
        }
        val handler = EnclosureHandler(baseUrl)
        return try {
            parserFactory().newSAXParser().parse(InputSource(StringReader(body)), handler)
            ParsedCollection(UrlRules.clean(handler.found), handler.warnings)
        } catch (e: SAXException) {
            ParsedCollection(UrlRules.clean(handler.found), listOf("the feed is not well-formed XML: ${e.message}"))
        } catch (e: IOException) {
            ParsedCollection(emptyList(), listOf("the feed could not be read: ${e.message}"))
        } catch (e: ParserConfigurationException) {
            ParsedCollection(emptyList(), listOf("no XML reader on this device: ${e.message}"))
        }
    }

    private fun parserFactory(): SAXParserFactory =
        SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            trySetFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }

    /** Readers differ in which of these they know; one refusing a feature is not a reason to fail. */
    private fun SAXParserFactory.trySetFeature(
        name: String,
        value: Boolean,
    ) {
        try {
            setFeature(name, value)
        } catch (e: SAXException) {
            return
        } catch (e: ParserConfigurationException) {
            return
        }
    }

    private val DOCTYPE = Regex("<!DOCTYPE", RegexOption.IGNORE_CASE)

    private class EnclosureHandler(
        private val baseUrl: String,
    ) : DefaultHandler() {
        val found = ArrayList<String>()
        val warnings = ArrayList<String>()

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes,
        ) {
            val tag = (qName ?: localName ?: return).substringAfterLast(':').lowercase()
            val type = attributes.getValue("type")
            when (tag) {
                "enclosure", "content", "thumbnail" -> {
                    take(attributes.getValue("url"), type, attributes.getValue("medium"))
                }

                "link" -> {
                    if (attributes.getValue("rel").equals("enclosure", ignoreCase = true)) {
                        take(attributes.getValue("href"), type, null)
                    }
                }
            }
        }

        /** An enclosure is taken when it says it is an image, or when its address ends like one. */
        private fun take(
            url: String?,
            type: String?,
            medium: String?,
        ) {
            val resolved = url?.let { UrlRules.resolve(baseUrl, it) } ?: return
            val declaredImage =
                type?.startsWith("image/", ignoreCase = true) == true ||
                    medium.equals("image", ignoreCase = true)
            val declaredOther = type != null && !type.startsWith("image/", ignoreCase = true)
            when {
                declaredImage -> found += resolved
                declaredOther -> Unit
                UrlRules.looksLikeImage(resolved) -> found += resolved
            }
        }
    }
}
