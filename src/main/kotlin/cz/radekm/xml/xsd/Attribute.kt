package cz.radekm.xml.xsd

import cz.radekm.xml.parser.*

data class Attribute(
        val name: String,
        val doc: String,
        val type: Type,
        val use: String?,
        val default: String?
) {
    sealed class Type {
        data class Ref(val name: String) : Type()
        data class Def(val simpleType: SimpleType): Type()
    }
}

fun ParserScope.attribute(): Attribute {
    name("attribute")
    ignoreBlankText() // Sometimes attributes are not empty :-(
    val type = oneOf<Attribute.Type> {
        variant {
            Attribute.Type.Ref(attr("type"))
        }
        variant {
            Attribute.Type.Def(child { simpleType() })
        }
    }
    return Attribute(attr("name"), documentation(), type, attr0("use"), attr0("default"))
}

data class AttributeGroup(val name: String, val attributes: List<Attribute>) : XsdItem()

fun ParserScope.attributeGroup(): AttributeGroup {
    name("attributeGroup")
    return AttributeGroup(attr("name"), children { attribute() })
}
