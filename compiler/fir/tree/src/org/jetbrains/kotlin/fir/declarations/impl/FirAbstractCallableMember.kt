/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirCallableMember
import org.jetbrains.kotlin.fir.transformSingle
import org.jetbrains.kotlin.fir.types.FirType
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationKind
import org.jetbrains.kotlin.name.Name

abstract class FirAbstractCallableMember(
    session: FirSession,
    psi: PsiElement?,
    declarationKind: IrDeclarationKind,
    name: Name,
    visibility: Visibility,
    modality: Modality?,
    final override val isOverride: Boolean,
    final override var receiverType: FirType?,
    final override var returnType: FirType
) : FirAbstractMemberDeclaration(session, psi, declarationKind, name, visibility, modality), FirCallableMember {

    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirElement {
        receiverType = receiverType?.transformSingle(transformer, data)
        returnType = returnType.transformSingle(transformer, data)

        return this
    }
}