/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.descriptors

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.FirRenderer
import org.jetbrains.kotlin.fir.declarations.*
import org.jetbrains.kotlin.fir.resolve.FirProvider
import org.jetbrains.kotlin.fir.resolve.FirTypeResolver
import org.jetbrains.kotlin.fir.scopes.impl.*
import org.jetbrains.kotlin.fir.transformSingle
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.fir.types.impl.FirResolvedTypeImpl
import org.jetbrains.kotlin.fir.visitors.CompositeTransformResult
import org.jetbrains.kotlin.fir.visitors.FirTransformer
import org.jetbrains.kotlin.fir.visitors.FirVisitor
import org.jetbrains.kotlin.fir.visitors.compose
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

fun FirElement.render(): String = buildString { this@render.accept(FirRenderer(this)) }

class FirTypeResolveTransformer(val superTypesOnly: Boolean = false) : FirTransformer<Nothing?>() {
    override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
        return if (superTypesOnly) {
            element.compose()
        } else {
            (element.transformChildren(this, data) as E).compose()
        }
    }

    lateinit var scope: FirCompositeScope
    lateinit var packageFqName: FqName
    private var classLikeName: FqName = FqName.ROOT

    override fun transformFile(file: FirFile, data: Nothing?): CompositeTransformResult<FirFile> {
        scope = FirCompositeScope(
            mutableListOf(
                FirExplicitImportingScope(file.imports),
                FirSelfImportingScope(file.packageFqName, file.session)
            )
        )
        packageFqName = file.packageFqName
        return super.transformFile(file, data)
    }

    private fun lookupSuperTypes(klass: FirClass): List<ClassId> {
        val superTypesBuilder = SuperClassHierarchyBuilder()
        klass.superTypes.any { it.accept(superTypesBuilder, null) }
        return superTypesBuilder.classes
    }

    private fun resolveSuperTypesAndExpansions(element: FirMemberDeclaration) {
        try {
            element.transformChildren(SuperTypeResolver(), null)
        } catch (e: Exception) {
            class SuperTypeResolveException(cause: Exception) : Exception(element.render(), cause)
            throw SuperTypeResolveException(e)
        }
    }

    override fun transformClass(klass: FirClass, data: Nothing?): CompositeTransformResult<FirDeclaration> {
        classLikeName = classLikeName.child(klass.name)
        val classId = ClassId(packageFqName, classLikeName, false)
        scope = FirCompositeScope(mutableListOf(scope))
        scope.scopes += FirClassLikeTypeParameterScope(classId, klass.session)
        resolveSuperTypesAndExpansions(klass)

        scope.scopes += FirNestedClassifierScope(classId, klass.session)
        val superTypeScopes = lookupSuperTypes(klass).map { FirNestedClassifierScope(it, klass.session) }
        scope.scopes.addAll(superTypeScopes)
        val result = super.transformClass(klass, data)
        scope = scope.scopes[0] as FirCompositeScope
        classLikeName = classLikeName.parent()

        return result
    }

    override fun transformTypeAlias(typeAlias: FirTypeAlias, data: Nothing?): CompositeTransformResult<FirDeclaration> {
        classLikeName = classLikeName.child(typeAlias.name)
        val classId = ClassId(packageFqName, classLikeName, false)
        scope = FirCompositeScope(mutableListOf(scope))
        scope.scopes += FirClassLikeTypeParameterScope(classId, typeAlias.session)

        resolveSuperTypesAndExpansions(typeAlias)

        scope = scope.scopes[0] as FirCompositeScope
        classLikeName = classLikeName.parent()
        return super.transformTypeAlias(typeAlias, data)
    }

    override fun transformType(type: FirType, data: Nothing?): CompositeTransformResult<FirType> {
        val typeResolver = FirTypeResolver.getInstance(type.session)
        type.transformChildren(this, data)
        return FirResolvedTypeImpl(
            type.session,
            type.psi,
            typeResolver.resolveType(type, scope),
            false,
            type.annotations
        ).compose()
    }

    override fun transformResolvedType(resolvedType: FirResolvedType, data: Nothing?): CompositeTransformResult<FirType> {
        return resolvedType.compose()
    }


    private inner class SuperTypeResolver : FirTransformer<Nothing?>() {
        override fun <E : FirElement> transformElement(element: E, data: Nothing?): CompositeTransformResult<E> {
            return element.compose()
        }

        override fun transformType(type: FirType, data: Nothing?): CompositeTransformResult<FirType> {
            val transformedType = this@FirTypeResolveTransformer.transformType(type, data).single as FirResolvedType

            val classId = (transformedType.type as? ConeClassLikeType)?.symbol?.classId ?: return transformedType.compose()
            val firProvider = FirProvider.getInstance(transformedType.session)

            val classes = generateSequence(classId) { it.outerClassId }.toList()

            val transformer = FirTypeResolveTransformer(superTypesOnly = true)

            val file = firProvider.getFirClassifierContainerFile(classes.last())

            file.transformSingle(transformer, null)

            classes.forEach {
                firProvider.getFirClassifierByFqName(it)!!.transformSingle(transformer, data)
            }

            return transformedType.compose()
        }
    }

    private inner class SuperClassHierarchyBuilder : FirVisitor<Boolean, Nothing?>() {

        override fun visitElement(element: FirElement, data: Nothing?): Boolean {
            return false
        }

        private tailrec fun ConeClassLikeType.computePartialExpansion(): ClassId {
            return when (this) {
                !is ConeAbbreviatedType -> this.symbol.classId
                else -> this.directExpansion.computePartialExpansion()
            }
        }

        val classes = mutableListOf<ClassId>()

        override fun visitResolvedType(resolvedType: FirResolvedType, data: Nothing?): Boolean {
            val provider = FirProvider.getInstance(resolvedType.session)
            val targetClassId = resolvedType.coneTypeSafe<ConeClassLikeType>()?.computePartialExpansion() ?: return false
            val classifier = provider.getFirClassifierByFqName(targetClassId)!!
            when (classifier) {
                is FirClass -> {
                    if (classifier.classKind == ClassKind.CLASS) {
                        classes += targetClassId
                        classifier.superTypes.any { it.accept(this, data) }
                    }
                }
                is FirTypeAlias -> classifier.expandedType.accept(this, data)
            }
            return true
        }

    }
}