import deal.compiler.DealCompilerWorkspace;
import deal.compiler.CompilerProtocolJson;
import deal.ui.UiDraftManifest;
import deal.ui.UiDraftWorkspace;
import deal.ui.UiDraftProtocolJson;
import streaming.compiler.ManifestUiRefinementSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Offline boundary smoke: no credentials, network, generated business source, or device. */
public final class CanonicalUiFirstHostContractTest {
    public static void main(String[] args) throws Exception {
        String pack = Files.readString(Path.of(args[0]));
        var draft = UiDraftWorkspace.start(DealCompilerWorkspace.digest("generic check"), pack, new UiDraftWorkspace.Limits(64, 10));
        Set<String> renderers = new HashSet<>();
        draft.componentPack().components().forEach(c -> renderers.add(c.name()));
        var manifest = UiDraftManifest.inspect(draft.componentPack(), renderers, Set.of());
        require(manifest.version().equals("ui-draft-manifest-v2"), "manifest version");
        require(UiDraftProtocolJson.VERSION.equals("ui-draft-wire-v5"), "wire version");
        require(manifest.components().stream().filter(c -> c.name().startsWith("Host"))
                .allMatch(c -> c.reason() == UiDraftManifest.Reason.CAPABILITY_UNAVAILABLE), "portable host rejection");
        String digest = manifest.packDigest();
        String valid = evidence(digest, "{\"Root\":[\"ADAPTIVE_COMPACT\",\"ADAPTIVE_REGULAR\",\"ADAPTIVE_EXPANDED\"]}");
        var session = session(pack, renderers, valid);
        Map<?, ?> preview = (Map<?, ?>) session.event().get("preview");
        require(preview != null && "typed-skeleton".equals(preview.get("projectionKind")), "typed projection");
        require(((Map<?, ?>) preview.get("displayState")).isEmpty(), "no synthetic display values");
        require(preview.get("bindingTypes") instanceof Map<?, ?>, "binding types");
        String before = CompilerProtocolJson.encode(session.event());
        reject(pack, renderers, evidence("0".repeat(64), "{}"));
        reject(pack, renderers, evidence(digest, "{\"Unknown\":[]}"));
        reject(pack, renderers, evidence(digest, "{\"Root\":[\"UNKNOWN\"]}"));
        reject(pack, renderers, evidence(digest, "{\"Root\":[\"ADAPTIVE_COMPACT\",\"ADAPTIVE_COMPACT\"]}"));
        reject(pack, renderers, evidence(digest, "{\"Root\":[],\"Root\":[]}"));
        require(before.equals(CompilerProtocolJson.encode(session.event())), "rejected evidence cannot mutate existing preview");
        Map<String, Object> supported = new LinkedHashMap<>();
        draft.componentPack().components().stream().filter(c -> !c.quality().isEmpty()).forEach(c ->
                supported.put(c.name(), c.quality().stream().map(q -> q.trait().name()).toList()));
        String completeEvidence = evidence(digest, CompilerProtocolJson.encode(supported));
        if (args.length > 1 && args[1].equals("batch")) {
            batch(pack, renderers, completeEvidence);
            System.out.println("PASS: v19 batch SELECT/LAYOUT checked preview and freeze");
            return;
        }
        freeze(pack, renderers, completeEvidence, Set.of("INPUT"), "TextField");
        freeze(pack, renderers, completeEvidence, Set.of("INPUT"), "NumberField");
        freeze(pack, renderers, completeEvidence, Set.of("DATE_TIME"), "TimeField");
        freeze(pack, renderers, completeEvidence, Set.of("DATE_TIME"), "DateTimeField");
        freeze(pack, renderers, completeEvidence, Set.of("COLLECTION"), "ListGroup/ListItem");
        freeze(pack, renderers, completeEvidence, Set.of("NAVIGATION", "OVERLAYS"), "Route", "Dialog");
        batch(pack, renderers, completeEvidence);
        System.out.println("PASS: manifest-v2, wire-v5, typed skeleton, portable capability rejection, stale/unknown/duplicate evidence, preview retention");
    }

    private static String evidence(String digest, String components) {
        return "{\"version\":\"renderer-quality-evidence-v1\",\"packDigest\":\"" + digest + "\",\"components\":" + components + "}";
    }

    private static ManifestUiRefinementSession session(String pack, Set<String> renderers, String evidence) {
        return new ManifestUiRefinementSession(pack, "@deal/ui", "Generic checked layout", "compact-phone", "en", List.of(), renderers, evidence);
    }

    private static void freeze(String pack, Set<String> renderers, String evidence, Set<String> required, String... preferred) {
        var session = session(pack, renderers, evidence);
        for (int turn = 0; turn < 64 && session.event().get("event").equals("jev_request"); turn++) {
            var request = session.request();
            Map<String, String> answers = new LinkedHashMap<>();
            for (Object raw : (List<?>) request.get("questions")) {
                var question = (streaming.compiler.JevUiPlanner.Question) raw;
                var options = question.options().stream().filter(o -> !o.alias().equals("unavailable")).toList();
                var selected = options.getLast();
                if (question.alias().startsWith("need:")) {
                    String label = required.contains(question.alias().substring(5)) ? "required" : "not_required";
                    selected = options.stream().filter(o -> o.label().equals(label)).findFirst().orElseThrow();
                } else if (question.alias().equals("continuation")) {
                    selected = options.stream().filter(o -> o.label().equals("complete_obligation")).findFirst().orElseThrow();
                } else if (question.alias().equals("patch")) {
                    selected = options.stream().filter(o -> o.label().startsWith("Text in Column")
                            || Arrays.stream(preferred).anyMatch(name -> o.label().startsWith(name + " in Column")))
                            .findFirst().orElseThrow(() -> new AssertionError("Missing generic patch " + required));
                }
                answers.put(question.alias(), selected.alias());
            }
            session.advance(CompilerProtocolJson.encode(Map.of("protocolVersion", request.get("plannerProtocolVersion"),
                    "requestToken", request.get("requestToken"), "returnedModel", "offline-fixture", "answers", answers)));
        }
        require("preview".equals(session.event().get("event")), "Generic freeze " + required + ": " + session.terminalReason());
        var preview = (Map<?, ?>) session.event().get("preview");
        require(((List<?>) preview.get("qualityDeficits")).isEmpty(), "generic quality deficits");
    }

    private static void reject(String pack, Set<String> renderers, String evidence) {
        try { session(pack, renderers, evidence); }
        catch (IllegalArgumentException rejected) {
            require(rejected.getMessage().equals("UIR_INVALID_RENDERER_QUALITY_EVIDENCE"), "stable rejection");
            return;
        }
        throw new AssertionError("Invalid renderer evidence was accepted");
    }

    private static void batch(String pack, Set<String> renderers, String evidence) {
        var session = session(pack, renderers, evidence);
        session.enableBatchPlanning();
        System.out.println("SELECT questions=" + ((List<?>) session.request().get("questions")).size()
                + " options=" + ((List<?>) session.request().get("questions")).stream()
                .mapToInt(q -> ((streaming.compiler.JevUiPlanner.Question) q).options().size()).sum()
                + " bytes=" + CompilerProtocolJson.encode(session.request()).length());
        require(session.batchEligible(), "v19 batch select fits Jev limits: " + session.batchFallbackReason());
        var select = session.request();
        require("SELECT".equals(select.get("phase")), "batch SELECT request");
        answer(session, select);
        require(session.batchEligible(), "v19 batch composition is representable: " + session.batchFallbackReason());
        var preview = (Map<?, ?>) session.event().get("preview");
        require(Boolean.TRUE.equals(preview.get("meaningful")), "SELECT checked meaningful preview");
        require("typed-skeleton".equals(preview.get("projectionKind"))
                && ((Map<?, ?>) preview.get("displayState")).isEmpty(), "SELECT inert typed preview");
        if ("jev_request".equals(session.event().get("event"))) {
            var layout = session.request();
            require("LAYOUT".equals(layout.get("phase")), "batch LAYOUT request");
            answer(session, layout);
        }
        require("preview".equals(session.event().get("event")) && session.turns() <= 2,
                "v19 batch frozen within two Jev evaluations: " + session.terminalReason());
    }

    private static void answer(ManifestUiRefinementSession session, Map<String, Object> request) {
        Map<String, String> answers = new LinkedHashMap<>();
        for (Object raw : (List<?>) request.get("questions")) {
            var question = (streaming.compiler.JevUiPlanner.Question) raw;
            var chosen = question.options().getFirst();
            answers.put(question.alias(), chosen.alias());
        }
        require(session.advance(CompilerProtocolJson.encode(Map.of(
                "protocolVersion", request.get("plannerProtocolVersion"),
                "requestToken", request.get("requestToken"),
                "returnedModel", "offline-fixture", "answers", answers))), "batch answer accepted");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
