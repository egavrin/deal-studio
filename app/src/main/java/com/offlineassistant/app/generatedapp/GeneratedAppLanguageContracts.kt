package com.offlineassistant.app.generatedapp

internal object GeneratedAppLanguageContracts {
    const val VERSION = "generated-app-language-v2"
    const val A2UI_VERSION = "generated-app-a2ui-v1"

    val a2uiWire: String = """
        Contract version: $A2UI_VERSION.
        Output exactly one raw A2UI JSON document. Never return Markdown, prose, comments, XML or executable code.
        The document uses this closed envelope:
        {
          "version":"v1.0",
          "createSurface":{
            "surfaceId":"generated_app",
            "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
            "catalogs":["${A2UiParser.BASIC_CATALOG_ID}","${A2UiParser.ASSISTANT_CATALOG_ID}"],
            "components":[...],
            "dataModel":{...}
          }
        }

        Component rules:
        - Every component is a flat object with unique "id", catalog "component", and catalog properties.
        - Exactly one component has id "root". Parent properties reference child IDs; never nest component objects.
        - The graph is connected, acyclic, 5..64 nodes and at most depth 8.
        - Use one InteractiveSurface for generated DEAL behavior. Never use the legacy surface.app token.
        - Use rich catalog composition around the interactive surface: visible hierarchy, status/HUD and controls.
        - Values may be literals or bindings {"path":"/pointer"}. Runtime DEAL state is available under:
          /app/title, /app/status, /app/primaryLabel, /app/items, /app/columns and /app/custom/<global>.
        - The primary DEAL reset action is {"event":{"name":"onPrimary","context":{}}}.
        - A grid item action is {"event":{"name":"onItem","context":{"index":0}}}; the InteractiveSurface
          itself normally owns grid and pointer dispatch, so do not create one button per generated game cell.
        - Media URLs must be HTTPS on assets.example.invalid, example.invalid, commons.wikimedia.org or
          upload.wikimedia.org. Media, code, chart, map and interactive components require descriptions.
        - Never emit arbitrary colors, dimensions, HTML, SVG, JavaScript, Android classes or unknown properties.

        Catalog signatures. A property without ? is required:
        ${A2UiCatalog.signatures}

        Canonical generated-app surface:
        {
          "version":"v1.0",
          "createSurface":{
            "surfaceId":"generated_app",
            "catalogId":"${A2UiParser.ASSISTANT_CATALOG_ID}",
            "catalogs":["${A2UiParser.BASIC_CATALOG_ID}","${A2UiParser.ASSISTANT_CATALOG_ID}"],
            "components":[
              {"id":"title","component":"Text","text":{"path":"/app/title"},"variant":"h2","tone":"inverse"},
              {"id":"status","component":"Badge","text":{"path":"/app/status"},"tone":"info"},
              {"id":"header","component":"Row","children":["title","status"],"align":"center","justify":"spaceBetween","gap":"sm"},
              {"id":"game","component":"InteractiveSurface","module_id":"deal","aspect":"wide","input_mode":"realtime","description":"Generated interactive application"},
              {"id":"reset_label","component":"Text","text":{"path":"/app/primaryLabel"},"variant":"label"},
              {"id":"reset","component":"Button","child":"reset_label","action":{"event":{"name":"onPrimary","context":{}}},"variant":"filled"},
              {"id":"root","component":"Column","children":["header","game","reset"],"gap":"md","align":"stretch"}
            ],
            "dataModel":{}
          }
        }
    """.trimIndent()

    val uiDsl: String = """
        Contract version: $VERSION.
        Output exactly one Compact UI DSL expression. This language is not JSON, XML, Markdown, Kotlin or JavaScript.

        Lexical grammar:
        - Identifiers contain letters, digits, dot, underscore or hyphen.
        - Enum values are bare identifiers. Never quote values.
        - State slots start with a dollar sign, for example title=${'$'}title.
        - Whitespace is optional. Comments and trailing text are forbidden.

        Syntax grammar (EBNF):
        expression = layout ;
        layout = layoutId, [ "(", argument, { ",", argument }, ")" ], "[", node, { ",", node }, "]" ;
        node = layout | component ;
        component = componentId, [ "(", argument, { ",", argument }, ")" ] ;
        argument = argumentName, "=", ( enumValue | stateSlot ) ;

        Layout catalog:
        - column(gap=none|xs|sm|md|lg, align=start|center|end|stretch, padding=none|sm|md|lg)
        - row(gap=none|xs|sm|md|lg, align=start|center|end|stretch, padding=none|sm|md|lg)
        - stack(align=start|center|end|stretch, padding=none|sm|md|lg)
        - grid2(gap=none|xs|sm|md|lg, padding=none|sm|md|lg)
        - section(tone=plain|soft|accent|dark, gap=none|xs|sm|md|lg, padding=none|sm|md|lg)
        Layouts accept only enum arguments, must have at least one child and may nest to depth 6.

        Component catalog:
        - text.heading(title=${'$'}title, style=display|title|compact, tone=default|primary|inverse,
          align=start|center|end|stretch)
        - text.status(status=${'$'}status, style=body|badge|caption,
          tone=default|muted|primary|positive|warning|inverse, align=start|center|end|stretch)
        - text.label(text=${'$'}title|${'$'}status|${'$'}primaryLabel, style=body|badge|caption,
          tone=default|muted|primary|inverse, align=start|center|end|stretch)
        - surface.app(grid=tiles|outline|neon, gap=none|xs|sm|md|lg,
          frame=none|soft|bordered, ratio=scene|square|wide)
        - control.button(onPrimary=${'$'}onPrimary, primaryLabel=${'$'}primaryLabel,
          variant=filled|tonal|outline, size=compact|regular, icon=none|restart|play,
          tone=default|muted|primary|positive|warning|inverse)
        - decor.divider(tone=soft|strong|primary)
        - decor.spacer(size=none|xs|sm|md|lg)
        Every listed argument is optional except the state bindings shown for text and control components.

        Structural invariants:
        - The expression contains 4 to 16 components total.
        - It contains exactly one text.heading, one text.status, one surface.app and one control.button.
        - surface.app is a leaf. Never put children inside it and never replace it with app-specific components.
        - Bindings must use the exact slots shown. Literal user-facing strings are forbidden in UI DSL.
        - The UI describes presentation only. All app state and behavior comes from DEAL through the four slots.

        Canonical valid expression:
        section(tone=dark,gap=sm,padding=md)[
          row(gap=sm,align=center)[
            text.heading(title=${'$'}title,style=title,tone=inverse,align=start),
            text.status(status=${'$'}status,style=badge,tone=inverse,align=end)
          ],
          surface.app(grid=tiles,gap=xs,frame=soft,ratio=wide),
          control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel,variant=filled,size=regular,icon=restart,tone=primary)
        ]
    """.trimIndent()

    val dealCore: String = """
        Contract version: $VERSION.
        Output exactly one complete raw DEAL module. DEAL is a strict typed sandbox language, not JavaScript.

        Lexical and statement grammar:
        - A module starts with a typed global declaration: let name: type = expression;
        - Variable declaration: let name: type = expression;
        - Function declaration: function name(arg: type, ...): returnType { statements }
        - Assignment: name = expression; or array[index] = expression;
        - Control flow: if (condition) { statements } else { statements }
        - Bounded loop: while (condition) { statements }
        - Return: return expression; or return null;
        - Every declaration, assignment, call used as a statement and return ends with a semicolon.
        - Allowed types: int, string, boolean, null, int[], string[], boolean[], int[][] and string[][].
        - Literals: integer, double-quoted string, true, false, null and fixed array literals.

        Expression grammar:
        - Indexing: array[index]. The only legal member access is array.length.
        - Arithmetic: + - * / %. Unary operators: - and !.
        - Comparison: < <= > >=. Equality: == === != !==.
        - Boolean AND: & or &&. Boolean OR: | or ||.
        - Parentheses may group expressions.
        - Calls are limited to generated helper functions and builtins abs(int), min(int,int),
          max(int,int), clamp(int,int,int).
        - Integer division truncates. Decimal numbers and floating-point types are unavailable.

        Forbidden syntax:
        - Never emit Markdown fences, prose, JSON, comments, imports, packages or classes.
        - Never use const, var, for, do, switch, when, break, continue, try, throw, new or this.
        - Never use ++, --, +=, -=, *=, /=, ternary operators, arrows or string interpolation.
        - Never use objects, maps, lambdas, dynamic arrays, collection methods or platform APIs.
        - Never use property access except array.length. In particular do not use .push, .map, .join,
          .toString, Math.*, fields or methods.
        - Do not read the clock, network, storage, sensors or random state.

        Execution limits and semantics:
        - Source must be at most 10000 characters, 2500 lexer tokens and 256 parsed statements.
        - Arrays are fixed-size mutable values. Update an element with array[index] = value or reset the
          complete array with a new literal of the same shape.
        - Every while loop must visibly advance a counter and terminate well within the execution budget.
        - Keep helpers compact and deterministic. Every required action must end with return null.
        - Use English for title, status, labels and visible shape text.
    """.trimIndent()

    val gridProfile: String = """
        Profile GRID is for turn-based boards, cell games, counters, quizzes and discrete choices.
        Required globals, with these exact names and types:
        - title:string, status:string, primaryLabel:string
        - items:string[] with 1..36 entries, each at most 24 characters
        - columns:int in 1..6; items.length must be divisible by columns
        Required actions:
        - function onItem(index: int): null
        - function onPrimary(): null
        onItem(0) must change items or status. onPrimary must restore items exactly to their initial value.
        Own the complete rules in DEAL. Board games need occupied-cell guards, turns, win/draw state and reset.
    """.trimIndent()

    val realtimeCanvasProfile: String = """
        Profile REALTIME_CANVAS is for motion, physics, projectiles, dragging, Pong, Arkanoid and action games.
        Coordinates and dimensions are integer scene units.
        Required globals, with these exact names and types:
        - title:string, status:string, primaryLabel:string
        - canvasWidth:int in 160..2000, canvasHeight:int in 120..2000
        - canvasBackground:string as #RRGGBB or #RRGGBBAA
        - shapeKinds:string[], shapeX:int[], shapeY:int[], shapeW:int[], shapeH:int[],
          shapeColors:string[], shapeLabels:string[]
        All seven shape arrays must have the same fixed length in 1..48.
        Prefer 32 or fewer shapes for complex scenes so every parallel literal can be counted and kept equal;
        use the 48-shape ceiling only when the request truly requires it.
        shapeKinds entries are rect, circle, line or text. Colors are #RRGGBB or #RRGGBBAA.
        Labels are at most 24 characters. Positions may be from -canvasSize through 2*canvasSize.
        Required actions:
        - function onTick(deltaMs: int): null
        - function onPointer(x: int, y: int, phase: int): null
        - function onPrimary(): null
        Pointer phase is 0 for down, 1 for move and 2 for up.
        onPointer(centerX,height-24,0) must observably change a shape array, status or scalar simulation state;
        it must also activate a ready or paused scene when movement requires activation. The following onTick(16)
        must observably change the activated scene. onPrimary must restore the complete initial canvas:
        dimensions, background and every shape array. Reset all internal simulation state too.
        Implement movement, bounds, collisions, score or lives, and win/lose state entirely in DEAL.
        Keep scene state in scalar globals and fixed parallel arrays; copy changed scalar positions into
        shapeX/shapeY during onTick. There is no renderer API to call.
    """.trimIndent()

    val gridReference: String = """
        Complete valid GRID grammar reference (syntax reference only; do not copy its app design):
        let title: string = "Tap Board";
        let status: string = "Ready";
        let primaryLabel: string = "Reset";
        let items: string[] = ["", "", "", ""];
        let columns: int = 2;
        function onItem(index: int): null {
          if (items[index] === "") { items[index] = "X"; } else { items[index] = ""; }
          status = "Board updated";
          return null;
        }
        function onPrimary(): null {
          items = ["", "", "", ""];
          status = "Ready";
          return null;
        }
    """.trimIndent()

    val realtimeCanvasReference: String = """
        Complete valid REALTIME_CANVAS grammar reference (syntax reference only; design the requested app):
        let title: string = "Motion Demo";
        let status: string = "Drag the paddle";
        let primaryLabel: string = "Reset";
        let canvasWidth: int = 1000;
        let canvasHeight: int = 600;
        let canvasBackground: string = "#0F172A";
        let shapeKinds: string[] = ["rect", "circle"];
        let shapeX: int[] = [30, 485];
        let shapeY: int[] = [250, 285];
        let shapeW: int[] = [30, 30];
        let shapeH: int[] = [100, 30];
        let shapeColors: string[] = ["#38BDF8", "#F8FAFC"];
        let shapeLabels: string[] = ["", ""];
        let ballX: int = 485;
        let velocityX: int = 6;
        function onTick(deltaMs: int): null {
          ballX = ballX + (velocityX * deltaMs / 16);
          if ((ballX <= 0) | (ballX >= 970)) { velocityX = -velocityX; }
          ballX = clamp(ballX, 0, 970);
          shapeX[1] = ballX;
          return null;
        }
        function onPointer(x: int, y: int, phase: int): null {
          shapeY[0] = clamp(y - 50, 0, 500);
          status = "Paddle moved";
          return null;
        }
        function onPrimary(): null {
          shapeX = [30, 485];
          shapeY = [250, 285];
          shapeW = [30, 30];
          shapeH = [100, 30];
          shapeKinds = ["rect", "circle"];
          shapeColors = ["#38BDF8", "#F8FAFC"];
          shapeLabels = ["", ""];
          ballX = 485;
          velocityX = 6;
          status = "Drag the paddle";
          return null;
        }
    """.trimIndent()

    fun dealProfiles(profile: GeneratedAppProfile?): String = when (profile) {
        GeneratedAppProfile.GRID -> "$gridProfile\n\n$gridReference"

        GeneratedAppProfile.REALTIME_CANVAS -> "$realtimeCanvasProfile\n\n$realtimeCanvasReference"

        null -> """
            Select exactly one profile from the request. Never mix their globals.
            $gridProfile

            $realtimeCanvasProfile

            $gridReference

            $realtimeCanvasReference
        """.trimIndent()
    }
}
