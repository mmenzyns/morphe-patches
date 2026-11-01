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
        val match = GodFragmentCreateViewFingerprint.instructionMatches.first()
        val returnInstruction = match.getInstruction<OneRegisterInstruction>()
        val returnReg = returnInstruction.registerA
        val returnIndex = match.index

        GodFragmentCreateViewFingerprint.method.addInstructions(
            returnIndex,
            """
                invoke-static {v$returnReg, p0}, $EXTENSION_CLASS->wrapFragmentView(Landroid/view/View;Ljava/lang/Object;)Landroid/view/View;
                move-result-object v$returnReg
            """
        )
    }
}
