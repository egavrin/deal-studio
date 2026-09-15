package com.offlineassistant.dealtoolchain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import deal.compiler.CompilerProtocolJson;
import deal.compiler.ConstructionRepairWorkspace;
import deal.semantic.ir.CanonicalJson;

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
        Object portioned = CanonicalDealToolchainBridge.configureConstructionPortions(
                CanonicalDealToolchainBridge.createGenerationSession(pack, "Create a counter", 6, 2));
        CanonicalDealToolchainBridge.configureHostCapabilityProfile(portioned, "android-host-effects-v1");
        String portionRequest = CanonicalDealToolchainBridge.refinementNextRequest(portioned);
        if (!portionRequest.contains("stage_constructor_calls")) {
            throw new AssertionError("Bounded construction portions were not enabled: " + portionRequest);
        }
        if (!portionRequest.contains("PlatformHostAction")) {
            throw new AssertionError("Compiler-owned host capability profile is missing: " + portionRequest);
        }
        checkStagedInspectionBatches(portioned);
        boolean rejected = false;
        try {
            CanonicalDealToolchainBridge.configureRepairProtocol(
                    CanonicalDealToolchainBridge.createGenerationSession(pack, "Create a counter", 6, 2), "unsupported-version");
        } catch (IllegalArgumentException expected) { rejected = true; }
        if (!rejected) throw new AssertionError("Unsupported protocol silently accepted");
        System.out.println("CanonicalRepairBridgeTest: default generation/refinement v2 and fail-closed negotiation passed");
    }

    private static void checkStagedInspectionBatches(Object session) {
        var constructors = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 16; i++) constructors.add(Map.of("id", "n" + i, "op", "integer", "value", i));
        String initialTicket = ConstructionRepairWorkspace.callsDigest(CanonicalJson.arr(List.of()));
        CanonicalDealToolchainBridge.refinementAcceptToolCall(session, "stage_constructor_calls",
                CompilerProtocolJson.encode(Map.of("ticket", initialTicket, "calls", constructors)));
        String ticket = ConstructionRepairWorkspace.callsDigest(CompilerProtocolJson.requireArray(
                CompilerProtocolJson.decode(CompilerProtocolJson.encode(constructors)), "calls"));
        var reads = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < 16; i++) reads.add(Map.of("name", "inspect_staged_call",
                "arguments", Map.of("ticket", ticket, "target", "n" + i)));
        String before = CanonicalDealToolchainBridge.refinementNextRequest(session);
        assertBatch(session, reads, true);
        var tooMany = new ArrayList<>(reads); tooMany.add(reads.getFirst());
        assertBatch(session, tooMany, false);
        assertBatch(session, List.of(reads.getFirst(), Map.of("name", "finish_constructor_calls",
                "arguments", Map.of("ticket", ticket))), false);
        assertBatch(session, List.of(Map.of("name", "inspect_staged_call",
                "arguments", Map.of("ticket", initialTicket, "target", "n0"))), false);
        assertBatch(session, List.of(Map.of("name", "not_issued", "arguments", Map.of())), false);
        if (!before.equals(CanonicalDealToolchainBridge.refinementNextRequest(session))) {
            throw new AssertionError("Batch prevalidation mutated compiler state or issued grants");
        }
        CanonicalDealToolchainBridge.refinementAcceptToolCalls(session, CompilerProtocolJson.encode(reads));
        assertBatch(session, reads, false); // Read grant was consumed; only targeted writes are now issued.
    }

    private static void assertBatch(Object session, List<? extends Map<String, ?>> calls, boolean expected) {
        String result = CanonicalDealToolchainBridge.refinementValidateToolCalls(session, CompilerProtocolJson.encode(calls));
        if (result.contains("\"valid\":true") != expected) throw new AssertionError(result);
    }

    private static void assertV2(String request) {
        if (!request.contains("\"repairProtocol\":\"repair-workspace-v2\"")) throw new AssertionError(request);
        if (!request.contains("\"constructionProtocol\":\"compiler-construction-v1\"")) throw new AssertionError(request);
    }
}
