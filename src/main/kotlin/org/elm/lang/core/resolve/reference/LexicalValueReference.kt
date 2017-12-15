package org.elm.lang.core.resolve.reference

import org.elm.lang.core.psi.ElmNamedElement
import org.elm.lang.core.psi.ElmPsiElement
import org.elm.lang.core.resolve.ElmReferenceElement
import org.elm.lang.core.resolve.scope.ExpressionScope

/**
 * Reference to a value in lexical expression scope
 */
class LexicalValueReference(element: ElmReferenceElement): ElmReferenceBase<ElmReferenceElement>(element) {

    override fun getVariants(): Array<ElmNamedElement> =
            emptyArray()

    override fun resolve(): ElmPsiElement? =
            getCandidates().find { it.name == element.referenceName }

    private fun getCandidates(): Array<ElmNamedElement> {
        return ExpressionScope(element).getVisibleValues().toTypedArray()
    }
}