package cz.radekm.xml.xsd

import cz.radekm.xml.parser.*

data class Sequence(
        val doc: String,
        val minOccurs: Int,
        val maxOccurs: Int?,
        val elements: List<Element>
)

fun ParserScope.sequence(): Sequence {
    name("sequence")
    ignoreChildrenWithName("any")
    return Sequence(documentation(), minOccurs(), maxOccurs(), children { element() })
}
