package cz.radekm.xml.xsd

import cz.radekm.xml.parser.*

data class ComplexType(
        val name: String?,
        val doc: String,
        val base: String?,
        val refsToAttributesGroups: List<String>,
        val attributes: List<Attribute>,
        val children: Children
) : XsdItem() {
    sealed class Children {
        data class Sequence(val sequence: cz.radekm.xml.xsd.Sequence) : Children()
        data class Choice(val choice: cz.radekm.xml.xsd.Choice) : Children()
        object None : Children()
    }
}

fun ParserScope.complexType(): ComplexType {
    name("complexType")
    ignoreAttrWithName("abstract")
    val name = attr0("name")
    val doc = documentation()

    fun ParserScope.children(): ComplexType.Children {
        return oneOf<ComplexType.Children> {
            variant { ComplexType.Children.Sequence(child { sequence() }) }
            variant { ComplexType.Children.Choice(child { choice() }) }
            variant {
                // FIXME This is hack to improve error reporting.
                checkNoRemainingItems(parseContext)
                ComplexType.Children.None
            }
        }
    }

    return oneOf<ComplexType> {
        variant {
            child {
                name("complexContent")
                child {
                    name("extension")
                    val base = attr("base")
                    val refsToAttributesGroups = children0 {
                        name("attributeGroup")
                        attr("ref")
                    }
                    val attributes = children0 { attribute() }
                    ComplexType(name, doc, base, refsToAttributesGroups, attributes, children())
                }
            }
        }
        variant {
            val refsToAttributesGroups = children0 {
                name("attributeGroup")
                attr("ref")
            }
            val attributes = children0 { attribute() }
            ignoreChildrenWithName("anyAttribute")
            ComplexType(name, doc, null, refsToAttributesGroups, attributes, children())
        }
    }
}
