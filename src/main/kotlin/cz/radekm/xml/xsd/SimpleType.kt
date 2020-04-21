package cz.radekm.xml.xsd

import cz.radekm.xml.parser.*

sealed class SimpleType : XsdItem() {
    abstract val name: String?
    abstract val doc: String

    sealed class Restriction : SimpleType() {
        data class Length(
                override val name: String?,
                override val doc: String,
                val base: String,
                val minLength: Int?,
                val maxLength: Int?
        ) : Restriction()
        data class Enum(
                override val name: String?,
                override val doc: String,
                val base: String,
                val allowedValues: kotlin.collections.List<String>
        ) : Restriction()
    }

    data class List(
            override val name: String?,
            override val doc: String,
            val itemType: String
    ) : SimpleType()

    data class Union(
            override val name: String?,
            override val doc: String,
            val memberTypes: kotlin.collections.List<String>
    ) : SimpleType()

    data class Alias(
            override val name: String?,
            override val doc: String,
            val aliasedType: String
    ) : SimpleType()
}

fun ParserScope.simpleType(): SimpleType {
    name("simpleType")
    val name = attr0("name")
    val doc = documentation()

    return oneOf<SimpleType> {
        variant {
            child {
                name("restriction")
                val base = attr("base")
                oneOf<SimpleType.Restriction> {
                    variant {
                        val minLength = child0 {
                            name("minLength")
                            attr("value").toInt()
                        }
                        val maxLength = child0 {
                            name("maxLength")
                            attr("value").toInt()
                        }
                        if (minLength == null && maxLength == null) {
                            throw ParseError.Other("No length restriction given")
                        }
                        SimpleType.Restriction.Length(name, doc, base, minLength, maxLength)
                    }
                    variant {
                        val values = children {
                            name("enumeration")
                            attr("value")
                        }
                        SimpleType.Restriction.Enum(name, doc, base, values)
                    }
                }
            }
        }
        variant {
            child {
                name("list")
                val itemType = attr("itemType")
                SimpleType.List(name, doc, itemType)
            }
        }
        variant {
            child {
                name("union")
                val memberTypes = attr("memberTypes").split(" ")
                SimpleType.Union(name, doc, memberTypes)
            }
        }
        variant {
            child {
                name("restriction")
                val aliasedType = attr("base")
                SimpleType.Alias(name, doc, aliasedType)
            }
        }
    }
}
