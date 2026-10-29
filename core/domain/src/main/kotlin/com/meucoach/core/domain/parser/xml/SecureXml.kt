package com.meucoach.core.domain.parser.xml

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.ErrorHandler
import org.xml.sax.InputSource
import org.xml.sax.SAXParseException
import java.io.ByteArrayInputStream
import java.io.StringReader
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Parses untrusted XML without network or external-entity resolution.
 *
 * Android and JVM implementations do not support the same set of parser
 * features, so every hardening feature is best-effort. The entity resolver and
 * disabled expansion remain in place even when a feature flag is unavailable.
 */
internal object SecureXml {
    fun parse(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.isExpandEntityReferences = false
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
        setFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        runCatching { factory.setXIncludeAware(false) }
        runCatching { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
        runCatching { factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }

        val builder = factory.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> InputSource(StringReader("")) }
        builder.setErrorHandler(
            object : ErrorHandler {
                override fun warning(exception: SAXParseException) = Unit

                override fun error(exception: SAXParseException) = throw exception

                override fun fatalError(exception: SAXParseException) = throw exception
            },
        )
        return builder.parse(ByteArrayInputStream(bytes))
    }

    private fun setFeature(factory: DocumentBuilderFactory, name: String, value: Boolean) {
        runCatching { factory.setFeature(name, value) }
    }
}

internal fun Document.elements(localName: String): List<Element> =
    getElementsByTagNameNS("*", localName).toElementList()

internal fun Element.elements(localName: String): List<Element> = getElementsByTagNameNS("*", localName).toElementList()

internal fun Element.children(localName: String): List<Element> =
    childNodes.toElementList().filter { it.localName == localName }

internal fun Element.child(localName: String): Element? =
    childNodes.toElementList().firstOrNull { it.localName == localName }

internal fun Element.text(localName: String): String? =
    child(localName)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

internal fun Element.double(localName: String): Double? = text(localName)?.toDoubleOrNull()

internal fun Element.int(localName: String): Int? = text(localName)?.toIntOrNull()

private fun org.w3c.dom.NodeList.toElementList(): List<Element> {
    val result = ArrayList<Element>(length)
    for (index in 0 until length) {
        val node = item(index)
        if (node.nodeType == Node.ELEMENT_NODE) result += node as Element
    }
    return result
}
