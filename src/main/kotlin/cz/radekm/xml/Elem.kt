package cz.radekm.xml

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `Elem` instance can be constructed only for XML elements
 * (a) which contain either elements, comments and blank text nodes
 * (b) or which contain comments and text nodes.
 * So mixed content (non-blank text nodes and elements) is not supported.
 *
 *
 * `children` is a list of elements contained by this element.
 *
 * `text` is defined iff element contained at least one text node
 * and any number of comment nodes (implies it contained no elements and no cdata).
 * If element contained more than one text node then `text`
 * is concatenation of their content.
 */
data class Elem(
        val name: String,
        val attrs: Map<String, String>,
        val children: List<Elem>,
        val text: String?
) {
    companion object {
        fun formattedAttrs(attrs: Map<String, String>) =
                attrs.toList().joinToString(",") { (k, v) -> "$k=$v" }

        fun formattedText(text: String) =
                text.trim().take(70).replace("\n", "\\n")
    }

    init {
        require(name.isNotEmpty()) { "Name must be non-empty." }
        require(children.isEmpty() || text == null) { "Mixed content is not supported. Element $name." }
    }

    fun formattedIntro(): String {
        val attributes = formattedAttrs(attrs)
        return if (attributes.isEmpty()) name
        else "$name @ $attributes"
    }

    fun formattedLines(): List<String> {
        val intro = "- ${formattedIntro()}"
        return if (text != null) {
            listOf(intro, "  # ${formattedText(text)}")
        } else {
            val children = children
                    .flatMap { it.formattedLines() }
                    .map { line -> "  $line" }
            listOf(intro) + children
        }
    }

    fun formatted() = formattedLines().joinToString("\n")

    override fun toString() = formatted()
}

object ElemReader {
    fun fromFile(file: String): Elem {
        val file = File(file)
        val builderFactory = DocumentBuilderFactory.newInstance()
        val builder = builderFactory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()
        return fromElement(doc.documentElement)
    }

    fun fromDocument(doc: Document): Elem = fromElement(doc.documentElement)

    fun fromElement(element: Element): Elem {
        // Remove namespace.
        val elName = element.tagName.takeLastWhile { it != ':' }

        val attrs = mutableMapOf<String, String>()
        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i)
            val name = attr.nodeName
            val value = attr.nodeValue
            if (name in attrs) {
                error("Element $elName contains duplicate attribute $name. Values ${attrs[name]} and $value.")
            }
            attrs[name] = value
        }

        val children = mutableListOf<Elem>()
        var text: String? = null
        for (i in 0 until element.childNodes.length) {
            val node = element.childNodes.item(i)
            when (node.nodeType) {
                Node.ELEMENT_NODE -> children += fromElement(node as Element)
                Node.TEXT_NODE -> text = (text ?: "") + node.nodeValue
                Node.COMMENT_NODE -> { /* Ignore comments. */ }
                else -> error("Element $elName contains node of unsupported type: ${node.nodeType}.")
            }
        }

        // Child elements and blank text nodes are ok - we just ignore blank text nodes.
        // If there are no child elements then we keep text even if it is blank.
        if (children.isNotEmpty() && text.isNullOrBlank()) {
            text = null
        }
        return Elem(elName, attrs, children, text)
    }
}
