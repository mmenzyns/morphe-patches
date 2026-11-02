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

        // 4. Hook w4m.i (Product detail price section composable)
        val priceMethod = ProductPriceSectionFingerprint.method

        // A. Pass discovered AndroidView bindings at method entry
        priceMethod.addInstructions(
            0,
            """
                const-string v0, "$androidViewClass"
                const-string v1, "$androidViewMethod"
                const-string v2, "$function1Class"
                invoke-static {v0, v1, v2}, $EXTENSION_CLASS->initComposeBindings(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
            """
        )

        // B. Inject onPriceSectionCompose at the very end of the price Column
        // (the last .q [endReplaceableGroup/endNode] before .W [skipToGroupEnd])
        val instructions = priceMethod.implementation!!.instructions
        val skipIndex = instructions.indexOfFirst { instruction ->
            val ref = (instruction as? ReferenceInstruction)?.reference?.toString() ?: ""
            ref.contains(";->W()V") || ref.contains("skipToGroupEnd")
        }

        val targetIndex = if (skipIndex != -1) {
            val lastQBeforeSkip = instructions.take(skipIndex).indexOfLast { instruction ->
                val ref = (instruction as? ReferenceInstruction)?.reference?.toString() ?: ""
                ref.contains(";->q(Z)V") || ref.contains("endReplaceableGroup") || ref.contains("endNode")
            }
            if (lastQBeforeSkip != -1) lastQBeforeSkip else skipIndex
        } else {
            instructions.size - 1
        }

        // p4 (v36) is Composer
        priceMethod.addInstructions(
            targetIndex,
            """
                invoke-static/range {p4 .. p4}, $EXTENSION_CLASS->onPriceSectionCompose(Ljava/lang/Object;)V
            """
        )
    }
}
