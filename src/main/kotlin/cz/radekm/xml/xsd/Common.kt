package cz.radekm.xml.xsd

import cz.radekm.xml.parser.*

open class XsdItem

fun ParserScope.documentation(): String {
    return child0 {
        name("annotation")
        child {
            name("documentation")
            text0().trim()
        }
    } ?: ""
}

fun ParserScope.minOccurs(): Int {
    return attr0("minOccurs")?.toInt() ?: 1
}

fun ParserScope.maxOccurs(): Int? {
    return attr0("maxOccurs").let {
        when (it) {
            null -> 1 // Default value.
            "unbounded" -> null
            else -> it.toInt()
        }
    }
}
