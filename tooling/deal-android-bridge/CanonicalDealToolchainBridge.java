package com.offlineassistant.dealtoolchain;

import deal.checker.CheckResult;
import deal.checker.ModuleResolver;
import deal.checker.NameResolver;
import deal.checker.Symbol;
import deal.checker.SymbolTable;
import deal.checker.TypeChecker;
import deal.compiler.CompilerProtocol.SemanticId;
import deal.compiler.CompilerProtocolJson;
import deal.compiler.DealCompilerWorkspace;
import deal.diagnostics.CompilerDiagnostic;
import deal.lexer.LexResult;
import deal.lexer.Lexer;
import deal.module.ModuleShapeValidator;
import deal.parser.ParseResult;
import deal.parser.Parser;
import deal.types.Type;
import deal.ui.UiChecker;
import deal.ui.CanonicalCompiler;
import deal.ui.UiDiagnostic;
import deal.ui.UiIrDumper;
import deal.ui.UiModel;
import deal.ui.UiParser;
import deal.ui.UiCompilerWorkspace;
import deal.semantic.ir.CanonicalJson;
import streaming.compiler.CanonicalRefinementSession;
import streaming.compiler.DirectRawGenerationExecutor;
import streaming.compiler.SurprisePromptGenerator;
import streaming.compiler.UiFirstLiveBridge;

import java.util.function.Consumer;
import streaming.compiler.UiFirstGenerationExecutor;
import streaming.compiler.UiFirstReplayFixture;

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Android reflection boundary for the pinned production Deal and Deal UI frontends. */
public final class CanonicalDealToolchainBridge {
    private static final Path DEAL_FILE = Path.of("/generated/app.deal");
    private static final Path UI_FILE = Path.of("/generated/app.dealui");
    private static final Path PACK_FILE = Path.of("/generated/platform-ui.dealui-pack");
    private static final String PACK_SPECIFIER = "./platform-ui.dealui-pack";
    private static volatile DirectRawGenerationExecutor activeDirectGeneration;
    private static volatile SurprisePromptGenerator activeSurprisePrompt;
    /**
     * Compiler-visible declarations for the small, deterministic host surface implemented by
     * {@link CanonicalDealRuntime}. They are appended so diagnostics keep the generated source's
     * original line numbers. Generated code must not declare names with the platform prefix.
     */
    private static final String GENERATED_APP_PRELUDE = """

            function platformIntText(value: int): string { return ""; }
            function platformNumberText(value: number): string { return ""; }
            function platformPad2(value: int): string { return ""; }
            function platformMinInt(left: int, right: int): int { return left; }
            function platformMaxInt(left: int, right: int): int { return left; }
            function platformAbsInt(value: int): int { return value; }
            function platformClampInt(value: int, low: int, high: int): int { return value; }
            """;

    private CanonicalDealToolchainBridge() {}

    /** Direct one-file DeepSeek generation; only a compiler-accepted canonical pair is returned. */
    public static String runDirectRawGeneration(
            String packSource, String originalUserRequest, String legalCapabilitiesJson, String deepSeekApiKey) {
        DirectRawGenerationExecutor executor = new DirectRawGenerationExecutor();
        activeDirectGeneration = executor;
        try {
            return executor.run(packSource, originalUserRequest, legalCapabilitiesJson, deepSeekApiKey);
        } finally {
            if (activeDirectGeneration == executor) activeDirectGeneration = null;
        }
    }

    public static void cancelDirectRawGeneration() {
        DirectRawGenerationExecutor executor = activeDirectGeneration;
        if (executor != null) executor.cancel();
    }

    /** DeepSeek-authored plain request for the product's Surprise Me entrypoint. */
    public static String runSurprisePromptGeneration(String localeLanguageTag, String deepSeekApiKey) {
        SurprisePromptGenerator generator = new SurprisePromptGenerator();
        activeSurprisePrompt = generator;
        try {
            return generator.run(localeLanguageTag, deepSeekApiKey);
        } finally {
            if (activeSurprisePrompt == generator) activeSurprisePrompt = null;
        }
    }

    public static void cancelSurprisePromptGeneration() {
        SurprisePromptGenerator generator = activeSurprisePrompt;
        if (generator != null) generator.cancel();
    }

    /** Stateless canonical graph inspection for streaming-compiler clients. */
    public static String inspectCanonicalApp(
            String dealSource,
            String dealUiSource,
            String packSource) {
        return CompilerProtocolJson.encode(CanonicalCompiler.inspectCanonicalApp(
                dealSource, dealUiSource, packSource, PACK_SPECIFIER));
    }

    /** Applies one compiler-owned DEAL transaction and returns canonical protocol JSON. */
    public static String applyDealChange(
            String source,
            String baseDigest,
            String operationsJson) {
        return CompilerProtocolJson.encode(CanonicalCompiler.applyDealChange(
                source, baseDigest, dealOperations(operationsJson)));
    }

    /** Applies one compiler-owned Deal UI transaction and returns canonical protocol JSON. */
    public static String applyDealUiChange(
            String dealSource,
            String source,
            String packSource,
            String baseDigest,
            String operationsJson) {
        return CompilerProtocolJson.encode(CanonicalCompiler.applyDealUiChange(
                dealSource, source, packSource, PACK_SPECIFIER, baseDigest,
                dealUiOperations(operationsJson)));
    }

    /** Creates the provider-neutral LLM orchestration session owned by streaming-compiler. */
    public static Object createRefinementSession(
            String dealSource,
            String dealUiSource,
            String packSource,
            String instruction,
            int maxRounds,
            int maxSemanticRepairs) {
        return new CanonicalRefinementSession(
                dealSource,
                dealUiSource,
                packSource,
                PACK_SPECIFIER,
                instruction,
                maxRounds,
                maxSemanticRepairs).useConstructionApi().withRepairProtocol("repair-workspace-v2");
    }

    public static Object createGenerationSession(
            String packSource,
            String instruction,
            int maxRounds,
            int maxSemanticRepairs) {
        return CanonicalRefinementSession.greenfield(
                packSource,
                PACK_SPECIFIER,
                instruction,
                maxRounds,
                maxSemanticRepairs).useConstructionApi().withRepairProtocol("repair-workspace-v2");
    }

    public static Object createRefinementSessionWithAgentSemantics(
            String dealSource,
            String dealUiSource,
            String packSource,
            String agentSemantics,
            String instruction,
            int maxRounds,
            int maxSemanticRepairs) {
        return ((CanonicalRefinementSession) createRefinementSession(
                dealSource, dealUiSource, packSource, instruction, maxRounds, maxSemanticRepairs))
                .withAgentSemantics(agentSemantics);
    }

    public static Object createGenerationSessionWithAgentSemantics(
            String packSource,
            String agentSemantics,
            String instruction,
            int maxRounds,
            int maxSemanticRepairs) {
        return ((CanonicalRefinementSession) createGenerationSession(
                packSource, instruction, maxRounds, maxSemanticRepairs))
                .withAgentSemantics(agentSemantics);
    }

    public static String refinementNextRequest(Object session) {
        return ((CanonicalRefinementSession) session).nextRequestJson();
    }

    /** Explicit capability negotiation; unsupported versions fail rather than silently falling back. */
    public static Object configureRepairProtocol(Object session, String version) {
        return ((CanonicalRefinementSession) session).withRepairProtocol(version);
    }

    public static Object createGenerationSessionWithReasoning(
            String packSource, String instruction, int maxRounds, int maxSemanticRepairs,
            String dealEffort, String uiEffort) {
        return ((CanonicalRefinementSession) createGenerationSession(
                packSource, instruction, maxRounds, maxSemanticRepairs))
                .withReasoningEffort(dealEffort, uiEffort);
    }

    public static Object createGenerationSessionWithReasoningAndAgentSemantics(
            String packSource, String agentSemantics, String instruction, int maxRounds, int maxSemanticRepairs,
            String dealEffort, String uiEffort) {
        return ((CanonicalRefinementSession) createGenerationSession(
                packSource, instruction, maxRounds, maxSemanticRepairs))
                .withAgentSemantics(agentSemantics)
                .withReasoningEffort(dealEffort, uiEffort);
    }

    public static String refinementAcceptToolCall(
            Object session,
            String name,
            String argumentsJson) {
        return ((CanonicalRefinementSession) session).acceptToolCallJson(name, argumentsJson);
    }

    public static String refinementAcceptToolCalls(Object session, String callsJson) {
        return ((CanonicalRefinementSession) session).acceptToolCallsJson(callsJson);
    }

    public static String refinementValidateToolCalls(Object session, String callsJson) {
        return ((CanonicalRefinementSession) session).validateToolCallsJson(callsJson);
    }

    public static String refinementResult(Object session) {
        return ((CanonicalRefinementSession) session).resultJson();
    }

    public static String validateAndDump(
            String dealSource,
            String dealUiSource,
            String packSource) {
        try {
            return new UiIrDumper().dump(check(dealSource, dealUiSource, packSource, false));
        } catch (UiDiagnostic diagnostic) {
            throw new IllegalArgumentException(diagnostic.format(), diagnostic);
        }
    }

    public static String compilePortable(
            String dealSource,
            String dealUiSource,
            String packSource) {
        try {
            return CanonicalDealUiJson.encode(check(dealSource, dealUiSource, packSource, false));
        } catch (UiDiagnostic diagnostic) {
            throw new IllegalArgumentException(diagnostic.format(), diagnostic);
        }
    }

    public static String compilePortablePreview(
            String dealSource,
            String dealUiSource,
            String packSource) {
        try {
            return CanonicalDealUiJson.encode(check(dealSource, dealUiSource, packSource, true));
        } catch (UiDiagnostic diagnostic) {
            throw new IllegalArgumentException(diagnostic.format(), diagnostic);
        }
    }

    /**
     * Runs the portable debug/test replay of the smallest complete UI-first transaction against
     * this bridge's caller-supplied pinned component pack. It deliberately has no provider,
     * credential, persistence, or publication surface; production generation does not call it.
     */
    public static String runUiFirstReplayFixture(String packSource) {
        requireText(packSource, "platform-ui.dealui-pack");
        var replay = UiFirstReplayFixture.run(packSource, PACK_SPECIFIER);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", replay.version());
        result.put("dealSource", replay.dealSource());
        result.put("dealUiSource", replay.dealUiSource());
        result.put("structuralDigest", replay.structuralDigest());
        result.put("bindingDigest", replay.bindingDigest());
        result.put("plannerEvaluations", replay.plannerEvaluations());
        return CompilerProtocolJson.encode(result);
    }

    /**
     * Starts the portable UI-first transaction used by Studio's Jev UI + DEAL mode.
     *
     * <p>The opaque handle stays inside this reflection-loaded compiler bundle. The Android host
     * may transport a provider response, but it cannot construct a draft, lower a recipe plan,
     * synthesize binding requirements, or link a source pair itself.
     */
    public static Object createUiFirstLiveSession(
            String packSource,
            String requestDigest,
            String scenario) {
        return UiFirstLiveBridge.createStudioSession(packSource, PACK_SPECIFIER, requestDigest, scenario);
    }

    public static String uiFirstLiveCurrentEvent(Object session) {
        return UiFirstLiveBridge.studioCurrentEvent(session);
    }

    public static String uiFirstLiveAdvancePlanner(Object session, String responseJson) {
        return UiFirstLiveBridge.studioAdvancePlanner(session, responseJson);
    }

    public static String uiFirstLiveBusinessRequest(
            Object session,
            String originalUserRequest,
            String repairCodesJson) {
        return UiFirstLiveBridge.studioBusinessRequest(session, originalUserRequest, repairCodesJson);
    }

    public static String uiFirstLiveCompleteBusiness(Object session, String completionJson) {
        return UiFirstLiveBridge.studioCompleteBusiness(session, completionJson);
    }

    /**
     * Starts the bounded natural-request S0 → S1 → S2 UI-first transaction.
     *
     * <p>As with the prepared live session, this bridge deliberately exposes only an opaque
     * compiler-owned handle. Android can transport finite Jev replies and a constrained business
     * completion, but it cannot shape a tree, interpret request spans, issue ports, or link a
     * source pair.
     */
    public static Object createNaturalUiFirstLiveSession(
            String packSource,
            String requestDigest,
            String originalUserRequest,
            String viewportClass,
            String locale,
            String legalCapabilitiesJson) {
        return UiFirstLiveBridge.createNaturalStudioSession(
                packSource,
                PACK_SPECIFIER,
                requestDigest,
                originalUserRequest,
                viewportClass,
                locale,
                legalCapabilitiesJson);
    }

    public static String naturalUiFirstLiveCurrentEvent(Object session) {
        return UiFirstLiveBridge.studioNaturalCurrentEvent(session);
    }

    public static String naturalUiFirstLiveAdvancePlanner(Object session, String responseJson) {
        return UiFirstLiveBridge.studioNaturalAdvancePlanner(session, responseJson);
    }

    public static String naturalUiFirstLiveBusinessRequest(Object session, String repairCodesJson) {
        return UiFirstLiveBridge.studioNaturalBusinessRequest(session, repairCodesJson);
    }

    public static String naturalUiFirstLiveCompleteBusiness(Object session, String completionJson) {
        return UiFirstLiveBridge.studioNaturalCompleteBusiness(session, completionJson);
    }

    /**
     * Opens the sole source-free S3 write surface after the natural UI draft has frozen.
     * Android receives a strict compiler tool schema only; canonical source strings never cross
     * this boundary until the compiler has linked and accepted the complete pair.
     */
    public static String naturalUiFirstBusinessConstructionRequest(Object session) {
        return UiFirstLiveBridge.studioNaturalBusinessConstructionRequest(session);
    }

    /** Applies one {@code construct_complete_frozen_business} tool call atomically. */
    public static String naturalUiFirstAdvanceBusinessConstruction(Object session, String toolCallJson) {
        return UiFirstLiveBridge.studioNaturalAdvanceBusinessConstruction(session, toolCallJson);
    }

    /**
     * Executes Jev planning and raw DEAL Flash generation through the versioned frozen ABI.
     * The compiler permits one replacement repair and atomically admits the source pair.
     * Credentials are a transient in-memory JSON bundle and are never echoed in the result.
     */
    public static String runNaturalUiFirstGeneration(
            String packSource,
            String originalUserRequest,
            String viewportClass,
            String locale,
            String legalCapabilitiesJson,
            String jevModel,
            String deepSeekModel,
            String credentialBundleJson) {
        return UiFirstGenerationExecutor.run(
                packSource,
                PACK_SPECIFIER,
                originalUserRequest,
                viewportClass,
                locale,
                legalCapabilitiesJson,
                jevModel,
                deepSeekModel,
                credentialBundleJson);
    }

    /** Same compiler-owned transaction with a one-way, source-free frozen-preview callback. */
    public static String runNaturalUiFirstGenerationWithPreview(
            String packSource,
            String originalUserRequest,
            String viewportClass,
            String locale,
            String legalCapabilitiesJson,
            String jevModel,
            String deepSeekModel,
            String credentialBundleJson,
            Consumer<String> previewConsumer) {
        return UiFirstGenerationExecutor.run(
                packSource,
                PACK_SPECIFIER,
                originalUserRequest,
                viewportClass,
                locale,
                legalCapabilitiesJson,
                jevModel,
                deepSeekModel,
                credentialBundleJson,
                previewConsumer);
    }

    public static String runManifestUiFirstGenerationWithPreview(
            String packSource, String originalUserRequest, String viewportClass, String locale,
            String legalCapabilitiesJson, String jevModel, String deepSeekModel, String credentialBundleJson,
            Consumer<String> previewConsumer, String rendererComponentsJson, String rendererQualityEvidenceJson) {
        if (!((deal.semantic.ir.CanonicalJson.Arr) deal.semantic.ir.CanonicalJson.parse(legalCapabilitiesJson)).items().isEmpty())
            throw new IllegalArgumentException("UI-first portable capabilities must be empty");
        return UiFirstGenerationExecutor.runManifest(packSource, PACK_SPECIFIER, originalUserRequest,
                viewportClass, locale, legalCapabilitiesJson, jevModel, deepSeekModel, credentialBundleJson,
                previewConsumer, rendererComponentsJson, rendererQualityEvidenceJson);
    }

    public static String runManifestUiFirstGenerationWithPreview(
            String packSource, String originalUserRequest, String viewportClass, String locale,
            String legalCapabilitiesJson, String jevModel, String deepSeekModel, String credentialBundleJson,
            Consumer<String> previewConsumer, String rendererComponentsJson, String rendererQualityEvidenceJson,
            String componentSemanticsJson) {
        if (!((deal.semantic.ir.CanonicalJson.Arr) deal.semantic.ir.CanonicalJson.parse(legalCapabilitiesJson)).items().isEmpty())
            throw new IllegalArgumentException("UI-first portable capabilities must be empty");
        return UiFirstGenerationExecutor.runManifest(packSource, PACK_SPECIFIER, originalUserRequest,
                viewportClass, locale, legalCapabilitiesJson, jevModel, deepSeekModel, credentialBundleJson,
                previewConsumer, rendererComponentsJson, rendererQualityEvidenceJson, componentSemanticsJson);
    }

    public static String runManifestCoherentUiFirstGenerationWithPreview(
            String packSource, String request, String viewport, String locale, String capabilities,
            String jevModel, String deepSeekModel, String credentials, Consumer<String> preview,
            String renderers, String quality, String semantics, Consumer<String> trace) {
        return UiFirstGenerationExecutor.runManifestCoherent(packSource, PACK_SPECIFIER, request,
                viewport, locale, capabilities, jevModel, deepSeekModel, credentials,
                preview, renderers, quality, semantics, trace);
    }

    public static String runManifestUiFirstGenerationWithTrace(
            String packSource, String request, String viewport, String locale, String capabilities,
            String jevModel, String deepSeekModel, String credentials, Consumer<String> preview,
            String renderers, String quality, String semantics, Consumer<String> trace) {
        return UiFirstGenerationExecutor.runManifest(packSource, PACK_SPECIFIER, request,
                viewport, locale, capabilities, jevModel, deepSeekModel, credentials,
                preview, renderers, quality, semantics, trace);
    }

    public static String appInterfaceFingerprint(String dealSource) {
        validateDealForUi(dealSource);
        var snapshot = CanonicalCompiler.extractAppInterface(embedded(dealSource).dealSource());
        if (snapshot == null) throw new IllegalArgumentException("app.deal has no AppInterface");
        return snapshot.fingerprint();
    }

    public static Object createManifestUiSession(String packSource, String request, String rendererComponentsJson, String rendererQualityEvidenceJson) {
        List<String> names = ((deal.semantic.ir.CanonicalJson.Arr) deal.semantic.ir.CanonicalJson.parse(rendererComponentsJson)).items().stream()
                .map(value -> ((deal.semantic.ir.CanonicalJson.Str) value).value()).toList();
        return new streaming.compiler.ManifestUiRefinementSession(packSource, PACK_SPECIFIER, request,
                "compact-phone", "en", List.of(), new java.util.HashSet<>(names), rendererQualityEvidenceJson);
    }

    public static Object createManifestUiSession(String packSource, String request, String rendererComponentsJson,
            String rendererQualityEvidenceJson, String componentSemanticsJson) {
        List<String> names = ((deal.semantic.ir.CanonicalJson.Arr) deal.semantic.ir.CanonicalJson.parse(rendererComponentsJson)).items().stream()
                .map(value -> ((deal.semantic.ir.CanonicalJson.Str) value).value()).toList();
        return new streaming.compiler.ManifestUiRefinementSession(packSource, PACK_SPECIFIER, request,
                "compact-phone", "en", List.of(), new java.util.HashSet<>(names),
                rendererQualityEvidenceJson, componentSemanticsJson);
    }

    public static String manifestUiCurrentEvent(Object session) {
        return deal.compiler.CompilerProtocolJson.encode(((streaming.compiler.ManifestUiRefinementSession) session).event());
    }

    public static String manifestUiAdvance(Object session, String response) {
        var manifest = (streaming.compiler.ManifestUiRefinementSession) session;
        manifest.advance(response);
        return deal.compiler.CompilerProtocolJson.encode(manifest.event());
    }

    /** Applies the sole compiler-determined preview patch without fabricating a provider answer. */
    public static String manifestUiApplyForcedPatch(Object session) {
        var manifest = (streaming.compiler.ManifestUiRefinementSession) session;
        manifest.applyForcedPatch();
        return deal.compiler.CompilerProtocolJson.encode(manifest.event());
    }

    /** Cancels the active compiler-owned UI-first provider transport, if there is one. */
    public static void cancelNaturalUiFirstGeneration() {
        UiFirstGenerationExecutor.cancelActive();
    }

    /**
     * Compiles the one-file Studio profile. The embedded view is authored after the DEAL module
     * under {@code // @ui-root}; the bridge gives the existing typed Deal UI checker a virtual
     * module, rather than persisting or accepting a second application source file.
     */
    public static String compileEmbeddedPortable(String source, String packSource) {
        EmbeddedSource embedded = embedded(source);
        if (embedded.viewStartLine() == 0) {
            throw new IllegalArgumentException("app.deal must end with exactly one // @ui-root embedded UI declaration");
        }
        try {
            return CanonicalDealUiJson.encode(checkEmbedded(embedded, packSource, false));
        } catch (UiDiagnostic diagnostic) {
            throw new IllegalArgumentException(formatEmbeddedDiagnostic(diagnostic, embedded), diagnostic);
        }
    }

    /** Validates generated application logic without requiring a Deal UI document. */
    public static String validateDealOnly(String dealSource) {
        requireText(dealSource, "app.deal");
        validateDeal(embedded(dealSource).dealSource());
        return "ok";
    }

    /** Validates DEAL plus the framework contracts that apply before a Deal UI exists. */
    public static String validateDealForUi(String dealSource) {
        requireText(dealSource, "app.deal");
        String logic = embedded(dealSource).dealSource();
        validateDeal(logic);
        try {
            new UiChecker().parseDeal(DEAL_FILE, sourceWithPrelude(logic));
        } catch (UiDiagnostic diagnostic) {
            throw new IllegalArgumentException(diagnostic.format(), diagnostic);
        }
        return "ok";
    }

    /** Derives the UI-facing contract from the validated DEAL module; no model planner is involved. */
    public static String extractAppInterface(String dealSource) {
        requireText(dealSource, "app.deal");
        validateDealForUi(dealSource);
        var snapshot = CanonicalCompiler.extractAppInterface(embedded(dealSource).dealSource());
        if (snapshot == null) throw new IllegalArgumentException("app.deal has no AppInterface");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", "app-interface-v1");
        result.put("root_state", snapshot.rootState());
        result.put("types", interfaceTypes(snapshot.types()));
        result.put("actions", interfaceTypes(snapshot.actions()));
        result.put("capabilities", snapshot.capabilities());
        return CompilerProtocolJson.encode(result);
    }

    public static Object createRuntime(String dealSource) {
        requireText(dealSource, "app.deal");
        validateDealForUi(dealSource);
        return new CanonicalDealRuntime(embedded(dealSource).dealSource());
    }

    public static String runtimeSnapshot(Object runtime) {
        return ((CanonicalDealRuntime) runtime).snapshotJson();
    }

    public static String runtimeRestore(Object runtime, Map<String, Object> state) {
        return ((CanonicalDealRuntime) runtime).restore(state);
    }

    public static String runtimeDispatch(
            Object runtime,
            String handler,
            String actionType,
            String[] fieldNames,
            Object[] fieldValues) {
        return ((CanonicalDealRuntime) runtime).dispatch(
                handler,
                actionType,
                fieldNames,
                fieldValues);
    }

    /**
     * Conservative production-lexer/parser inspection for an incomplete streamed prefix.
     * Only lexical errors that are not an unfinished token at EOF are terminal. Parser
     * diagnostics are returned to the caller for observability but remain non-terminal
     * because the production parser is intentionally designed around complete modules.
     */
    public static String inspectDealPrefix(String source) {
        String compilerSource = normalizeDirectives(source);
        int scalarLength = compilerSource.codePointCount(0, compilerSource.length());
        LexResult lexed = new Lexer(compilerSource, DEAL_FILE.toString()).tokenize();
        StringBuilder result = new StringBuilder(512);
        boolean impossible = false;
        for (CompilerDiagnostic diagnostic : lexed.diagnostics()) {
            if (!"error".equals(diagnostic.severity())) continue;
            boolean incompleteAtEof = Set.of("E1003", "E1004", "E1042", "E1044").contains(diagnostic.code())
                    && diagnostic.range().endScalarOffset() >= scalarLength;
            if (!incompleteAtEof) impossible = true;
            appendPrefixDiagnostic(result, "lex", diagnostic);
        }
        try {
            ParseResult parsed = new Parser(lexed.tokens(), DEAL_FILE.toString()).parse();
            for (CompilerDiagnostic diagnostic : parsed.diagnostics()) {
                if ("error".equals(diagnostic.severity())) {
                    appendPrefixDiagnostic(result, "parse", diagnostic);
                }
            }
        } catch (RuntimeException ignored) {
            result.append("parserCrashed=1\n");
        }
        result.insert(0, "impossible=" + (impossible ? "1" : "0") + "\n");
        return result.toString();
    }

    private static UiModel.CheckedProgram check(
            String dealSource,
            String dealUiSource,
            String packSource,
            boolean allowUnreachableUpdates) {
        requireText(dealSource, "app.deal");
        requireText(dealUiSource, "app.dealui");
        requireText(packSource, "platform-ui.dealui-pack");

        validateDeal(dealSource);

        UiModel.ViewModule views = UiParser.parseViews(UI_FILE, dealUiSource);
        UiModel.PackModule pack = UiParser.parsePack(PACK_FILE, packSource);
        UiChecker checker = new UiChecker();
        UiModel.DealModule deal = checker.parseDeal(DEAL_FILE, sourceWithPrelude(dealSource));
        return checker.check(
                UI_FILE,
                views,
                DEAL_FILE,
                deal,
                Map.of(PACK_SPECIFIER, pack),
                allowUnreachableUpdates);
    }

    private static UiModel.CheckedProgram checkEmbedded(EmbeddedSource embedded, String packSource, boolean allowUnreachableUpdates) {
        requireText(packSource, "platform-ui.dealui-pack");
        validateDeal(embedded.dealSource());
        UiModel.ViewModule views = UiParser.parseViews(UI_FILE, embedded.virtualUiSource());
        UiModel.PackModule pack = UiParser.parsePack(PACK_FILE, packSource);
        UiChecker checker = new UiChecker();
        UiModel.DealModule deal = checker.parseDeal(DEAL_FILE, sourceWithPrelude(embedded.dealSource()));
        return checker.check(
                UI_FILE,
                views,
                DEAL_FILE,
                deal,
                Map.of(PACK_SPECIFIER, pack),
                allowUnreachableUpdates);
    }

    private static EmbeddedSource embedded(String source) {
        String marker = "// @ui-root";
        int start = source.indexOf(marker);
        if (start < 0) return new EmbeddedSource(source, "", 0);
        if (source.indexOf(marker, start + marker.length()) >= 0) {
            throw new IllegalArgumentException("app.deal may contain exactly one // @ui-root embedded view marker");
        }
        String deal = source.substring(0, start).stripTrailing() + "\n";
        String view = source.substring(start).trim();
        if (!view.startsWith(marker)) throw new IllegalArgumentException("Embedded UI must start at // @ui-root");
        String virtualUi = "import * as app from \"./app.deal\";\n"
                + "import * as ui from \"" + PACK_SPECIFIER + "\";\n\n"
                + view + "\n";
        int viewStartLine = 1;
        for (int index = 0; index < start; index++) if (source.charAt(index) == '\n') viewStartLine++;
        return new EmbeddedSource(deal, virtualUi, viewStartLine);
    }

    private static String formatEmbeddedDiagnostic(UiDiagnostic diagnostic, EmbeddedSource embedded) {
        String formatted = diagnostic.format();
        if (embedded.viewStartLine() == 0) return formatted;
        Pattern virtualLine = Pattern.compile(Pattern.quote(UI_FILE.toString()) + ":(\\d+):");
        Matcher matcher = virtualLine.matcher(formatted);
        StringBuffer rewritten = new StringBuffer();
        while (matcher.find()) {
            int virtualLineNumber = Integer.parseInt(matcher.group(1));
            int sourceLineNumber = embedded.viewStartLine() + virtualLineNumber - 4;
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(DEAL_FILE + ":" + sourceLineNumber + ":"));
        }
        matcher.appendTail(rewritten);
        return rewritten.toString();
    }

    private record EmbeddedSource(String dealSource, String virtualUiSource, int viewStartLine) {}

    private static void validateDeal(String source) {
        String compilerSource = compilerSource(source);
        LexResult lexed = new Lexer(compilerSource, DEAL_FILE.toString()).tokenize();
        requireNoErrors("Deal lexer", lexed.diagnostics());
        ParseResult parsed = new Parser(
                lexed.tokens(),
                DEAL_FILE.toString()).parse();
        requireNoErrors("Deal parser", parsed.diagnostics());
        requireNoErrors(
                "Deal module shape",
                ModuleShapeValidator.validate(parsed.program(), DEAL_FILE.toString(), false));

        NoImports resolverDelegate = new NoImports();
        NameResolver resolver = new NameResolver(DEAL_FILE.toString(), resolverDelegate);
        SymbolTable symbols = resolver.resolve(parsed.program());
        requireNoErrors("Deal name resolver", resolver.diagnostics());
        CheckResult checked = TypeChecker.check(
                DEAL_FILE.toString(),
                symbols,
                resolver,
                parsed.program());
        requireNoErrors("Deal type checker", checked.diagnostics());
    }

    private static List<Map<String, Object>> interfaceTypes(
            List<deal.compiler.CompilerProtocol.TypeSnapshot> values) {
        return values.stream().map(type -> Map.<String, Object>of(
                "name", type.name(),
                "fields", type.fields().stream().map(field -> Map.of(
                        "name", field.name(), "type", field.type())).toList())).toList();
    }

    private static String compilerSource(String source) {
        return normalizeDirectives(sourceWithPrelude(source));
    }

    private static String sourceWithPrelude(String source) {
        return source.contains("function platformIntText(value: int): string")
                ? source
                : source + GENERATED_APP_PRELUDE;
    }

    private static String normalizeDirectives(String source) {
        return source.replaceAll(
                "(?m)^(\\s*)// @(ui-(?:update|effect|effect-policy|effect-failure))(\\s*)$",
                "$1//  $2$3");
    }

    private static void appendPrefixDiagnostic(
            StringBuilder out,
            String phase,
            CompilerDiagnostic diagnostic) {
        String message = diagnostic.message()
                .replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
        out.append("diagnostic=").append(phase).append('\t')
                .append(diagnostic.code()).append('\t')
                .append(diagnostic.range().startScalarOffset()).append('\t')
                .append(diagnostic.range().endScalarOffset()).append('\t')
                .append(message).append('\n');
    }

    private static void requireNoErrors(String phase, List<CompilerDiagnostic> diagnostics) {
        List<CompilerDiagnostic> errors = diagnostics.stream()
                .filter(diagnostic -> "error".equals(diagnostic.severity()))
                .limit(32)
                .toList();
        if (!errors.isEmpty()) {
            StringBuilder message = new StringBuilder(phase);
            for (CompilerDiagnostic diagnostic : errors) {
                message.append("\n").append(diagnostic.file()).append(":")
                        .append(diagnostic.line()).append(":").append(diagnostic.column()).append(" ")
                        .append(diagnostic.code()).append(": ").append(diagnostic.message());
            }
            throw new IllegalArgumentException(message.toString());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is empty");
        }
    }

    private static List<DealCompilerWorkspace.Operation> dealOperations(String source) {
        CanonicalJson.Arr values = CompilerProtocolJson.requireArray(
                CompilerProtocolJson.decode(source), "DEAL operations");
        List<DealCompilerWorkspace.Operation> result = new ArrayList<>();
        for (CanonicalJson.Value value : values.items()) {
            CanonicalJson.Obj operation = CompilerProtocolJson.requireObject(value, "DEAL operation");
            String name = CompilerProtocolJson.stringField(operation, "operation");
            SemanticId target = new SemanticId(CompilerProtocolJson.stringField(operation, "targetId"));
            result.add(switch (name) {
                case DealCompilerWorkspace.ADD_DECLARATION -> new DealCompilerWorkspace.AddDeclaration(
                        target, CompilerProtocolJson.stringField(operation, "declaration"));
                case DealCompilerWorkspace.REMOVE_DECLARATION -> new DealCompilerWorkspace.RemoveDeclaration(target);
                case DealCompilerWorkspace.REPLACE_DECLARATION -> new DealCompilerWorkspace.ReplaceDeclaration(
                        target, CompilerProtocolJson.stringField(operation, "declaration"));
                case DealCompilerWorkspace.REPLACE_FUNCTION_BODY -> new DealCompilerWorkspace.ReplaceFunctionBody(
                        target, CompilerProtocolJson.stringField(operation, "body"));
                case DealCompilerWorkspace.REPLACE_BLOCK_BODY -> new DealCompilerWorkspace.ReplaceBlockBody(
                        target, CompilerProtocolJson.stringField(operation, "body"));
                default -> throw new IllegalArgumentException("Unsupported DEAL operation: " + name);
            });
        }
        return List.copyOf(result);
    }

    private static List<UiCompilerWorkspace.Operation> dealUiOperations(String source) {
        CanonicalJson.Arr values = CompilerProtocolJson.requireArray(
                CompilerProtocolJson.decode(source), "Deal UI operations");
        List<UiCompilerWorkspace.Operation> result = new ArrayList<>();
        for (CanonicalJson.Value value : values.items()) {
            CanonicalJson.Obj operation = CompilerProtocolJson.requireObject(value, "Deal UI operation");
            String name = CompilerProtocolJson.stringField(operation, "operation");
            SemanticId target = new SemanticId(CompilerProtocolJson.stringField(operation, "targetId"));
            result.add(switch (name) {
                case UiCompilerWorkspace.ADD_VIEW -> new UiCompilerWorkspace.AddView(
                        target, CompilerProtocolJson.stringField(operation, "source"));
                case UiCompilerWorkspace.REMOVE_VIEW -> new UiCompilerWorkspace.RemoveView(target);
                case UiCompilerWorkspace.REPLACE_VIEW_BODY -> new UiCompilerWorkspace.ReplaceViewBody(
                        target, CompilerProtocolJson.stringField(operation, "body"));
                case UiCompilerWorkspace.REPLACE_SUBTREE -> new UiCompilerWorkspace.ReplaceSubtree(
                        target, CompilerProtocolJson.stringField(operation, "source"));
                case UiCompilerWorkspace.INSERT_CHILD -> new UiCompilerWorkspace.InsertChild(
                        target, CompilerProtocolJson.intField(operation, "index"),
                        CompilerProtocolJson.stringField(operation, "source"));
                case UiCompilerWorkspace.REMOVE_NODE -> new UiCompilerWorkspace.RemoveNode(target);
                case UiCompilerWorkspace.MOVE_NODE -> new UiCompilerWorkspace.MoveNode(
                        target,
                        new SemanticId(CompilerProtocolJson.stringField(operation, "newParentId")),
                        CompilerProtocolJson.intField(operation, "index"));
                case UiCompilerWorkspace.SET_PROPERTY -> new UiCompilerWorkspace.SetProperty(
                        target,
                        CompilerProtocolJson.stringField(operation, "property"),
                        CompilerProtocolJson.stringField(operation, "expression"));
                default -> throw new IllegalArgumentException("Unsupported Deal UI operation: " + name);
            });
        }
        return List.copyOf(result);
    }

    private static final class NoImports implements ModuleResolver {
        @Override
        public Map<String, Type> resolveModule(
                String modulePath,
                String importingModule,
                Set<String> modulesInProgress) throws ModuleNotFoundException {
            throw new ModuleNotFoundException("Imports are not enabled in generated app.deal: " + modulePath);
        }

        @Override
        public Symbol.ClassSymbol resolveClassSymbol(
                String className,
                String modulePath,
                String importingModule) throws ModuleNotFoundException {
            throw new ModuleNotFoundException("Imported class is unavailable: " + modulePath + "." + className);
        }
    }
}
