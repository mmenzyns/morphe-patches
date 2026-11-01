package app.template.patches.alza

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.Opcode

object GodFragmentCreateViewFingerprint : Fingerprint(
    definingClass = "Lcz/alza/base/delegate/fragment/GodFragment;",
    name = "onCreateView",
    returnType = "Landroid/view/View;",
    parameters = listOf(
        "Landroid/view/LayoutInflater;",
        "Landroid/view/ViewGroup;",
        "Landroid/os/Bundle;"
    ),
    filters = listOf(
        opcode(Opcode.RETURN_OBJECT)
    )
)

object ProductPriceSectionFingerprint : Fingerprint(
    returnType = "V",
    isStatic = true,
    custom = { method, _ ->
        method.parameterTypes.size == 6 &&
            method.parameterTypes.any { it == "Lcz/alza/base/lib/product/detail/model/general/data/ProductPrices;" }
    }
)

