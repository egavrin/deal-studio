package com.offlineassistant.app.generatedapp

internal object GeneratedAppLanguageContracts {
    const val VERSION = "generated-app-language-v4"
    const val A2UI_VERSION = "generated-app-a2ui-v3"

    /**
     * Structural decoding contract for the compact on-device UI model. The model still
     * chooses composition and visual properties; invalid nodes, bindings and unfinished
     * trees are excluded from its token distribution by llama.cpp.
     */
    val compactUiGrammar: String = """
        root ::= clean | dark | accent

        clean ::= "column(gap=" gap ")[row(gap=" gap ",align=" align ")[text.heading(title=${'$'}title,style=" heading-style "),text.status(status=${'$'}status,style=" status-style ",tone=" tone ",align=" align ")],surface.app(grid=" grid ",gap=" gap ",frame=" frame ",ratio=" ratio "),control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel,variant=" variant ",icon=" icon ",size=compact)]"
        dark ::= "section(tone=dark,padding=" padding ",gap=" gap ")[text.heading(title=${'$'}title,style=" heading-style ",tone=inverse),text.status(status=${'$'}status,style=" status-style ",tone=inverse),surface.app(grid=" grid ",gap=" gap ",frame=" frame ",ratio=" ratio "),decor.divider(tone=strong),control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel,variant=" variant ",icon=" icon ")]"
        accent ::= "column(gap=" gap ")[section(tone=accent,padding=" padding ",gap=" gap ")[text.heading(title=${'$'}title,style=" heading-style "),text.status(status=${'$'}status,style=" status-style ",tone=" tone ")],stack(align=" align ")[surface.app(grid=" grid ",gap=" gap ",frame=" frame ",ratio=" ratio "),text.label(text=" label-binding ",style=badge,tone=" tone ",align=" align ")],control.button(onPrimary=${'$'}onPrimary,primaryLabel=${'$'}primaryLabel,variant=" variant ",icon=" icon ")]"

        gap ::= "none" | "xs" | "sm" | "md" | "lg"
        padding ::= "none" | "sm" | "md" | "lg"
        align ::= "start" | "center" | "end" | "stretch"
        heading-style ::= "display" | "title" | "compact"
        status-style ::= "body" | "badge" | "caption"
        tone ::= "default" | "muted" | "primary" | "positive" | "warning" | "inverse"
        grid ::= "tiles" | "outline" | "neon"
        frame ::= "none" | "soft" | "bordered"
        ratio ::= "scene" | "square" | "wide"
        variant ::= "filled" | "tonal" | "outline"
        icon ::= "none" | "restart" | "play"
        label-binding ::= "${'$'}title" | "${'$'}status" | "${'$'}primaryLabel"
    """.trimIndent()

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
        - The graph is acyclic, 3..96 nodes and at most depth 10. All normal components are connected to root.
          A BottomSheet may be a separate validated overlay root; all of its content must be connected to it.
        - Before returning, trace child references from root and each BottomSheet overlay root. Verify that every
          declared component ID is reached exactly once. Remove abandoned alternatives and unused components.
        - Use zero or one InteractiveSurface. Use it for boards, free-form graphics, pointer interaction or animation.
          Forms, task lists, dashboards and multi-screen utilities should normally use native A2UI components and
          named DEAL events instead of forcing their content into a game-like surface.
        - Use rich catalog composition with visible hierarchy, status/HUD and controls. Implement the requested
          product surfaces, not a legend of feature names. Never turn words such as navigation, weather, progress,
          checklist, image or settings into a decorative row of icons and labels.
        - Match requested concepts to their catalog components directly: multi-screen flows use Navigation;
          editable values use input controls; collections use List, Timeline, DataTable or ImageGallery; temporary
          secondary flows use Modal, BottomSheet or Menu; photos use Image or ImageGallery. Do not approximate a
          supported component with arbitrary Rows, Columns, Text and Icon nodes.
        - Keep visual density intentional. Use one dominant title, at most one hero image per route, semantic icons
          only where they improve scanning, and short labels that do not crowd one horizontal row. Prefer Card,
          spacing and hierarchy over decorative repetition.
        - Render /app/title, /app/status and /app/primaryLabel at most once each. Expose onPrimary at most once.
        - Never copy an initial runtime value such as the current status, counter value or reset label into a text
          literal. It becomes stale after an action. Bind dynamic text to /app, or use a stable semantic caption.
        - tone=inverse is valid only below a Card with tone=dark; never place inverse text on the root surface.
        - Values may be literals or bindings {"path":"/pointer"}. Runtime DEAL state is available under:
          /app/title, /app/status, /app/primaryLabel, /app/items, /app/columns, /app/custom/<global>,
          and structured TRACKER resources below /app/resources/<name>. Resource values are read-only in A2UI;
          mutate them only through named DEAL events.
        - The entire /app subtree is read-only presentation state. TextField.value, DateTimeInput.value,
          CheckBox.checked, ChoicePicker.value and Slider.value must never bind to /app. Bind each editable control
          to a non-/app client path such as /form/amount and initialize that path in dataModel. Input updates are
          synchronous, so a DEAL action may read the same client binding in its context, for example
          {"event":{"name":"onAdd","context":{"amount":{"path":"/form/amount"}}}}.
        - Read-only controls whose changes are implemented by DEAL events, such as Stepper and Checklist, may
          display /app/resources values because their actions mutate DEAL state rather than writing the binding.
        - Never declare app, /app or /app/... keys in dataModel. The host injects the complete read-only /app tree.
          Counter resource progress is an integer percentage in 0..100; a Progress or ProgressRing bound to it uses
          max=100. Prefer binding max to the counter target when rendering its raw value instead.
        - The primary DEAL reset action is {"event":{"name":"onPrimary","context":{}}}.
        - A grid item action is {"event":{"name":"onItem","context":{"index":0}}}; the InteractiveSurface
          itself normally owns grid and pointer dispatch, so do not create one button per generated game cell.
        - Inside a List or Timeline template, {"path":"@item"} or {"path":"."} resolves the current item and
          {"path":"@index"} resolves its index. Use relative property paths for fields of object items.
        - Additional DEAL events use onPascalCase names and at most six scalar context values. Prefer conventional
          onSubmit(value), onAdd(value), onDelete(index), onToggle(index), onSelect(index), onSave(), onNext(),
          onPrevious(), onSearch(query) and onConfirm() names. The context keys must exactly match DEAL parameters.
        - A family of choices that differs by a scalar value must call one parameterized DEAL event and pass the
          exact value in context. For example, 250 ml and 500 ml controls both call onAdjust(amount). Their visible
          numbers must equal the context values. Do not invent zero-argument wrappers such as onSmall/onMedium that
          hide different constants from presentation.
        - Local renderer functions use {"functionCall":{"call":"name","args":{...}}}. Supported calls are:
          setValue(path,value), toggleValue(path), appendValue(path,value), removeAt(path,index),
          moveItem(path,from,to), navigate(route), showOverlay(id), hideOverlay(id), showSnackbar(message),
          and openUrl(url). They are bounded host operations, not executable code.
        - Navigation provides two to five real routes with distinct child screens. A requested multi-screen app is
          invalid if it only displays route names without a Navigation component.
        - Modal and BottomSheet visibility is controlled by showOverlay and hideOverlay. Every overlay needs a
          reachable opening control whose action is showOverlay with the exact overlay_id; its close or confirm
          control uses hideOverlay. Use overlays for secondary flows instead of permanently expanding hidden content.
        - Media URLs must be HTTPS on Wikimedia, images.unsplash.com, plus.unsplash.com or images.pexels.com.
          Never invent an image URL. Use Image only when the request or supplied data contains a real URL; otherwise
          use a semantic Icon and a graceful placeholder. Media, code, chart, map and interactive components require
          descriptions for accessibility.
        - Supported Icon names are the closed names in the Icon catalog signature. Choose a specific semantic icon
          instead of using info as decoration. Icons must have an accessibility description unless redundant.
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
              {"id":"title","component":"Text","text":{"path":"/app/title"},"variant":"h2","tone":"default"},
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
        - Calls are limited to generated helper functions, core builtins abs(int), min(int,int),
          max(int,int), clamp(int,int,int), arrayFilled(count:int,value:scalar), arrayCopy(array), the TRACKER
          state builtins and the REALTIME_CANVAS scene builtins below.
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
        - Target at most 10000 source characters; hard limits are 48000 characters, 6000 lexer tokens,
          512 parsed statements and 64 global declarations.
        - The response is rejected if it reaches the source limit before the final closing brace. Prefer a
          complete compact implementation over expanded repetition.
        - Represent repeated cells, pieces, tasks and rules with fixed arrays, helper functions and bounded loops.
          Never unroll equivalent branches for every board cell, list item, scene node or state transition.
        - Never declare numbered sibling globals such as item1/item2, lastMove1/lastMove2 or x1/x2. Use one
          bounded array or a host-managed resource. Numbered state families are invalid even when they fit the
          source limit; stop and redesign compactly instead of continuing a repeated declaration sequence.
        - Arrays are fixed-size mutable values. Update an element with array[index] = value. Array assignment
          aliases the same mutable value; use arrayCopy(source) when independent state or reset semantics are
          required. arrayFilled creates 1..64 equal scalar entries and arrayCopy accepts at most 64 entries.
        - Every while loop must visibly advance a counter and terminate well within the execution budget.
        - Keep helpers compact and deterministic. Every required action must end with return null.
        - UI-facing named actions use onPascalCase and zero to six scalar int, string or boolean parameters. Prefer
          the conventional names and parameter keys from the A2UI contract. Parameter names must match A2UI event
          context keys exactly; helper functions that are not exposed to UI do not use the on prefix.
        - Expose one parameterized action for a family of controls that differs only by a scalar choice. For
          example use onAdjust(amount:int) for all quick-add amounts. Do not expose zero-argument wrappers such as
          onSmall(), onMedium() or onLarge() that hide presentation-relevant constants inside DEAL.
        - Use English for title, status, labels and visible shape text.
    """.trimIndent()

    val gridProfile: String = """
        Profile GRID is for turn-based boards, cell games, counters, quizzes and discrete choices.
        Required globals, with these exact names and types:
        - title:string, status:string, primaryLabel:string
        - items:string[] with 1..64 entries, each at most 24 characters
        - columns:int in 1..8 is the exact row topology for items. The renderer scales cells to the viewport; it never changes the column count. The final row may be partial.
        Required actions:
        - function onItem(index: int): null
        - function onPrimary(): null
        At least one valid onItem(index) call must change items or status. onPrimary must restore items exactly
        to their initial value.
        Own the complete rules in DEAL. Board games need occupied-cell guards, turns, win/draw state and reset.
        For a large fixed grid, initialize a compact seed with arrayFilled(cellCount, ""), assign populated cells
        by index, expose items = arrayCopy(seed), and reset with items = arrayCopy(seed). Do not hand-count a long
        literal of empty cells and do not alias mutable items to its reset seed.
        When a domain has universally recognized font-safe symbols, use those symbols in items instead of
        ambiguous one-letter abbreviations. Keep plain text when no symbol is unambiguous.
    """.trimIndent()

    val realtimeCanvasProfile: String = """
        Profile REALTIME_CANVAS is for free-form graphical apps, diagrams, simulations, motion, physics,
        dragging and action games. Coordinates and dimensions are integer scene units. Rendering uses a
        bounded retained scene graph owned by the sandbox; never declare parallel shape arrays.
        Required globals, with these exact names and types:
        - title:string, status:string, primaryLabel:string
        - canvasWidth:int in 160..2000, canvasHeight:int in 120..2000
        - canvasBackground:string as #RRGGBB or #RRGGBBAA
        - continuousAnimation:boolean; true only when periodic onTick frames are required

        Scene creation builtins return an integer handle. group is a non-empty string used for generic lookup.
        Coordinates are top-left bounds; line width/height are signed endpoint deltas. Colors use #RRGGBB or
        #RRGGBBAA. A scene contains 1..96 nodes after initialization.
        - sceneClear():null
        - sceneRect(group:string,x:int,y:int,w:int,h:int,color:string):int
        - sceneRoundRect(group:string,x:int,y:int,w:int,h:int,color:string,radius:int):int
        - sceneCircle(group:string,x:int,y:int,w:int,h:int,color:string):int
        - sceneEllipse(group:string,x:int,y:int,w:int,h:int,color:string):int
        - sceneLine(group:string,x:int,y:int,dx:int,dy:int,color:string):int
        - sceneText(group:string,x:int,y:int,w:int,h:int,color:string,label:string):int

        Scene mutation builtins return null:
        - sceneSetPosition(handle:int,x:int,y:int), sceneMove(handle:int,dx:int,dy:int)
        - sceneSetSize(handle:int,w:int,h:int), sceneSetColor(handle:int,color:string)
        - sceneSetStroke(handle:int,color:string,width:int), sceneSetLabel(handle:int,label:string)
        - sceneSetVisible(handle:int,visible:boolean), sceneSetGroupVisible(group:string,visible:boolean)
        - sceneSetInteractive(handle:int,interactive:boolean)
        - sceneSetLayer(handle:int,layer:int), sceneSetRotation(handle:int,degrees:int)
        - sceneSetCornerRadius(handle:int,radius:int), sceneRemove(handle:int)

        Scene query builtins:
        - sceneX(handle), sceneY(handle), sceneW(handle), sceneH(handle), sceneLayer(handle),
          sceneRotation(handle):int; sceneVisible(handle), sceneInteractive(handle):boolean
        - sceneCount(group:string):int, sceneAt(group:string,index:int):int
        Generic geometry builtins:
        - sceneOverlaps(first:int,second:int):boolean, sceneContains(handle:int,x:int,y:int):boolean
        - rectsOverlap(ax:int,ay:int,aw:int,ah:int,bx:int,by:int,bw:int,bh:int):boolean
        - pointInRect(px:int,py:int,x:int,y:int,w:int,h:int):boolean
        - circlesOverlap(ax:int,ay:int,ar:int,bx:int,by:int,br:int):boolean

        Build the initial scene by calling a generated buildScene() helper at module scope. Use one handle global
        for unique moving nodes. For repeated nodes, assign the same group and iterate with sceneCount(group)
        and sceneAt(group,index); never mirror render properties into parallel arrays. Keep domain state such as
        score, lives and velocity in compact scalar globals. sceneClear() resets handle allocation deterministically.
        Required actions:
        - function onTick(deltaMs: int): null
        - function onPointer(x: int, y: int, phase: int): null
        - function onPrimary(): null
        Pointer phase is 0 for down, 1 for move and 2 for up.
        Mark every pointer target with sceneSetInteractive(handle,true). At least one marked node must be visible,
        and onPointer at the center of at least one marked node must observably change the scene, status or scalar
        simulation state. The pointer must also activate a ready or paused scene when movement requires activation.
        If continuousAnimation is true, the following onTick(16) must observably change the activated scene. If it
        is false, the renderer does not schedule frame callbacks. onPrimary must restore the complete initial canvas,
        including interactive metadata:
        dimensions, background, every scene node and all internal simulation state.
        Implement application rules, movement, collisions, score/lives and win/lose state entirely in DEAL.
    """.trimIndent()

    val trackerProfile: String = """
        Profile TRACKER is for stateful utilities: goals, habits, schedules, checklists, forms, counters,
        histories and dashboards. It is a generic bounded resource ABI, not a water, todo or fitness template.
        Required globals, with these exact names and types:
        - title:string, status:string, primaryLabel:string
        Create 1..16 uniquely named resources during module initialization. Creation returns an integer handle:
        - stateCounter(name:string,value:int,target:int,min:int,max:int):int
        - stateList(name:string,capacity:int):int
        - stateSeries(name:string,labels:string[],values:int[]):int; labels may be [] or match values

        Counter mutation and queries:
        - stateCounterAdd(handle:int,delta:int):null
        - stateCounterSet(handle:int,value:int):null
        - stateCounterSetTarget(handle:int,target:int):null
        - stateCounterValue(handle:int):int, stateCounterTarget(handle:int):int

        Generic list mutation and queries. Each item has label, detail, group, numeric value, done and icon fields:
        - stateListAdd(handle:int,label:string,detail:string,group:string,value:int,done:boolean,icon:string):null
        - stateListToggle(handle:int,index:int):null
        - stateListSetDone(handle:int,index:int,done:boolean):null
        - stateListSetValue(handle:int,index:int,value:int):null
        - stateListRemove(handle:int,index:int):null
        - stateListCount(handle:int):int, stateListDoneCount(handle:int):int
        - stateListValue(handle:int,index:int):int, stateListDone(handle:int,index:int):boolean

        Series mutation and queries:
        - stateSeriesSet(handle:int,index:int,value:int):null
        - stateSeriesAdd(handle:int,index:int,delta:int):null
        - stateSeriesCount(handle:int):int, stateSeriesValue(handle:int,index:int):int
        - stateReset():null restores every resource to its post-initialization state

        Trusted host clock queries are clockEpochMinute():int, clockMinuteOfDay():int,
        clockSecondOfMinute():int and clockWeekday():int (Monday=0). UI may bind the same clock under
        /app/clock/{localTime,localDate,epochMinute,minuteOfDay,secondOfMinute,weekday}. For live countdowns or
        time-derived state, define optional function onTick(deltaMs:int):null; the host invokes it once per second
        while the app is visible. onTick is host-driven and must never be exposed as an A2UI action.

        Runtime bindings are:
        - counter: /app/resources/<name>/{kind,value,target,min,max,progress}
        - list: /app/resources/<name>/{kind,count,doneCount,items}; each item exposes
          {index,label,detail,group,value,done,icon}
        - series: /app/resources/<name>/{kind,labels,values,total,maximum}
        Lists have capacity 1..64; all resources and history are bounded by the host. Creation after initialization
        is forbidden. Required actions are function onPrimary():null plus at least one named onPascalCase UI action.
        onPrimary must call stateReset() and restore status. Keep domain rules in DEAL and presentation in A2UI.
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

    val trackerReference: String = """
        Complete valid TRACKER grammar reference (syntax reference only; design the requested utility):
        let title: string = "Daily goals";
        let status: string = "2 tasks complete";
        let primaryLabel: string = "Reset";
        let goal: int = stateCounter("goal", 800, 2000, 0, 3000);
        let tasks: int = stateList("tasks", 12);
        stateListAdd(tasks, "Morning walk", "Before breakfast", "Health", 1, true, "check");
        stateListAdd(tasks, "Take vitamins", "09:00", "Health", 1, false, "notification");
        let history: int = stateSeries("history", ["M","T","W","T","F","S","S"], [1,1,0,1,1,0,0]);
        function onAdjust(amount: int): null {
            stateCounterAdd(goal, amount);
            status = stateCounterValue(goal) + " of " + stateCounterTarget(goal);
            return null;
        }
        function onToggle(index: int): null {
            stateListToggle(tasks, index);
            status = stateListDoneCount(tasks) + " tasks complete";
            return null;
        }
        function onCheckIn(index: int): null {
            stateSeriesAdd(history, index, 1);
            return null;
        }
        function onPrimary(): null {
            stateReset();
            status = "2 tasks complete";
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
        let continuousAnimation: boolean = true;
        let paddle: int = 0;
        let ball: int = 0;
        let ballX: int = 485;
        let velocityX: int = 6;
        function buildScene(): null {
          sceneClear();
          paddle = sceneRoundRect("paddle", 30, 250, 30, 100, "#38BDF8", 12);
          sceneSetInteractive(paddle, true);
          ball = sceneCircle("ball", ballX, 285, 30, 30, "#F8FAFC");
          sceneSetStroke(ball, "#7DD3FC", 3);
          return null;
        }
        buildScene();
        function onTick(deltaMs: int): null {
          ballX = ballX + (velocityX * deltaMs / 16);
          if ((ballX <= 0) | (ballX >= 970)) { velocityX = -velocityX; }
          ballX = clamp(ballX, 0, 970);
          sceneSetPosition(ball, ballX, sceneY(ball));
          return null;
        }
        function onPointer(x: int, y: int, phase: int): null {
          sceneSetPosition(paddle, sceneX(paddle), clamp(y - 50, 0, 500));
          status = "Paddle moved";
          return null;
        }
        function onPrimary(): null {
          ballX = 485;
          velocityX = 6;
          status = "Drag the paddle";
          buildScene();
          return null;
        }
    """.trimIndent()

    fun dealProfiles(profile: GeneratedAppProfile?): String = when (profile) {
        GeneratedAppProfile.GRID -> "$gridProfile\n\n$gridReference"

        GeneratedAppProfile.REALTIME_CANVAS -> "$realtimeCanvasProfile\n\n$realtimeCanvasReference"

        GeneratedAppProfile.TRACKER -> "$trackerProfile\n\n$trackerReference"

        null -> """
        Select exactly one profile from the request. Never mix their globals.
            Use GRID for discrete boards, turn-based games and simple selectable collections. Use TRACKER for
            stateful utilities, forms, goals, lists and histories. Use
            REALTIME_CANVAS only when the requested interaction fundamentally needs free-form spatial graphics,
            dragging, animation or physics.
            $gridProfile

            $trackerProfile

            $realtimeCanvasProfile

            $gridReference

            $trackerReference

            $realtimeCanvasReference
        """.trimIndent()
    }
}
