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

package compiler.clr.backend.lower

import compiler.clr.backend.ClrBackendContext
import org.jetbrains.kotlin.backend.common.FileLoweringPass
import org.jetbrains.kotlin.backend.common.phaser.PhaseDescription
import org.jetbrains.kotlin.backend.jvm.classNameOverride
import org.jetbrains.kotlin.backend.jvm.createJvmFileFacadeClass
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.IrMemberAccessExpression
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.createThisReceiverParameter
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.load.kotlin.FacadeClassSource

@PhaseDescription(name = "ExternalPackageParentPatcherLowering")
internal class ExternalPackageParentPatcherLowering(val context: ClrBackendContext) : FileLoweringPass {
	override fun lower(irFile: IrFile) {
		irFile.acceptVoid(Visitor())
	}

	private inner class Visitor : IrElementVisitorVoid {
		override fun visitElement(element: IrElement) {
			element.acceptChildrenVoid(this)
		}

		@OptIn(UnsafeDuringIrConstructionAPI::class)
		override fun visitMemberAccess(expression: IrMemberAccessExpression<*>) {
			visitElement(expression)
			val callee = expression.symbol.owner as? IrMemberWithContainerSource ?: return
			if (callee.parent is IrExternalPackageFragment) {
				val parentClass = generateOrGetFacadeClass(callee) ?: return
				parentClass.parent = callee.parent
				callee.parent = parentClass
				when (callee) {
					is IrProperty -> handleProperty(callee, parentClass)
					is IrSimpleFunction -> callee.correspondingPropertySymbol?.owner?.let {
						handleProperty(
							it,
							parentClass
						)
					}
				}
			}
		}

		private fun generateOrGetFacadeClass(declaration: IrMemberWithContainerSource): IrClass? {
			val deserializedSource = declaration.containerSource ?: return null
			if (deserializedSource !is FacadeClassSource) return null
			val facadeName = deserializedSource.facadeClassName ?: deserializedSource.className
			return createJvmFileFacadeClass(
				if (deserializedSource.facadeClassName != null) IrDeclarationOrigin.JVM_MULTIFILE_CLASS else IrDeclarationOrigin.FILE_CLASS,
				facadeName.fqNameForTopLevelClassMaybeWithDollars.shortName(),
				deserializedSource,
				deserializeIr = { irClass -> deserializeTopLevelClass(irClass) }
			).also {
				it.createThisReceiverParameter()
				it.classNameOverride = facadeName
			}
		}

		private fun deserializeTopLevelClass(irClass: IrClass): Boolean {
			/*return context.irDeserializer.deserializeTopLevelClass(
				irClass, context.irBuiltIns, context.symbolTable, context.irProviders, context.generatorExtensions
			)*/
			TODO()
		}

		private fun handleProperty(property: IrProperty, newParent: IrClass) {
			property.parent = newParent
			property.getter?.parent = newParent
			property.setter?.parent = newParent
			property.backingField?.parent = newParent
		}
	}
}