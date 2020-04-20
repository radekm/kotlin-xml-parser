package cz.radekm.xml.parser

import cz.radekm.xml.Elem

data class ElemWithError(val elem: Elem, val error: ParseError)

data class ElemWithErrors(val elem: Elem, val errors: List<ParseError>) {
    fun withAdditionalErrors(e: ParseError): ElemWithErrors = copy(errors = errors + e)

    fun toElemWithLastError() = ElemWithError(elem, errors.last())
}

/**
 * `attrs`, `children`, `text` contain items which remain to be parsed.
 * Additionally for improved error reporting `children` contains reasons
 * why parsers which we tried so far failed.
 * So at the end if some children remain we know why they cannot be parsed.
 */
data class ParseContext(
        val origElem: Elem,
        val name: String,
        var attrs: Map<String, String>,
        var children: List<ElemWithErrors>,
        var text: String?
) {
    companion object {
        fun forElem(el: Elem) = ParseContext(
                origElem = el,
                name = el.name,
                attrs = el.attrs,
                children = el.children.map { ElemWithErrors(it, emptyList()) },
                text = el.text
        )
    }
}

@DslMarker
annotation class ParserScopeMarker

/**
 * Receiver for parsing functions.
 *
 * Parsing function must remove parsed attributes, child elements
 * and text from `parseContext` if it succeeds (ie. returns normally).
 *
 * `parseContext` may or may not be updated if parsing function
 * fails (ie. throws an exception). It's up to parsing function.
 */
@ParserScopeMarker
interface ParserScope {
    val parseContext: ParseContext

    companion object {
        fun forElem(el: Elem) = object : ParserScope {
            override val parseContext = ParseContext.forElem(el)
        }
    }
}

fun checkNoRemainingItems(parseContext: ParseContext) {
    if (parseContext.attrs.isNotEmpty() || parseContext.children.isNotEmpty() || parseContext.text != null) {
        throw ParseError.RemainingItems(
                parseContext.origElem,
                parseContext.attrs,
                parseContext.children,
                parseContext.text
        )
    }
}

/**
 * Tries to `parse` children of the current element.
 * Returns list which contains `atMost >= 1` parsed children.
 * Always succeeds.
 *
 * Additionally if the returned list is empty it means
 * that it tried to `parse` every child element but it failed.
 * So `parseContext.children` contains the same children
 * but each with one extra error.
 */
fun <T> ParserScope.childrenAtMost0(atMost: Int, parse: ParserScope.() -> T): List<T> {
    require(atMost >= 1)

    // Children which weren't parsed.
    // Either because `parse` failed or because
    // `atMost` children were already parsed successfully.
    val remainingChildren = mutableListOf<ElemWithErrors>()

    // Result of this method.
    // `parseContext.children` are partitioned between `remainingChildren` and `result`.
    val result = mutableListOf<T>()

    for (ch in parseContext.children) {
        // `atMost` children were already parsed successfully.
        if (result.size == atMost) {
            remainingChildren += ch
            continue
        }

        // Try to parse `ch.elem`.
        try {
            val scope = ParserScope.forElem(ch.elem)
            val parsed = scope.parse()
            checkNoRemainingItems(scope.parseContext) // Everything must be parsed.

            result += parsed
        } catch (e: ParseError) {
            // `scope.parse` threw exception because element cannot be parsed.
            // or `checkNoRemainingItems` threw.
            // No matter which one we have to record the error.
            remainingChildren += ch.withAdditionalErrors(e)
        }
    }

    // If `result` is defined then we successfully parsed `ch.elem` and
    // we need to remove it from `parseContext.children` and record
    // errors we got when trying to parse children before `ch.elem`.
    //
    // If `result` is not defined then we only record errors
    // we got when trying to parse the children.
    // This step is not necessary but it's useful when implementing
    // `child` and `children` - since they can extract `errorPerChild`
    // for `ParseError.NoMatchingChild` from `parseContext.children`.
    parseContext.children = remainingChildren

    return result
}

/**
 * `originalParseContext` is modified after a variant succeeds
 * (this implies that `oneOf` succeeds).
 * So if all variants fail `originalParseContext` is not touched.
 *
 * `errorPerVariant` is used iff all variants fail.
 */
data class VariantParseContext<T>(
        val originalParseContext: ParseContext,
        var result: T?,
        var errorPerVariant: List<ParseError>
)

@ParserScopeMarker
interface VariantParserScope<T> {
    val variantParseContext: VariantParseContext<T>
}

fun <T> ParserScope.oneOf(blockWithVariants: VariantParserScope<T>.() -> Unit): T {
    val scope = object : VariantParserScope<T> {
        override val variantParseContext = VariantParseContext<T>(
                originalParseContext = this@oneOf.parseContext,
                result = null,
                errorPerVariant = emptyList()
        )
    }

    // `scope.blockWithVariants` doesn't throw `ParseError`
    // (each `variant` catches it and it should not contain other calls).
    scope.blockWithVariants()
    val result = scope.variantParseContext.result

    if (result == null) {
        // All variants failed.
        throw ParseError.NoMatchingVariant(
                scope.variantParseContext.originalParseContext.origElem,
                scope.variantParseContext.errorPerVariant
        )
    } else {
        // `parseContext` was already modified by successful `variant`.
        return result
    }
}

fun <T> VariantParserScope<T>.variant(parse: ParserScope.() -> T) {
    if (variantParseContext.result != null)
        return

    try {
        val scope = object : ParserScope {
            // We have to copy `originalParseContext` because `scope.parse`
            // may modify it even if it fails.
            override val parseContext = this@variant.variantParseContext.originalParseContext.copy()
        }

        variantParseContext.result = scope.parse()

        // Success we have to modify the `originalParseContext`.
        variantParseContext.originalParseContext.let {
            it.attrs = scope.parseContext.attrs
            it.children = scope.parseContext.children
            it.text = scope.parseContext.text
        }
    } catch (e: ParseError) {
        variantParseContext.errorPerVariant += e
    }
}

// We use `Error` suffix because it's shorter than `Exception`.
// I know that `Error` should be used for serious things but don't care.
abstract class ParseError(message: String?) : RuntimeException(message) {
    // TODO Consider using `parseContext` instead of `origElem`.
    //      Since remaining items are not obvious from `origElem`.
    //      We need some evidence which proves that `parseContext`
    //      is significantly better.

    class RemainingItems(
            val origElem: Elem,
            val attrs: Map<String, String>,
            val children: List<ElemWithErrors>,
            val text: String?
    ) : ParseError(null) {
        override fun formattedLines(): List<String> {
            val stats = listOfNotNull(
                    if (attrs.isEmpty()) null else "${attrs.size} attrs",
                    if (children.isEmpty()) null else "${children.size} children",
                    if (text == null) null else "text"
            ).joinToString(", ")
            val result = mutableListOf(
                    "Remaining items ($stats)",
                    origElem.formattedIntro()
            )
            if (attrs.isNotEmpty()) {
                result += "  Remaining attributes:"
                result += "  ${Elem.formattedAttrs(attrs)}"
            }
            children.forEachIndexed { idx, (ch, es) ->
                result += "  Remaining child $idx:"
                result += "  ${ch.formattedIntro()}"
                es.forEachIndexed { idx, e ->
                    result += "    Error $idx"
                    e.formattedLines().forEach { line -> result += "      $line" }
                }
            }
            if (text != null) {
                result += "  Remaining text:"
                result += "  ${Elem.formattedText(text)}"
            }
            return result
        }
    }
    class NoMatchingChild(
            val origElem: Elem,
            val errorPerChild: List<ElemWithError>
    ) : ParseError(null) {
        override fun formattedLines(): List<String> {
            val result = mutableListOf(
                    "No matching child (${errorPerChild.size} tried)",
                    origElem.formattedIntro()
            )
            errorPerChild.forEachIndexed { idx, (elem, e) ->
                result += "  Child $idx:"
                result += "  ${elem.formattedIntro()}"
                e.formattedLines().forEach { line -> result += "    $line" }
            }
            return result
        }
    }
    class NoMatchingVariant(
            val origElem: Elem,
            val errorPerVariant: List<ParseError>
    ) : ParseError(null) {
        override fun formattedLines(): List<String> {
            val result = mutableListOf(
                    "No matching variant (${errorPerVariant.size} tried)",
                    origElem.formattedIntro()
            )
            errorPerVariant.forEachIndexed { idx, e ->
                result += "  Variant $idx:"
                e.formattedLines().forEach { line -> result += "    $line" }
            }
            return result
        }
    }
    class Other(message: String) : ParseError(message) {
        override fun formattedLines(): List<String> {
            return listOf(message!!)
        }
    }

    // TODO How to make this informative?
    //      It would be nice to name parsers so user has idea what is happening.
    abstract fun formattedLines(): List<String>
}

object Parser {
    fun <T> run(el: Elem, parse: ParserScope.() -> T): T {
        val scope = ParserScope.forElem(el)
        val parsed = scope.parse()
        checkNoRemainingItems(scope.parseContext)
        return parsed
    }
}
