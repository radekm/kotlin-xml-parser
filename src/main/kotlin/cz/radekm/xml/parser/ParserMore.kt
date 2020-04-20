package cz.radekm.xml.parser

fun ParserScope.name(expectedName: String) {
    if (parseContext.name != expectedName)
        throw ParseError.Other("Element name is not $expectedName")
}

fun ParserScope.attr0(name: String): String? {
    val value = parseContext.attrs[name]
    parseContext.attrs = parseContext.attrs - name
    return value
}

fun ParserScope.attr(name: String): String {
    val value = attr0(name)
    return value ?: throw ParseError.Other("Attribute $name not found")
}

fun ParserScope.attr(name: String, expectedValue: String) {
    val value = attr(name)
    if (value != expectedValue)
        throw ParseError.Other("Attribute $name has not value $value")
}

fun ParserScope.ignoreAttrWithName(name: String) {
    parseContext.attrs = parseContext.attrs - name
}

fun <T> ParserScope.child0(parse: ParserScope.() -> T): T? {
    return childrenAtMost0(1, parse).firstOrNull()
}

fun <T> ParserScope.child(parse: ParserScope.() -> T): T {
    val result = childrenAtMost0(1, parse).firstOrNull()

    if (result == null) {
        val errorPerChild = parseContext.children.map { it.toElemWithLastError() }
        throw ParseError.NoMatchingChild(parseContext.origElem, errorPerChild)
    }

    return result
}

fun <T> ParserScope.children0(parse: ParserScope.() -> T): List<T> {
    return childrenAtMost0(Int.MAX_VALUE, parse)
}

fun <T> ParserScope.children(parse: ParserScope.() -> T): List<T> {
    val result = childrenAtMost0(Int.MAX_VALUE, parse)

    if (result.isEmpty()) {
        val errorPerChild = parseContext.children.map { it.toElemWithLastError() }
        throw ParseError.NoMatchingChild(parseContext.origElem, errorPerChild)
    }

    return result
}

fun ParserScope.ignoreChildrenWithName(name: String) {
    parseContext.children = parseContext.children.filter { it.elem.name != name }
}

fun ParserScope.text0(): String {
    val text = parseContext.text
    parseContext.text = null
    return text ?: ""
}
