package app.template.patches.alza

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_ALZA
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
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
        val createViewMethod = GodFragmentCreateViewFingerprint.method

        // 1. Add the wrap call at returnIndex before return-object
        val match = GodFragmentCreateViewFingerprint.instructionMatches.first()
        val returnInstruction = match.getInstruction<OneRegisterInstruction>()
        val returnReg = returnInstruction.registerA
        val returnIndex = match.index

        createViewMethod.addInstructions(
            returnIndex,
            """
                invoke-static {v$returnReg, p0}, $EXTENSION_CLASS->wrapFragmentView(Landroid/view/View;Ljava/lang/Object;)Landroid/view/View;
                move-result-object v$returnReg
            """
        )

        // 2. Add entry call at index 0 so p0 (which is untouched 'this' = GodFragment) is captured
        createViewMethod.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION_CLASS->onFragmentCreateViewStart(Ljava/lang/Object;)V
            """
        )

        // 3. Hook w4m.i (Product detail price section composable) to inject inline price chart
        val priceMethod = ProductPriceSectionFingerprint.method

        // A. Capture price model at method entry (index 0)
        // w4m.i has 38 registers, so p1 (v33) requires /range
        priceMethod.addInstructions(
            0,
            """
                invoke-static/range {p1 .. p1}, $EXTENSION_CLASS->onPriceModelCaptured(Ljava/lang/Object;)V
            """
        )

        // B. Emit Compose spacer inside the price Column (right at getPriceWithoutVatVisible)
        val instructions = priceMethod.implementation!!.instructions
        val vatIndex = instructions.indexOfFirst { instruction ->
            (instruction as? ReferenceInstruction)?.reference?.toString()?.contains("getPriceWithoutVatVisible") == true
        }

        val targetIndex = if (vatIndex != -1) {
            vatIndex
        } else {
            val priceIndex = instructions.indexOfFirst { instruction ->
                (instruction as? ReferenceInstruction)?.reference?.toString()?.contains("getPrice") == true
            }
            if (priceIndex != -1) priceIndex + 1 else 1
        }

        // p4 (v36) requires /range
        priceMethod.addInstructions(
            targetIndex,
            """
                invoke-static/range {p4 .. p4}, $EXTENSION_CLASS->onPriceSectionCompose(Ljava/lang/Object;)V
            """
        )
    }
}

