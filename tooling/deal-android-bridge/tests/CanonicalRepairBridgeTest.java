package com.offlineassistant.dealtoolchain;

/** Exercises the exact factories called by Studio, without a provider or Android runtime. */
public final class CanonicalRepairBridgeTest {
    public static void main(String[] args) {
        String pack = """
                pack version "test";
                export class TextProps { value: string; }
                export component Text(props: TextProps): View;
                """;
        String deal = """
                export class AppState { title: string = "Ready"; }
                export function initialState(): AppState { return {title: "Ready"}; }
                """;
        String ui = """
                import * as app from "./app.deal";
                import * as ui from "./platform-ui.dealui-pack";
                // @ui-root
                export view App(state: app.AppState): View { ui.Text(value: state.title) }
                """;
        Object generation = CanonicalDealToolchainBridge.createGenerationSession(pack, "Create a counter", 6, 2);
        assertV2(CanonicalDealToolchainBridge.refinementNextRequest(generation));
        Object refinement = CanonicalDealToolchainBridge.createRefinementSession(deal, ui, pack, "Change the title", 6, 2);
        assertV2(CanonicalDealToolchainBridge.refinementNextRequest(refinement));
        Object configured = CanonicalDealToolchainBridge.createGenerationSessionWithReasoning(pack, "Create a counter", 6, 2, "none", "none");
        String request = CanonicalDealToolchainBridge.refinementNextRequest(configured);
        assertV2(request);
        if (!request.contains("\"reasoningEffort\":\"none\"")) throw new AssertionError("Reasoning profile changed");
        boolean rejected = false;
        try {
            CanonicalDealToolchainBridge.configureRepairProtocol(
                    CanonicalDealToolchainBridge.createGenerationSession(pack, "Create a counter", 6, 2), "unsupported-version");
        } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Unsupported protocol silently accepted");
        System.out.println("CanonicalRepairBridgeTest: default generation/refinement v2 and fail-closed negotiation passed");
    }

    private static void assertV2(String request) {
        if (!request.contains("\"repairProtocol\":\"repair-workspace-v2\"")) throw new AssertionError(request);
        if (!request.contains("\"constructionProtocol\":\"compiler-construction-v1\"")) throw new AssertionError(request);
    }
}
