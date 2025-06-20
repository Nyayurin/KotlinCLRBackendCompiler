/*
   Copyright 2025 Nyayurin

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package compiler.clr.frontend

import org.jetbrains.kotlin.KtIoFileSourceFile
import org.jetbrains.kotlin.KtPsiSourceFile
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.KtVirtualFileSourceFile
import org.jetbrains.kotlin.asJava.finder.JavaElementFinder
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.PsiBasedProjectFileSearchScope
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.compiler.unregisterFinders
import org.jetbrains.kotlin.com.intellij.openapi.project.Project
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiElementFinder
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.fir.FirModuleData
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.SessionConfiguration
import org.jetbrains.kotlin.fir.java.FirJavaElementFinder
import org.jetbrains.kotlin.fir.java.FirJavaFacadeForSource
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectEnvironment
import org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope
import org.jetbrains.kotlin.load.java.createJavaClassFinder
import org.jetbrains.kotlin.load.java.structure.JavaAnnotation
import org.jetbrains.kotlin.load.kotlin.KotlinClassFinder
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.load.kotlin.VirtualFileFinderFactory
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.jvm.modules.JavaModuleResolver
import java.io.File

open class VfsBasedProjectEnvironment(
	val project: Project,
	val localFileSystem: VirtualFileSystem,
	private val getPackagePartProviderFn: (GlobalSearchScope) -> PackagePartProvider,
) : AbstractProjectEnvironment {

	override fun getKotlinClassFinder(fileSearchScope: AbstractProjectFileSearchScope): KotlinClassFinder =
		VirtualFileFinderFactory.getInstance(project).create(fileSearchScope.asPsiSearchScope())

	override fun getJavaModuleResolver(): JavaModuleResolver = object : JavaModuleResolver {
		override fun checkAccessibility(
			fileFromOurModule: VirtualFile?,
			referencedFile: VirtualFile,
			referencedPackage: FqName?,
		): JavaModuleResolver.AccessError? = null

		override fun getAnnotationsForModuleOwnerOfClass(classId: ClassId): List<JavaAnnotation>? = null
	}

	override fun getPackagePartProvider(fileSearchScope: AbstractProjectFileSearchScope): PackagePartProvider =
		getPackagePartProviderFn(fileSearchScope.asPsiSearchScope())

	@OptIn(SessionConfiguration::class)
	override fun registerAsJavaElementFinder(firSession: FirSession) {
		val psiFinderExtensionPoint = PsiElementFinder.EP.getPoint(project)
		psiFinderExtensionPoint.unregisterFinders<JavaElementFinder>()
		psiFinderExtensionPoint.unregisterFinders<FirJavaElementFinder>()

		val firJavaElementFinder = FirJavaElementFinder(firSession, project)
		firSession.register(FirJavaElementFinder::class, firJavaElementFinder)
		// see comment and TODO in KotlinCoreEnvironment.registerKotlinLightClassSupport (KT-64296)
		@Suppress("DEPRECATION")
		PsiElementFinder.EP.getPoint(project).registerExtension(firJavaElementFinder)
		Disposer.register(project) {
			psiFinderExtensionPoint.unregisterFinders<FirJavaElementFinder>()
		}
	}

	private fun List<VirtualFile>.toSearchScope(allowOutOfProjectRoots: Boolean) =
		takeIf { it.isNotEmpty() }
			?.let {
				if (allowOutOfProjectRoots) GlobalSearchScope.filesWithLibrariesScope(project, it)
				else GlobalSearchScope.filesWithoutLibrariesScope(project, it)
			}
			?: GlobalSearchScope.EMPTY_SCOPE

	override fun getSearchScopeByIoFiles(
		files: Iterable<File>,
		allowOutOfProjectRoots: Boolean,
	): AbstractProjectFileSearchScope =
		PsiBasedProjectFileSearchScope(
			files
				.mapNotNull { localFileSystem.findFileByPath(it.absolutePath) }
				.toSearchScope(allowOutOfProjectRoots)
		)

	override fun getSearchScopeBySourceFiles(
		files: Iterable<KtSourceFile>,
		allowOutOfProjectRoots: Boolean,
	): AbstractProjectFileSearchScope =
		PsiBasedProjectFileSearchScope(
			files
				.mapNotNull {
					when (it) {
						is KtPsiSourceFile -> it.psiFile.virtualFile
						is KtVirtualFileSourceFile -> it.virtualFile
						is KtIoFileSourceFile -> localFileSystem.findFileByPath(it.file.absolutePath)
						else -> null // TODO: find out whether other use cases should be supported
					}
				}
				.toSearchScope(allowOutOfProjectRoots)
		)

	override fun getSearchScopeByDirectories(directories: Iterable<File>): AbstractProjectFileSearchScope =
		PsiBasedProjectFileSearchScope(
			directories
				.mapNotNull { localFileSystem.findFileByPath(it.absolutePath) }
				.toSet()
				.takeIf { it.isNotEmpty() }
				?.let {
					KotlinToJVMBytecodeCompiler.DirectoriesScope(project, it)
				} ?: GlobalSearchScope.EMPTY_SCOPE
		)

	override fun getSearchScopeForProjectLibraries(): AbstractProjectFileSearchScope =
		PsiBasedProjectFileSearchScope(ProjectScope.getLibrariesScope(project))

	override fun getSearchScopeForProjectJavaSources(): AbstractProjectFileSearchScope =
		PsiBasedProjectFileSearchScope(TopDownAnalyzerFacadeForJVM.AllJavaSourcesInProjectScope(project))

	override fun getFirJavaFacade(
		firSession: FirSession,
		baseModuleData: FirModuleData,
		fileSearchScope: AbstractProjectFileSearchScope,
	) = FirJavaFacadeForSource(
		firSession,
		baseModuleData,
		project.createJavaClassFinder(fileSearchScope.asPsiSearchScope())
	)
}

private fun AbstractProjectFileSearchScope.asPsiSearchScope() =
	when {
		this === AbstractProjectFileSearchScope.EMPTY -> GlobalSearchScope.EMPTY_SCOPE
		this === AbstractProjectFileSearchScope.ANY -> GlobalSearchScope.notScope(GlobalSearchScope.EMPTY_SCOPE)
		else -> (this as PsiBasedProjectFileSearchScope).psiSearchScope
	}