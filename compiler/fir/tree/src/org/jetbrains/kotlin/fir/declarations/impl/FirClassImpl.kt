/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.declarations.impl

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClass
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.transformInplace
import org.jetbrains.kotlin.fir.types.FirType
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationKind
import org.jetbrains.kotlin.name.Name

open class FirClassImpl(
    session: FirSession,
    psi: PsiElement?,
    name: Name,
    visibility: Visibility,
    modality: Modality?,
    final override val classKind: ClassKind,
    final override val isInner: Boolean,
    final override val isCompanion: Boolean,
    final override val isData: Boolean
) : FirAbstractMemberDeclaration(session, psi, IrDeclarationKind.CLASS, name, visibility, modality), FirClass {
    override val superTypes = mutableListOf<FirType>()

    override val declarations = mutableListOf<FirDeclaration>()


    override fun <D> transformChildren(transformer: FirTransformer<D>, data: D): FirClass {
        typeParameters.transformInplace(transformer, data)
        superTypes.transformInplace(transformer, data)
        declarations.transformInplace(transformer, data)

        return this
    }
}