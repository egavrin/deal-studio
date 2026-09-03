package com.offlineassistant.dealtoolchain;

import deal.ast.*;
import deal.lexer.LexResult;
import deal.lexer.Lexer;
import deal.parser.ParseResult;
import deal.parser.Parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded interpreter for the pure synchronous Deal subset used by generated applications. */
final class CanonicalDealRuntime {
    private static final int MAX_STEPS = 50_000;
    private static final int MAX_CALL_DEPTH = 64;
    private static final int MAX_COLLECTION_SIZE = 2_048;

    private final Map<String, ClassDeclaration> classes = new LinkedHashMap<>();
    private final Map<String, FunctionDeclaration> functions = new LinkedHashMap<>();
    private Map<String, Object> state;
    private int steps;
    private int callDepth;

    CanonicalDealRuntime(String source) {
        String compilerSource = source.replaceAll(
                "(?m)^(\\s*)// @(ui-(?:update|effect|effect-policy|effect-failure))(\\s*)$",
                "$1//  $2$3");
        LexResult lexed = new Lexer(compilerSource, "/generated/app.deal").tokenize();
        ParseResult parsed = new Parser(lexed.tokens(), "/generated/app.deal").parse();
        for (StatementNode statement : parsed.program().statements()) register(statement);
        rejectUnsupportedProgram(parsed.program());
        Object initial = call("initialState", List.of());
        if (!(initial instanceof Map<?, ?> value)) {
            throw new IllegalArgumentException("initialState() must return an application state object");
        }
        state = object(value);
    }

    String snapshotJson() {
        return json(state);
    }

    String dispatch(String handler, String actionType, String[] names, Object[] values) {
        steps = 0;
        if (names.length != values.length) throw new IllegalArgumentException("Action field arrays differ in size");
        ClassDeclaration actionClass = classes.get(simple(actionType));
        if (actionClass == null) throw new IllegalArgumentException("Unknown action class: " + actionType);
        Map<String, Object> action = classDefaults(actionClass);
        for (int index = 0; index < names.length; index++) {
            String fieldName = names[index];
            ClassField field = actionClass.fields().stream()
                    .filter(candidate -> candidate.name().equals(fieldName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown action field: " + fieldName));
            action.put(fieldName, coerce(values[index], field.type()));
        }
        Object next = call(handler, List.of(deepCopy(state), action));
        if (!(next instanceof Map<?, ?> value)) {
            throw new IllegalArgumentException("Deal update did not return an application state object");
        }
        state = object(value);
        return snapshotJson();
    }

    private void register(StatementNode statement) {
        if (statement instanceof ExportDeclaration export) register(export.declaration());
        else if (statement instanceof ClassDeclaration value) classes.put(value.name(), value);
        else if (statement instanceof FunctionDeclaration value) functions.put(value.name(), value);
    }

    private void rejectUnsupportedProgram(ProgramNode program) {
        for (StatementNode statement : program.statements()) {
            StatementNode value = statement instanceof ExportDeclaration export ? export.declaration() : statement;
            if (!(value instanceof ClassDeclaration) && !(value instanceof FunctionDeclaration)) {
                throw new IllegalArgumentException("Generated app.deal may contain only classes and functions at module scope");
            }
            if (value instanceof FunctionDeclaration function && (function.isAsync() || function.isExternal())) {
                throw new IllegalArgumentException("Generated app.deal runtime supports synchronous internal functions only");
            }
        }
        if (!functions.containsKey("initialState")) {
            throw new IllegalArgumentException("Generated app.deal must export initialState()");
        }
    }

    private Object call(String name, List<Object> arguments) {
        step();
        FunctionDeclaration function = functions.get(name);
        if (function == null) return builtin(name, arguments);
        if (arguments.size() != function.params().size()) {
            throw new IllegalArgumentException("Invalid argument count for " + name);
        }
        if (++callDepth > MAX_CALL_DEPTH) throw new IllegalArgumentException("Deal call depth exceeded");
        Environment environment = new Environment(null);
        for (int index = 0; index < arguments.size(); index++) {
            environment.values.put(function.params().get(index).name(), deepCopy(arguments.get(index)));
        }
        try {
            execute(function.body(), environment);
            return null;
        } catch (Returned value) {
            return materialize(value.value, function.returnType());
        } finally {
            callDepth--;
        }
    }

    private Object builtin(String name, List<Object> arguments) {
        return switch (name) {
            case "platformIntText" -> {
                requireArity(name, arguments, 1);
                yield Long.toString(integer(arguments.get(0)));
            }
            case "platformNumberText" -> {
                requireArity(name, arguments, 1);
                yield string(number(arguments.get(0)));
            }
            case "platformPad2" -> {
                requireArity(name, arguments, 1);
                yield String.format(Locale.ROOT, "%02d", integer(arguments.get(0)));
            }
            case "platformMinInt" -> numberBinary(arguments, Math::min, Math::min);
            case "platformMaxInt" -> numberBinary(arguments, Math::max, Math::max);
            case "platformAbsInt" -> {
                requireArity(name, arguments, 1);
                yield Math.abs(integer(arguments.get(0)));
            }
            case "platformClampInt" -> {
                requireArity(name, arguments, 3);
                long value = integer(arguments.get(0));
                yield Math.max(integer(arguments.get(1)), Math.min(value, integer(arguments.get(2))));
            }
            default -> throw new IllegalArgumentException("Unsupported Deal function: " + name);
        };
    }

    private void execute(StatementNode statement, Environment environment) {
        step();
        if (statement instanceof Block block) {
            Environment nested = new Environment(environment);
            for (StatementNode child : block.statements()) execute(child, nested);
        } else if (statement instanceof VariableDeclaration variable) {
            Object value = eval(variable.initializer(), environment, variable.typeAnnotation().orElse(null));
            environment.values.put(variable.name(), value);
        } else if (statement instanceof ReturnStatement value) {
            throw new Returned(value.expr().map(expression -> eval(expression, environment, null)).orElse(null));
        } else if (statement instanceof ExpressionStatement value) {
            eval(value.expr(), environment, null);
        } else if (statement instanceof IfStatement value) {
            if (bool(eval(value.condition(), environment, null))) execute(value.thenBlock(), environment);
            else value.elseBranch().ifPresent(branch -> {
                if (branch instanceof Either.Left<IfStatement, Block> left) execute(left.value(), environment);
                else execute(((Either.Right<IfStatement, Block>) branch).value(), environment);
            });
        } else if (statement instanceof WhileStatement value) {
            while (bool(eval(value.condition(), environment, null))) {
                try { execute(value.body(), environment); }
                catch (Continued ignored) { }
                catch (Broken ignored) { break; }
            }
        } else if (statement instanceof ForOfStatement value) {
            Object source = eval(value.iterable(), environment, null);
            List<?> items = source instanceof String text
                    ? text.codePoints().mapToObj(code -> new String(Character.toChars(code))).toList()
                    : list(source);
            for (Object item : items) {
                Environment nested = new Environment(environment);
                nested.values.put(value.varName(), deepCopy(item));
                try { execute(value.body(), nested); }
                catch (Continued ignored) { }
                catch (Broken ignored) { break; }
            }
        } else if (statement instanceof ForStatement value) {
            Environment nested = new Environment(environment);
            value.init().ifPresent(initializer -> {
                if (initializer instanceof ForInit.VarDecl variable) execute(variable.decl(), nested);
                else eval(((ForInit.AssignExpr) initializer).expr(), nested, null);
            });
            while (value.condition().isEmpty() || bool(eval(value.condition().get(), nested, null))) {
                try { execute(value.body(), nested); }
                catch (Continued ignored) { }
                catch (Broken ignored) { break; }
                value.update().ifPresent(expression -> eval(expression, nested, null));
            }
        } else if (statement instanceof BreakStatement) {
            throw new Broken();
        } else if (statement instanceof ContinueStatement) {
            throw new Continued();
        } else if (statement instanceof DeleteStatement value) {
            delete(value.target(), environment);
        } else {
            throw new IllegalArgumentException("Unsupported Deal statement in generated runtime: " + statement.getClass().getSimpleName());
        }
    }

    private Object eval(ExpressionNode expression, Environment environment, TypeNode expected) {
        step();
        if (expression instanceof LiteralExpr value) return literal(value.value());
        if (expression instanceof IdentifierExpr value) return environment.get(value.name());
        if (expression instanceof ArrayLiteralExpr value) {
            TypeNode itemType = expected instanceof ArrayType array ? array.elementType() : null;
            List<Object> result = new ArrayList<>();
            for (ExpressionNode item : value.elements()) result.add(eval(item, environment, itemType));
            bounded(result);
            return result;
        }
        if (expression instanceof ObjectLiteralExpr value) {
            Map<String, Object> result = expected == null ? new LinkedHashMap<>() : defaults(expected);
            ClassDeclaration declaration = expected == null ? null : classes.get(simple(expected));
            for (Property property : value.properties()) {
                TypeNode propertyType = declaration == null ? null : declaration.fields().stream()
                        .filter(field -> field.name().equals(property.name()))
                        .findFirst().map(ClassField::type).orElse(null);
                result.put(property.name(), eval(property.value(), environment, propertyType));
            }
            return result;
        }
        if (expression instanceof MemberAccessExpr value) {
            Object target = eval(value.object(), environment, null);
            if (value.field().equals("length")) {
                if (target instanceof List<?> items) return (long) items.size();
                if (target instanceof String text) return (long) text.length();
            }
            return map(target).get(value.field());
        }
        if (expression instanceof IndexExpr value) {
            List<?> target = list(eval(value.array(), environment, null));
            int index = Math.toIntExact(integer(eval(value.index(), environment, null)));
            return target.get(index);
        }
        if (expression instanceof UnaryExpr value) {
            Object operand = eval(value.expr(), environment, null);
            return value.op() == UnaryOp.NOT ? !bool(operand) : negate(operand);
        }
        if (expression instanceof BinaryExpr value) return binary(value, environment);
        if (expression instanceof AssignmentExpr value) {
            Object resolved = eval(value.value(), environment, null);
            assign(value.target(), resolved, environment);
            return resolved;
        }
        if (expression instanceof CallExpr value) {
            if (!(value.callee() instanceof IdentifierExpr callee)) {
                throw new IllegalArgumentException("Generated Deal runtime supports direct pure function calls only");
            }
            List<Object> arguments = new ArrayList<>();
            for (ExpressionNode argument : value.args()) arguments.add(eval(argument, environment, null));
            return call(callee.name(), arguments);
        }
        if (expression instanceof HasExpr value) {
            Object target = eval(value.object(), environment, null);
            return map(target).containsKey(value.field()) && map(target).get(value.field()) != null;
        }
        if (expression instanceof TemplateLiteralExpr value) {
            StringBuilder result = new StringBuilder();
            for (ExpressionNode part : value.parts()) result.append(string(eval(part, environment, null)));
            return result.toString();
        }
        throw new IllegalArgumentException("Unsupported Deal expression in generated runtime: " + expression.getClass().getSimpleName());
    }

    private Object binary(BinaryExpr value, Environment environment) {
        if (value.op() == BinaryOp.AND) {
            Object left = eval(value.left(), environment, null);
            return bool(left) && bool(eval(value.right(), environment, null));
        }
        if (value.op() == BinaryOp.OR) {
            Object left = eval(value.left(), environment, null);
            return bool(left) || bool(eval(value.right(), environment, null));
        }
        Object left = eval(value.left(), environment, null);
        Object right = eval(value.right(), environment, null);
        return switch (value.op()) {
            case ADD -> left instanceof String || right instanceof String
                    ? string(left) + string(right) : arithmetic(left, right, '+');
            case SUB -> arithmetic(left, right, '-');
            case MUL -> arithmetic(left, right, '*');
            case DIV -> arithmetic(left, right, '/');
            case MOD -> arithmetic(left, right, '%');
            case POW -> Math.pow(number(left), number(right));
            case EQ -> left == null ? right == null : left.equals(right);
            case NEQ -> !(left == null ? right == null : left.equals(right));
            case LT -> number(left) < number(right);
            case LTE -> number(left) <= number(right);
            case GT -> number(left) > number(right);
            case GTE -> number(left) >= number(right);
            default -> throw new IllegalStateException(value.op().name());
        };
    }

    private void assign(ExpressionNode target, Object value, Environment environment) {
        if (target instanceof IdentifierExpr identifier) {
            environment.set(identifier.name(), deepCopy(value));
        } else if (target instanceof MemberAccessExpr member) {
            map(eval(member.object(), environment, null)).put(member.field(), deepCopy(value));
        } else if (target instanceof IndexExpr index) {
            List<Object> array = mutableList(eval(index.array(), environment, null));
            int position = Math.toIntExact(integer(eval(index.index(), environment, null)));
            if (position == array.size()) {
                bounded(array);
                array.add(deepCopy(value));
            } else {
                array.set(position, deepCopy(value));
            }
        } else {
            throw new IllegalArgumentException("Unsupported Deal assignment target");
        }
    }

    private void delete(ExpressionNode target, Environment environment) {
        if (target instanceof MemberAccessExpr member) {
            map(eval(member.object(), environment, null)).remove(member.field());
        } else if (target instanceof IndexExpr index) {
            mutableList(eval(index.array(), environment, null)).remove(
                    Math.toIntExact(integer(eval(index.index(), environment, null))));
        } else throw new IllegalArgumentException("Unsupported Deal delete target");
    }

    private Map<String, Object> defaults(TypeNode type) {
        ClassDeclaration declaration = classes.get(simple(type));
        return declaration == null ? new LinkedHashMap<>() : classDefaults(declaration);
    }

    private Map<String, Object> classDefaults(ClassDeclaration declaration) {
        Map<String, Object> result = new LinkedHashMap<>();
        Environment empty = new Environment(null);
        for (ClassField field : declaration.fields()) {
            Object value = field.defaultExpr().isPresent()
                    ? eval(field.defaultExpr().get(), empty, field.type())
                    : field.optional() || field.nullable() ? null : defaultPrimitive(field.type());
            result.put(field.name(), value);
        }
        return result;
    }

    private Object materialize(Object value, TypeNode expected) {
        return coerce(value, expected);
    }

    private Object coerce(Object value, TypeNode expected) {
        if (value == null || expected == null) return deepCopy(value);
        if (expected instanceof NullableType nullable) return coerce(value, nullable.innerType());
        if (expected instanceof ArrayType array) {
            List<Object> result = new ArrayList<>();
            for (Object item : list(value)) result.add(coerce(item, array.elementType()));
            bounded(result);
            return result;
        }
        String name = simple(expected);
        if (classes.containsKey(name) && value instanceof Map<?, ?> object) {
            Map<String, Object> result = classDefaults(classes.get(name));
            Map<String, Object> source = object(object);
            for (ClassField field : classes.get(name).fields()) {
                if (source.containsKey(field.name())) result.put(field.name(), coerce(source.get(field.name()), field.type()));
            }
            return result;
        }
        return switch (name) {
            case "int" -> integer(value);
            case "number" -> number(value);
            case "boolean" -> value instanceof Boolean ? value : Boolean.parseBoolean(String.valueOf(value));
            case "string" -> String.valueOf(value);
            default -> deepCopy(value);
        };
    }

    private Object defaultPrimitive(TypeNode type) {
        if (type instanceof ArrayType) return new ArrayList<>();
        return switch (simple(type)) {
            case "int" -> 0L;
            case "number" -> 0.0;
            case "boolean" -> false;
            case "string" -> "";
            default -> defaults(type);
        };
    }

    private Object literal(LiteralValue value) {
        if (value instanceof LiteralValue.NullLiteral) return null;
        if (value instanceof LiteralValue.BooleanLiteral item) return item.value();
        if (value instanceof LiteralValue.IntLiteral item) return item.value();
        if (value instanceof LiteralValue.NumberLiteral item) return item.value();
        return ((LiteralValue.StringLiteral) value).value();
    }

    private Object arithmetic(Object left, Object right, char operator) {
        boolean decimal = left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float;
        if (decimal) {
            double a = number(left), b = number(right);
            return switch (operator) { case '+' -> a + b; case '-' -> a - b; case '*' -> a * b;
                case '/' -> a / b; case '%' -> a % b; default -> throw new IllegalStateException(); };
        }
        long a = integer(left), b = integer(right);
        return switch (operator) { case '+' -> a + b; case '-' -> a - b; case '*' -> a * b;
            case '/' -> a / b; case '%' -> a % b; default -> throw new IllegalStateException(); };
    }

    private Object numberBinary(List<Object> arguments, LongBinary longs, DoubleBinary doubles) {
        requireArity("numeric function", arguments, 2);
        Object left = arguments.get(0), right = arguments.get(1);
        if (left instanceof Double || left instanceof Float || right instanceof Double || right instanceof Float) {
            return doubles.apply(number(left), number(right));
        }
        return longs.apply(integer(left), integer(right));
    }

    private Object negate(Object value) {
        return value instanceof Double || value instanceof Float ? -number(value) : -integer(value);
    }

    private static void requireArity(String name, List<?> values, int size) {
        if (values.size() != size) throw new IllegalArgumentException(name + " expects " + size + " arguments");
    }

    private void step() {
        if (++steps > MAX_STEPS) throw new IllegalArgumentException("Deal execution budget exceeded");
    }

    private static void bounded(List<?> values) {
        if (values.size() >= MAX_COLLECTION_SIZE) throw new IllegalArgumentException("Deal collection size exceeded");
    }

    private static String simple(TypeNode type) {
        if (type instanceof NullableType nullable) return simple(nullable.innerType());
        if (type instanceof ArrayType array) return simple(array.elementType());
        if (type instanceof NamedType named) return simple(named.name());
        if (type instanceof QualifiedType qualified) return qualified.typeName();
        return type.toString();
    }

    private static String simple(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private static long integer(Object value) {
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private static double number(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean result) return result;
        throw new IllegalArgumentException("Expected boolean, got " + value);
    }

    private static String string(Object value) {
        if (value == null) return "null";
        if (value instanceof Double number && number == Math.rint(number)) return Long.toString(number.longValue());
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> result)) throw new IllegalArgumentException("Expected object, got " + value);
        return (Map<String, Object>) result;
    }

    private static Map<String, Object> object(Map<?, ?> value) {
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(String.valueOf(key), deepCopy(item)));
        return result;
    }

    private static List<?> list(Object value) {
        if (!(value instanceof List<?> result)) throw new IllegalArgumentException("Expected array, got " + value);
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> mutableList(Object value) {
        if (!(value instanceof List<?> result)) throw new IllegalArgumentException("Expected mutable array, got " + value);
        return (List<Object>) result;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) return object(map);
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            list.forEach(item -> copy.add(deepCopy(item)));
            return copy;
        }
        return value;
    }

    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof String text) return quote(text);
        if (value instanceof Boolean || value instanceof Number) return String.valueOf(value);
        if (value instanceof List<?> list) {
            StringBuilder out = new StringBuilder("[");
            for (int index = 0; index < list.size(); index++) {
                if (index > 0) out.append(',');
                out.append(json(list.get(index)));
            }
            return out.append(']').toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder out = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                out.append(quote(String.valueOf(entry.getKey()))).append(':').append(json(entry.getValue()));
                first = false;
            }
            return out.append('}').toString();
        }
        throw new IllegalArgumentException("State is not JSON-compatible: " + value.getClass().getName());
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(character);
            }
        }
        return out.append('"').toString();
    }

    private static final class Environment {
        final Environment parent;
        final Map<String, Object> values = new LinkedHashMap<>();
        Environment(Environment parent) { this.parent = parent; }
        Object get(String name) {
            if (values.containsKey(name)) return values.get(name);
            if (parent != null) return parent.get(name);
            throw new IllegalArgumentException("Unknown Deal value: " + name);
        }
        void set(String name, Object value) {
            if (values.containsKey(name)) values.put(name, value);
            else if (parent != null) parent.set(name, value);
            else throw new IllegalArgumentException("Unknown Deal assignment target: " + name);
        }
    }

    private static final class Returned extends RuntimeException { final Object value; Returned(Object value) { this.value = value; } }
    private static final class Broken extends RuntimeException {}
    private static final class Continued extends RuntimeException {}
    @FunctionalInterface private interface LongBinary { long apply(long left, long right); }
    @FunctionalInterface private interface DoubleBinary { double apply(double left, double right); }
}
