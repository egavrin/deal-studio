package com.offlineassistant.dealtoolchain;

import deal.ast.ArrayType;
import deal.ast.ClassDeclaration;
import deal.ast.ClassField;
import deal.ast.ExportDeclaration;
import deal.ast.FunctionDeclaration;
import deal.ast.NamedType;
import deal.ast.ProgramNode;
import deal.ast.StatementNode;
import deal.ast.TypeNode;
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

import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

/** Android reflection boundary for the pinned production Deal and Deal UI frontends. */
public final class CanonicalDealToolchainBridge {
    private static final Path DEAL_FILE = Path.of("/generated/app.deal");
    private static final Path UI_FILE = Path.of("/generated/app.dealui");
    private static final Path PACK_FILE = Path.of("/generated/platform-ui.dealui-pack");
    private static final String PACK_SPECIFIER = "./platform-ui.dealui-pack";
    private static final Pattern CAPABILITY = Pattern.compile(
            "(?m)^\\s*//\\s*generated-capability:\\s*([a-z][a-z0-9.]*)\\s*$");
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

    /** Validates generated application logic without requiring a Deal UI document. */
    public static String validateDealOnly(String dealSource) {
        requireText(dealSource, "app.deal");
        validateDeal(dealSource);
        return "ok";
    }

    /** Validates DEAL plus the framework contracts that apply before a Deal UI exists. */
    public static String validateDealForUi(String dealSource) {
        requireText(dealSource, "app.deal");
        validateDeal(dealSource);
        try {
            new UiChecker().parseDeal(DEAL_FILE, sourceWithPrelude(dealSource));
        } catch (UiDiagnostic diagnostic) {
            throw new IllegalArgumentException(diagnostic.format(), diagnostic);
        }
        return "ok";
    }

    /** Derives the UI-facing contract from the validated DEAL module; no model planner is involved. */
    public static String extractAppInterface(String dealSource) {
        requireText(dealSource, "app.deal");
        validateDealForUi(dealSource);
        ParseResult parsed = new Parser(
                new Lexer(normalizeDirectives(dealSource), DEAL_FILE.toString()).tokenize().tokens(),
                DEAL_FILE.toString()).parse();
        Map<String, ClassDeclaration> classes = new LinkedHashMap<>();
        List<FunctionDeclaration> functions = new java.util.ArrayList<>();
        for (StatementNode statement : parsed.program().statements()) {
            StatementNode declaration = statement instanceof ExportDeclaration export
                    ? export.declaration()
                    : statement;
            if (declaration instanceof ClassDeclaration value) classes.put(value.name(), value);
            if (declaration instanceof FunctionDeclaration value) functions.add(value);
        }
        FunctionDeclaration initial = functions.stream()
                .filter(function -> "initialState".equals(function.name()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("app.deal does not export initialState()"));
        String rootState = typeName(initial.returnType());
        if (!classes.containsKey(rootState)) {
            throw new IllegalArgumentException("initialState() return type is not a generated class: " + rootState);
        }
        Set<String> actionNames = new LinkedHashSet<>();
        for (FunctionDeclaration function : functions) {
            if (function.params().size() != 2 || "initialState".equals(function.name())) continue;
            String stateType = typeName(function.params().get(0).type());
            String actionType = typeName(function.params().get(1).type());
            if (rootState.equals(stateType) && rootState.equals(typeName(function.returnType()))) {
                actionNames.add(actionType);
            }
        }
        if (actionNames.isEmpty()) {
            throw new IllegalArgumentException("app.deal does not expose any generated UI action");
        }
        for (String actionName : actionNames) {
            if (!classes.containsKey(actionName)) {
                throw new IllegalArgumentException("UI action type is not a generated class: " + actionName);
            }
        }

        StringBuilder out = new StringBuilder(2048);
        out.append('{');
        jsonField(out, "version", "app-interface-v1");
        out.append(',');
        jsonField(out, "root_state", rootState);
        out.append(',').append("\"types\":");
        appendTypes(out, classes, actionNames, false);
        out.append(',').append("\"actions\":");
        appendTypes(out, classes, actionNames, true);
        out.append(',').append("\"capabilities\":[");
        Matcher matcher = CAPABILITY.matcher(dealSource);
        boolean first = true;
        while (matcher.find()) {
            if (!first) out.append(',');
            jsonString(out, matcher.group(1));
            first = false;
        }
        return out.append("]}").toString();
    }

    public static Object createRuntime(String dealSource) {
        requireText(dealSource, "app.deal");
        validateDealForUi(dealSource);
        return new CanonicalDealRuntime(dealSource);
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

    private static void appendTypes(
            StringBuilder out,
            Map<String, ClassDeclaration> classes,
            Set<String> actionNames,
            boolean actions) {
        out.append('[');
        boolean firstType = true;
        for (ClassDeclaration declaration : classes.values()) {
            if (actionNames.contains(declaration.name()) != actions) continue;
            if (!firstType) out.append(',');
            out.append('{');
            jsonField(out, "name", declaration.name());
            out.append(',').append("\"fields\":[");
            boolean firstField = true;
            for (ClassField field : declaration.fields()) {
                if (!firstField) out.append(',');
                out.append('{');
                jsonField(out, "name", field.name());
                out.append(',');
                jsonField(out, "type", typeName(field.type()));
                out.append('}');
                firstField = false;
            }
            out.append("]}");
            firstType = false;
        }
        out.append(']');
    }

    private static String typeName(TypeNode type) {
        if (type instanceof NamedType named) return named.name();
        if (type instanceof ArrayType array) return typeName(array.elementType()) + "[]";
        throw new IllegalArgumentException("Generated app contract uses unsupported type: " + type);
    }

    private static void jsonField(StringBuilder out, String name, String value) {
        jsonString(out, name);
        out.append(':');
        jsonString(out, value);
    }

    private static void jsonString(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(current);
            }
        }
        out.append('"');
    }

    private static String compilerSource(String source) {
        return normalizeDirectives(sourceWithPrelude(source));
    }

    private static String sourceWithPrelude(String source) {
        return source + GENERATED_APP_PRELUDE;
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
