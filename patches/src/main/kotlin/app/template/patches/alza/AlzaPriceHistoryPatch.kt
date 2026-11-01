package app.template.patches.alza

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.COMPATIBILITY_ALZA
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

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
        val method = GodFragmentCreateViewFingerprint.method

        // 1. First add the wrap call at returnIndex before return-object
        val match = GodFragmentCreateViewFingerprint.instructionMatches.first()
        val returnInstruction = match.getInstruction<OneRegisterInstruction>()
        val returnReg = returnInstruction.registerA
        val returnIndex = match.index

        method.addInstructions(
            returnIndex,
            """
                invoke-static {v$returnReg, p0}, $EXTENSION_CLASS->wrapFragmentView(Landroid/view/View;Ljava/lang/Object;)Landroid/view/View;
                move-result-object v$returnReg
            """
        )

        // 2. Add entry call at index 0 so p0 (which is untouched 'this' = GodFragment) is captured
        method.addInstructions(
            0,
            """
                invoke-static {p0}, $EXTENSION_CLASS->onFragmentCreateViewStart(Ljava/lang/Object;)V
            """
        )
    }
}

