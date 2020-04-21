package cz.radekm.xml.xsd

import cz.radekm.xml.parser.*

data class Choice(val possibleElements: List<Element>)

fun ParserScope.choice(): Choice {
    name("choice")
    return Choice(children { element() })
}
