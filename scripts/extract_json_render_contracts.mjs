#!/usr/bin/env node
/**
 * Build-time, provenance-checked extraction of the pinned json-render catalogs.
 *
 * This is deliberately not part of the Android app or the generated-app runtime.
 * It executes the upstream Zod schemas in a supplied, already checked-out source
 * tree, emits a deterministic artifact, and refuses a source revision other than
 * the one frozen by the UI-first inventory. Run it with Node's TypeScript type
 * stripping enabled, for example:
 *
 *   node --experimental-strip-types scripts/extract_json_render_contracts.mjs \
 *     --source-root /path/to/json-render --check
 */

import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, relative, resolve } from "node:path";
import { pathToFileURL } from "node:url";

const PINNED_REVISION = "3ad381881194e7011ad3ccd6d668033495a06c29";
const EXPECTED_COUNTS = Object.freeze({
  shadcn: 36,
  "react-native": 26,
  "jev-playground": 2,
});
const DEFAULT_OUTPUT = "tooling/deal-ui-pack/vercel-json-render-contract-v1.json";

function fail(message) {
  throw new Error(message);
}

function sha256(contents) {
  return createHash("sha256").update(contents).digest("hex");
}

function parseArgs(argv) {
  const options = { sourceRoot: null, output: DEFAULT_OUTPUT, check: false };
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--source-root") {
      options.sourceRoot = argv[++index] ?? null;
    } else if (argument === "--output") {
      options.output = argv[++index] ?? null;
    } else if (argument === "--check") {
      options.check = true;
    } else if (argument === "--help" || argument === "-h") {
      process.stdout.write(
        "Usage: node --experimental-strip-types scripts/extract_json_render_contracts.mjs " +
          "--source-root /path/to/json-render [--output path] [--check]\\n",
      );
      process.exit(0);
    } else {
      fail(`unknown argument: ${argument}`);
    }
  }
  if (!options.sourceRoot) {
    fail("--source-root is required; no network clone or branch fallback is performed");
  }
  if (!options.output) {
    fail("--output needs a path");
  }
  return options;
}

function readSourceFile(sourceRoot, relativePath) {
  const path = join(sourceRoot, relativePath);
  if (!existsSync(path) || !statSync(path).isFile()) {
    fail(`pinned json-render source is missing ${relativePath}`);
  }
  const contents = readFileSync(path);
  return { path, contents, sha256: sha256(contents) };
}

function checkedRevision(sourceRoot) {
  let revision;
  try {
    revision = execFileSync("git", ["-C", sourceRoot, "rev-parse", "HEAD"], {
      encoding: "utf8",
      stdio: ["ignore", "pipe", "pipe"],
    }).trim();
  } catch {
    fail(`--source-root is not a readable git checkout: ${sourceRoot}`);
  }
  if (revision !== PINNED_REVISION) {
    fail(
      `expected json-render ${PINNED_REVISION}, received ${revision}; ` +
        "do not silently extract a different upstream contract",
    );
  }
  return revision;
}

function zodDefinition(schema) {
  if (!schema || typeof schema !== "object") {
    fail("catalog prop is not a Zod schema object");
  }
  const definition = schema.def ?? schema._def;
  if (!definition || typeof definition !== "object" || typeof definition.type !== "string") {
    fail("unsupported Zod schema representation; update the extractor deliberately");
  }
  return definition;
}

function serializableDefault(value) {
  if (value === null || ["string", "number", "boolean"].includes(typeof value)) {
    return value;
  }
  return "<non-literal-default>";
}

function unwrap(schema) {
  let current = schema;
  const modifiers = { nullable: false, optional: false };
  let defaultValue;
  for (;;) {
    const definition = zodDefinition(current);
    if (definition.type === "nullable") {
      modifiers.nullable = true;
      current = definition.innerType;
      continue;
    }
    if (definition.type === "optional") {
      modifiers.optional = true;
      current = definition.innerType;
      continue;
    }
    if (definition.type === "default") {
      const rawDefault =
        typeof definition.defaultValue === "function"
          ? definition.defaultValue()
          : definition.defaultValue;
      defaultValue = serializableDefault(rawDefault);
      current = definition.innerType;
      continue;
    }
    return { schema: current, modifiers, defaultValue };
  }
}

function describeZod(schema) {
  const { schema: unwrapped, modifiers, defaultValue } = unwrap(schema);
  const definition = zodDefinition(unwrapped);
  const result = {
    required: !modifiers.optional,
    nullable: modifiers.nullable,
  };
  if (defaultValue !== undefined) {
    result.default = defaultValue;
  }

  switch (definition.type) {
    case "string":
    case "number":
    case "boolean":
    case "unknown":
    case "any":
    case "null":
      return { ...result, type: definition.type };
    case "enum":
      return {
        ...result,
        type: "enum",
        values: Object.values(definition.entries).sort(),
      };
    case "literal": {
      const values = Array.isArray(definition.values)
        ? definition.values
        : definition.value === undefined
          ? []
          : [definition.value];
      return { ...result, type: "literal", values };
    }
    case "array":
      return { ...result, type: "array", element: describeZod(definition.element) };
    case "object": {
      const shape = definition.shape ?? unwrapped.shape;
      if (!shape || typeof shape !== "object") {
        fail("Zod object schema has no readable shape");
      }
      return { ...result, type: "object", fields: describeShape(shape) };
    }
    case "record":
      return {
        ...result,
        type: "record",
        key: describeZod(definition.keyType),
        value: describeZod(definition.valueType),
      };
    case "union":
      return {
        ...result,
        type: "union",
        options: definition.options.map((option) => describeZod(option)),
      };
    case "tuple":
      return {
        ...result,
        type: "tuple",
        items: definition.items.map((item) => describeZod(item)),
      };
    default:
      fail(`unsupported Zod type '${definition.type}'; add a deliberate extractor rule`);
  }
}

function describeShape(shape) {
  return Object.fromEntries(
    Object.entries(shape)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([name, schema]) => [name, describeZod(schema)]),
  );
}

function describeCatalog(catalog, origin, expectedCount) {
  if (!catalog || typeof catalog !== "object") {
    fail(`${origin} catalog did not export an object`);
  }
  const names = Object.keys(catalog).sort();
  if (names.length !== expectedCount) {
    fail(`${origin} expected ${expectedCount} components, found ${names.length}`);
  }
  return Object.fromEntries(
    names.map((name) => {
      const component = catalog[name];
      if (!component?.props) {
        fail(`${origin}.${name} has no props schema`);
      }
      const propsDefinition = zodDefinition(component.props);
      if (propsDefinition.type !== "object") {
        fail(`${origin}.${name}.props must be a Zod object`);
      }
      const shape = propsDefinition.shape ?? component.props.shape;
      return [
        name,
        {
          props: describeShape(shape),
          events: [...(component.events ?? [])].sort(),
          slots: [...(component.slots ?? [])],
        },
      ];
    }),
  );
}

function playgroundComponents(grammar) {
  const requiredMarkers = [
    '"Metric"',
    '"BarGraph"',
    "changeType",
    "sales_chart",
  ];
  for (const marker of requiredMarkers) {
    if (!grammar.includes(marker)) {
      fail(`pinned Playground grammar no longer contains expected marker ${marker}`);
    }
  }
  // grammar.ts is a concrete-candidate catalog, rather than a universal schema.
  // These are the observed prop shapes of its Metric and BarGraph candidates.
  return {
    Metric: {
      sourceKind: "concrete-candidate-observation",
      props: {
        change: { required: true, nullable: true, type: "string" },
        changeType: {
          required: true,
          nullable: false,
          type: "enum",
          values: ["negative", "positive"],
        },
        label: { required: true, nullable: false, type: "string" },
        prefix: { required: true, nullable: true, type: "string" },
        suffix: { required: true, nullable: true, type: "string" },
        value: { required: true, nullable: false, type: "string" },
      },
      events: [],
      slots: [],
    },
    BarGraph: {
      sourceKind: "concrete-candidate-observation",
      props: {
        data: {
          required: true,
          nullable: false,
          type: "array",
          element: {
            required: true,
            nullable: false,
            type: "object",
            fields: {
              label: { required: true, nullable: false, type: "string" },
              value: { required: true, nullable: false, type: "number" },
            },
          },
        },
        title: { required: true, nullable: false, type: "string" },
      },
      events: [],
      slots: [],
    },
  };
}

async function buildContract(sourceRoot) {
  const revision = checkedRevision(sourceRoot);
  const reactNativeCatalog = readSourceFile(sourceRoot, "packages/react-native/src/catalog.ts");
  const shadcnCatalog = readSourceFile(sourceRoot, "packages/shadcn/src/catalog.ts");
  const grammar = readSourceFile(sourceRoot, "apps/web/lib/jev/grammar.ts");

  let reactNativeModule;
  let shadcnModule;
  try {
    reactNativeModule = await import(pathToFileURL(reactNativeCatalog.path).href);
    shadcnModule = await import(pathToFileURL(shadcnCatalog.path).href);
  } catch (error) {
    fail(
      "unable to execute pinned TypeScript/Zod catalogs. Run this with Node's " +
        `--experimental-strip-types flag and install the source checkout dependencies: ${error.message}`,
    );
  }

  const components = {
    shadcn: describeCatalog(
      shadcnModule.shadcnComponentDefinitions,
      "shadcn",
      EXPECTED_COUNTS.shadcn,
    ),
    "react-native": describeCatalog(
      reactNativeModule.standardComponentDefinitions,
      "react-native",
      EXPECTED_COUNTS["react-native"],
    ),
    "jev-playground": playgroundComponents(grammar.contents.toString("utf8")),
  };
  if (Object.keys(components["jev-playground"]).length !== EXPECTED_COUNTS["jev-playground"]) {
    fail("unexpected Jev Playground component denominator");
  }

  return {
    schemaVersion: "deal-studio-json-render-contract-extraction-v1",
    source: {
      repository: "vercel-labs/json-render",
      revision,
      extraction: "executed-zod-schemas-for-catalogs; concrete-candidate-observation-for-playground",
      files: {
        "apps/web/lib/jev/grammar.ts": grammar.sha256,
        "packages/react-native/src/catalog.ts": reactNativeCatalog.sha256,
        "packages/shadcn/src/catalog.ts": shadcnCatalog.sha256,
      },
    },
    requiredRows: Object.values(EXPECTED_COUNTS).reduce((left, right) => left + right, 0),
    origins: EXPECTED_COUNTS,
    components,
  };
}

async function main() {
  const options = parseArgs(process.argv.slice(2));
  const sourceRoot = resolve(options.sourceRoot);
  const output = resolve(options.output);
  const contract = await buildContract(sourceRoot);
  const rendered = `${JSON.stringify(contract, null, 2)}\n`;
  if (options.check) {
    if (!existsSync(output)) {
      fail(`checked contract artifact is missing: ${relative(process.cwd(), output)}`);
    }
    const existing = readFileSync(output, "utf8");
    if (existing !== rendered) {
      fail(
        `checked contract artifact is stale: ${relative(process.cwd(), output)}; ` +
          "regenerate it from the pinned source after reviewing the diff",
      );
    }
    process.stdout.write(`json-render contract extraction matches ${relative(process.cwd(), output)}\n`);
    return;
  }
  mkdirSync(dirname(output), { recursive: true });
  writeFileSync(output, rendered, "utf8");
  process.stdout.write(`wrote ${relative(process.cwd(), output)}\n`);
}

main().catch((error) => {
  process.stderr.write(`json-render contract extraction failed: ${error.message}\n`);
  process.exitCode = 1;
});
