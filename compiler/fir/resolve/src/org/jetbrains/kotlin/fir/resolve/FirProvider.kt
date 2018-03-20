/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.resolve

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirMemberDeclaration
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.service
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

interface FirProvider {
    fun getFirClassifierByFqName(fqName: ClassId): FirMemberDeclaration?

    fun getFirTypeParameterByFqName(fqName: ClassId, parameterName: Name): FirTypeParameter?

    fun getFirClassifierContainerFile(fqName: ClassId): FirFile

    companion object {
        fun getInstance(session: FirSession): FirProvider = session.service()
    }

    fun getFirFilesByPackage(fqName: FqName): List<FirFile>
}