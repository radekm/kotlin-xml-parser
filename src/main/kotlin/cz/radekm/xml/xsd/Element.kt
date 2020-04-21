package cz.radekm.xml.xsd

import cz.radekm.xml.parser.*

data class Element(
        val name: String?,
        val doc: String,
        val type: Type,
        val minOccurs: Int,
        val maxOccurs: Int?
) : XsdItem() {
    sealed class Type {
        data class Ref(val name: String) : Type()
        data class Def(val complexType: ComplexType): Type()
    }
}

fun ParserScope.element(): Element {
    name("element")

    ignoreAttrWithName("final")
    ignoreAttrWithName("fixed")
    if (attr0("nillable") !in listOf("false", null))
        error("Nillable elements not supported")

    val type = oneOf<Element.Type> {
        variant {
            Element.Type.Ref(attr("type"))
        }
        variant {
            Element.Type.Def(child { complexType() })
        }
    }

    return Element(attr0("name"), documentation(), type, minOccurs(), maxOccurs())
}
