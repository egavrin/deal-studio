package com.offlineassistant.dealtoolchain;

import deal.ui.UiModel;

import java.util.List;
import java.util.Map;

/** Serializes the checked Deal UI tree into a toolkit-neutral runtime payload. */
final class CanonicalDealUiJson {
    private CanonicalDealUiJson() {}

    static String encode(UiModel.CheckedProgram program) {
        StringBuilder out = new StringBuilder(4096);
        out.append('{');
        field(out, "version", "canonical-dealui-ir-v1");
        comma(out);
        field(out, "title", program.title());
        comma(out);
        field(out, "rootStateType", program.rootStateType());
        comma(out);
        quote(out, "metadata");
        out.append(':');
        metadata(out, program.metadata());
        comma(out);
        quote(out, "nodes");
        out.append(':');
        nodes(out, program.rootNodes());
        comma(out);
        quote(out, "updates");
        out.append(':').append('{');
        boolean first = true;
        for (Map.Entry<String, UiModel.Handler> entry : program.updates().entrySet()) {
            if (!first) comma(out);
            quote(out, entry.getKey());
            out.append(':');
            quote(out, entry.getValue().name());
            first = false;
        }
        out.append('}');
        comma(out);
        quote(out, "tokens");
        out.append(':').append('{');
        first = true;
        for (Map.Entry<String, UiModel.Token> entry : program.tokens().entrySet()) {
            if (!first) comma(out);
            quote(out, entry.getKey());
            out.append(':');
            expr(out, entry.getValue().value());
            first = false;
        }
        out.append('}');
        out.append('}');
        return out.toString();
    }

    private static void metadata(StringBuilder out, UiModel.CheckedMetadata metadata) {
        out.append('{');
        field(out, "rootStateType", metadata.rootStateType());
        comma(out);
        quote(out, "reachableInputActions");
        out.append(':');
        strings(out, metadata.reachableInputActions());
        comma(out);
        quote(out, "effectCompletionActions");
        out.append(':');
        strings(out, metadata.effectCompletionActions());
        comma(out);
        quote(out, "usedComponents");
        out.append(':');
        strings(out, metadata.usedComponents());
        comma(out);
        quote(out, "componentCapabilities");
        out.append(':');
        stringMap(out, metadata.componentCapabilities());
        comma(out);
        quote(out, "packVersions");
        out.append(':');
        stringMap(out, metadata.packVersions());
        comma(out);
        quote(out, "packDigests");
        out.append(':');
        stringMap(out, metadata.packDigests());
        out.append('}');
    }

    private static void stringMap(StringBuilder out, Map<String, String> values) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) comma(out);
            quote(out, entry.getKey());
            out.append(':');
            quote(out, entry.getValue());
            first = false;
        }
        out.append('}');
    }

    private static void nodes(StringBuilder out, List<UiModel.RenderNode> nodes) {
        out.append('[');
        for (int index = 0; index < nodes.size(); index++) {
            if (index > 0) comma(out);
            node(out, nodes.get(index));
        }
        out.append(']');
    }

    private static void node(StringBuilder out, UiModel.RenderNode node) {
        out.append('{');
        if (node instanceof UiModel.RenderCall call) {
            field(out, "kind", "call");
            comma(out);
            field(out, "name", call.name());
            comma(out);
            field(out, "identity", call.identity());
            comma(out);
            quote(out, "arguments");
            out.append(':');
            expressions(out, call.arguments());
            comma(out);
            quote(out, "children");
            out.append(':');
            nodes(out, call.children());
        } else if (node instanceof UiModel.RenderWhen when) {
            field(out, "kind", "when");
            comma(out);
            field(out, "identity", when.identity());
            comma(out);
            quote(out, "condition");
            out.append(':');
            expr(out, when.condition());
            comma(out);
            quote(out, "then");
            out.append(':');
            nodes(out, when.thenNodes());
            comma(out);
            quote(out, "else");
            out.append(':');
            nodes(out, when.elseNodes());
        } else if (node instanceof UiModel.RenderForEach each) {
            field(out, "kind", "foreach");
            comma(out);
            field(out, "identity", each.identity());
            comma(out);
            quote(out, "source");
            out.append(':');
            path(out, each.source());
            comma(out);
            field(out, "item", each.item().name());
            comma(out);
            field(out, "itemType", each.item().type().name());
            comma(out);
            quote(out, "key");
            out.append(':');
            path(out, each.key());
            comma(out);
            quote(out, "children");
            out.append(':');
            nodes(out, each.children());
        } else {
            UiModel.RenderScope scope = (UiModel.RenderScope) node;
            field(out, "kind", "scope");
            comma(out);
            quote(out, "bindings");
            out.append(':');
            expressions(out, scope.bindings());
            comma(out);
            quote(out, "children");
            out.append(':');
            nodes(out, scope.children());
        }
        out.append('}');
    }

    private static void expressions(StringBuilder out, Map<String, UiModel.Expr> values) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, UiModel.Expr> entry : values.entrySet()) {
            if (!first) comma(out);
            quote(out, entry.getKey());
            out.append(':');
            expr(out, entry.getValue());
            first = false;
        }
        out.append('}');
    }

    private static void expr(StringBuilder out, UiModel.Expr expression) {
        if (expression == null) {
            out.append("null");
            return;
        }
        out.append('{');
        if (expression instanceof UiModel.Literal literal) {
            field(out, "kind", "literal");
            comma(out);
            field(out, "type", literal.type());
            comma(out);
            quote(out, "value");
            out.append(':');
            literal(out, literal.value());
        } else if (expression instanceof UiModel.PathExpr value) {
            field(out, "kind", "path");
            comma(out);
            quote(out, "parts");
            out.append(':');
            strings(out, value.parts());
        } else if (expression instanceof UiModel.Unary value) {
            field(out, "kind", "unary");
            comma(out);
            field(out, "operator", value.operator());
            comma(out);
            quote(out, "operand");
            out.append(':');
            expr(out, value.operand());
        } else if (expression instanceof UiModel.Binary value) {
            field(out, "kind", "binary");
            comma(out);
            field(out, "operator", value.operator());
            comma(out);
            quote(out, "left");
            out.append(':');
            expr(out, value.left());
            comma(out);
            quote(out, "right");
            out.append(':');
            expr(out, value.right());
        } else if (expression instanceof UiModel.Has value) {
            field(out, "kind", "has");
            comma(out);
            quote(out, "path");
            out.append(':');
            path(out, value.path());
        } else {
            UiModel.Action value = (UiModel.Action) expression;
            field(out, "kind", "action");
            comma(out);
            field(out, "name", value.name());
            comma(out);
            quote(out, "fields");
            out.append(':');
            expressions(out, value.fields());
        }
        out.append('}');
    }

    private static void path(StringBuilder out, UiModel.PathExpr value) {
        out.append('{');
        field(out, "kind", "path");
        comma(out);
        quote(out, "parts");
        out.append(':');
        strings(out, value.parts());
        out.append('}');
    }

    private static void literal(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String string) {
            quote(out, string);
        } else if (value instanceof Boolean || value instanceof Number) {
            out.append(value);
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) comma(out);
                Object item = list.get(index);
                if (item instanceof UiModel.Expr itemExpression) expr(out, itemExpression);
                else literal(out, item);
            }
            out.append(']');
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) comma(out);
                quote(out, String.valueOf(entry.getKey()));
                out.append(':');
                Object item = entry.getValue();
                if (item instanceof UiModel.Expr itemExpression) expr(out, itemExpression);
                else literal(out, item);
                first = false;
            }
            out.append('}');
        } else {
            throw new IllegalArgumentException("Unsupported Deal UI literal: " + value.getClass().getName());
        }
    }

    private static void strings(StringBuilder out, List<String> values) {
        out.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) comma(out);
            quote(out, values.get(index));
        }
        out.append(']');
    }

    private static void field(StringBuilder out, String name, String value) {
        quote(out, name);
        out.append(':');
        quote(out, value);
    }

    private static void comma(StringBuilder out) {
        out.append(',');
    }

    private static void quote(StringBuilder out, String value) {
        out.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (character < 0x20) out.append(String.format("\\u%04x", (int) character));
                    else out.append(character);
                }
            }
        }
        out.append('"');
    }
}
