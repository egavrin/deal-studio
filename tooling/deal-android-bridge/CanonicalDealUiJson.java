package com.offlineassistant.dealtoolchain;

import deal.ui.UiModel;

/** Transitional adapter; checked IR serialization belongs to the portable UI compiler. */
final class CanonicalDealUiJson {
    private CanonicalDealUiJson() {}
    static String encode(UiModel.CheckedProgram program) {
        return deal.ui.CanonicalDealUiJson.encode(program);
    }
}
