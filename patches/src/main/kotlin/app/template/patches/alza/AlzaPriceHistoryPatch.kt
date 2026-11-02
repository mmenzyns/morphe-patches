package app.template.patches.alza

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_ALZA
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction

private const val EXTENSION_CLASS = "Lapp/template/extension/alza/AlzaPriceChartInjector;"

@Suppress("unused")
val alzaPriceHistoryPatch = bytecodePatch(
    name = "Alza Hlídač shopů Price History",
    description = "Displays inline price history chart from Hlídač shopů in product detail screen.",
    default = true
) {
    compatibleWith(COMPATIBILITY_ALZA)

    extendWith("extensions/extension.mpe")

    execute {
        // 1. Hook ProductDetailParams constructors to capture productId and productInfo cleanly
        val withIdConstructor = ProductDetailParamsWithIdFingerprint.method
        withIdConstructor.addInstructions(
            0,
            """
                invoke-static {p1, p4}, $EXTENSION_CLASS->onProductDetailParams(ILjava/lang/Object;)V
            """
        )

        val withMetaConstructor = ProductDetailParamsWithMetaFingerprint.method
        withMetaConstructor.addInstructions(
            0,
            """
                invoke-static {p1, p4}, $EXTENSION_CLASS->onProductDetailParamsWithMeta(Ljava/lang/Object;Ljava/lang/Object;)V
            """
        )

        // 2. Light hook on GodFragment.onCreateView entry for fallback tracking (NO View wrapping)
        val createViewMethod = GodFragmentCreateViewFingerprint.method
        createViewMethod.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION_CLASS->onFragmentCreateViewStart(Ljava/lang/Object;)V
            """
        )

        // 3. Resolve dynamic Compose AndroidView binding via fingerprints (no hardcoded obfuscated names)
        val androidViewClass = AndroidViewFingerprint.method.definingClass.removePrefix("L").removeSuffix(";").replace("/", ".")
        val androidViewMethod = AndroidViewFingerprint.method.name
        val function1Class = AndroidViewFingerprint.method.parameterTypes[0].toString().removePrefix("L").removeSuffix(";").replace("/", ".")

        // 4. Hook PriceSectionContentFingerprint (f5m.invoke) - the FlowRow price section composable
        // This injects the chart as the 3rd child of FlowRow (after both Price and Home Credit / installments)
        // so Price and Home Credit remain side-by-side on Line 1, and the chart wraps to Line 2 underneath both.
        val contentMethod = PriceSectionContentFingerprint.method

        // Pass discovered AndroidView bindings at method entry
        contentMethod.addInstructions(
            0,
            """
                const-string v0, "$androidViewClass"
                const-string v1, "$androidViewMethod"
                const-string v2, "$function1Class"
                invoke-static {v0, v1, v2}, $EXTENSION_CLASS->initComposeBindings(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
            """
        )

        val instructions = contentMethod.implementation!!.instructions
        val altIndex = instructions.indexOfFirst { instruction ->
            (instruction as? ReferenceInstruction)?.reference?.toString()?.contains("getAlternativePrice") == true
        }

        val wIndex = if (altIndex != -1) {
            val idx = instructions.drop(altIndex).indexOfFirst { instruction ->
                (instruction as? ReferenceInstruction)?.reference?.toString()?.contains(";->W()V") == true
            }
            if (idx != -1) altIndex + idx else instructions.size
        } else {
            instructions.size
        }

        // Find all q(Z)V (endReplaceableGroup) calls between getAlternativePrice and W()
        // These correspond to the ends of both the 'with alternative price' and 'without alternative price' branches.
        val qIndices = instructions.take(wIndex).mapIndexedNotNull { idx, instruction ->
            if (idx > altIndex) {
                val ref = (instruction as? ReferenceInstruction)?.reference?.toString() ?: ""
                if (ref.contains(";->q(Z)V") || ref.contains("endReplaceableGroup") || ref.contains("endNode")) {
                    idx
                } else null
            } else null
        }

        // Inject right after each branch's q(Z)V (before the branch's goto).
        // Sort descending so earlier insertions don't shift later indices.
        // p2 is the Composer parameter in invoke(RowScope, Composer, Integer)
        for (qIndex in qIndices.sortedDescending()) {
            contentMethod.addInstructions(
                qIndex + 1,
                """
                    invoke-static/range {p2 .. p2}, $EXTENSION_CLASS->onPriceSectionCompose(Ljava/lang/Object;)V
                """
            )
        }
    }
}
