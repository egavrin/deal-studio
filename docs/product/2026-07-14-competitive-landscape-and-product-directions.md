# Competitive Landscape and Product Directions

Date: 2026-07-14

## Purpose

This review looks for reusable product patterns across:

- system mobile assistants;
- AI notes and task managers;
- Android automation tools;
- local/offline voice assistants;
- emerging mobile-agent platform APIs.

The target is not a generic chatbot. The strongest product direction for this project is a private local personal assistant where notes and tasks provide memory, while typed Android capabilities provide safe actions.

## Competitive Landscape

| Product or approach | Strong pattern | Relevant limitation | Idea for this project |
| --- | --- | --- | --- |
| Google Gemini Utilities | Full lifecycle for alarms/timers, device controls, media, notifications and multiple actions in one request | Deep controls depend on default-assistant privileges and explicit notification access | Treat every domain as CRUD, not only `create`; add capability/permission metadata |
| Gemini screen automation | Builds an action plan, runs in background, exposes progress, Stop and Take control, and pauses for confirmation | Beta, selected devices/apps/languages; Google explicitly warns about mistakes and sensitive actions | Add plan/preview/confirm/progress contracts before any multi-step execution |
| Apple Siri AI and App Intents | Personal context, on-screen context and typed app actions exposed by developers | Requires app participation and platform integration | Keep skills schema-first; expose our notes/tasks through Android AppFunctions |
| Samsung Bixby and Modes/Routines | Conversational device control plus reusable trigger-condition-action routines | Strongest on Samsung system integrations | Add user-defined local routines after individual actions are stable |
| Perplexity Assistant | Can become the Android default assistant and mixes answers with app actions | Primarily cloud-centric and less deeply integrated than an OEM assistant | Default-assistant mode is valuable even before broad app automation |
| Todoist Ramble | Live voice capture turns unstructured speech into task drafts with project, date, deadline and priority; spoken corrections edit the draft | Cloud AI; optimized for task capture rather than broad assistant use | Build a dedicated voice-capture session with live editable task cards and conversational repair |
| Tana Voice Memos | One recording becomes typed objects such as tasks, decisions, agenda items and reflections | Complex knowledge model and cloud processing | Introduce a small stable object vocabulary instead of folders alone |
| Voicenotes | Memo/meeting/dictation modes, automatic titles/summaries, searchable voice memory and Ask AI over prior notes | Cloud processing; tasks are secondary | Make every transcript searchable and support local answers grounded in selected notes |
| Notion AI Meeting Notes | Transcript -> decisions/action items -> searchable workspace | Heavy workspace product; online-first | Later add a focused meeting-note mode, not a general document editor |
| Capacities | Daily note, timeline, quick capture and typed objects | More PKM than action assistant | Use Inbox + Today as primary surfaces and attach every capture to time/context |
| Tasker/MacroDroid | Trigger + conditions + ordered actions + reusable action blocks | Complex setup; some Android actions require root or special access | Offer a constrained visual routine builder backed by the same capability registry |
| Home Assistant Assist | Explicit focused-local versus full-local pipeline choices; streaming TTS; proactive conversations | Primarily smart-home oriented | Keep fast deterministic intents separate from open-ended local answers; add proactive prompts only from explicit user rules |
| FUTO Voice Input | Fully offline dictation with a simple privacy promise | Dictation only, no personal memory or actions | Privacy state should be obvious and understandable, not buried in debug UI |
| Dicio | Modular open-source Android voice assistant with offline ASR and graphical skill results | Narrower NLU/LLM capability and older interaction model | Retain modular skills and offline operation while improving conversation and visual feedback |
| Picovoice Rhino | Direct on-device speech-to-intent for deterministic command domains | Commercial runtime and predefined contexts | Evaluate direct speech-to-intent only as an optional fast path, not a replacement for open transcription |
| Android AppFunctions | OS-indexed typed functions act as local Android MCP tools for agents | Experimental preview; current project uses compileSdk 36 while the current tooling guidance requires compileSdk 37 | Run a separate compatibility spike; eventually export note/task capabilities and discover other apps' functions |

## Product Patterns Worth Copying

### 1. Capture before organization

The lowest-friction products do not ask the user to choose a folder, type or project before speaking. They capture into an Inbox, infer a draft structure and let the user correct it before commit.

Target interaction:

1. Hold or tap the microphone.
2. Speak one thought or a short brain dump.
3. See transcript and draft cards appear while speaking.
4. Correct with speech or touch: `Нет, это на пятницу`, `Удали второй пункт`.
5. Confirm once.

The committed objects must come from a typed draft parser. Qwen remains answer-only and must not execute actions or generate command JSON.

### 2. CRUD completeness creates trust

An assistant does not feel useful if it can create a timer but cannot answer how much time remains, pause it, rename it or cancel it. Every supported domain should define a complete, realistic lifecycle:

- create;
- list/search/status;
- edit/reschedule;
- complete/pause/resume;
- delete/cancel;
- undo when possible.

### 3. Personal memory needs evidence

For queries over notes and tasks, retrieval should happen outside Qwen. The model receives a small set of selected local records and returns plain text. The answer UI should link back to the source notes/tasks and show when no relevant local evidence was found.

This allows requests such as:

- `Что я записывал про поездку в Казань?`
- `Какие решения мы приняли по ремонту?`
- `Что из обещанного Ивану еще не сделано?`

### 4. Plans, confirmations and takeover

Multi-step agents are safer when execution is visible. The reusable interaction contract should support:

- draft plan;
- clarification;
- per-step status;
- confirmation policy;
- Stop;
- user takeover or opening the target system app;
- final result with partial-failure details.

This is more important than adding a large number of fragile actions.

### 5. Routines are user-authored proactivity

Proactivity should initially come only from explicit rules, not autonomous LLM guesses.

Routine model:

`trigger + conditions -> ordered typed actions`

Examples:

- `Когда подключусь к домашнему Wi-Fi после 18:00, покажи домашние задачи.`
- `Каждый будний день в 8:00 прочитай план дня.`
- `Когда заряд станет ниже 20%, включи экономию и напомни взять зарядку.`
- `Когда подключены наушники, предложи продолжить последнее аудио.`

## Recommended Product Position

### Local Personal Operator

The product promise:

> Быстро зафиксировать мысль, найти личную информацию и безопасно выполнить действие на телефоне без отправки голоса и личной памяти в облако.

### Differentiation hypothesis

The demo should not compete on model intelligence. Its defensible advantages are:

- the complete ASR/NLU/LLM/TTS path can remain on the phone;
- common actions use the small RuBERT path and do not pay LLM latency;
- actions are deterministic, typed and testable instead of being inferred by a general chatbot;
- private notes, tasks and notification-derived context can be useful without cloud indexing;
- the app continues to provide core actions when the network is unavailable.

This produces three explicit product lanes:

1. **Reflex lane** - RuBERT intent + slots -> validation -> typed Skill -> widget. This owns every phone and organizer action.
2. **Memory lane** - deterministic local retrieval/aggregation -> bounded evidence -> Qwen plain-text explanation -> source links. Qwen never executes the action.
3. **Answer lane** - Qwen plain-text answer for open questions that do not map to an action or personal-memory lookup.

The routing decision between these lanes must remain inspectable in debug builds. Low confidence in an action classifier must not silently turn into LLM tool execution.

### Scenarios suitable for the small Qwen model

The local 0.5B model is most valuable when the problem is bounded and grounded, not when it must know everything or build a reliable action plan:

- summarize three to five retrieved notes with links to originals;
- read a structured Today snapshot as a short natural briefing;
- explain why a requested action could not run and present deterministic alternatives;
- compare two user-selected notes or task lists;
- rewrite one short note for clarity without modifying the original;
- answer short general questions where the UI clearly labels the result as a local-model answer;
- conduct a lightweight private reflection over user-selected journal entries.

Do not use Qwen for intent fallback, command JSON, autonomous planning, destructive decisions, unbounded search over all private data or claims requiring fresh online knowledge.

The product has four connected surfaces:

1. **Capture** - text/voice Inbox for notes, tasks and reminders.
2. **Today** - calendar agenda, active timers, due tasks and recent reminders.
3. **Ask** - Qwen answers over general knowledge or retrieved local records, always as text.
4. **Act** - typed Android capabilities with permission, confirmation and result widgets.

## High-Value Feature Ideas

### Organizer

- Unified Inbox with Note/Task/Reminder conversion.
- Projects/lists, tags, priorities, recurrence and checklists.
- Today, Upcoming and Waiting views.
- Postpone, batch-complete and undo.
- Local full-text search first; optional local embeddings only after measuring quality, RAM and index cost.
- Source-linked Qwen summaries over selected notes.
- Daily briefing and end-of-day review.
- Voice capture corrections while recording.

### Calendar

Calendar is part of personal memory, not only another phone action. It should feed Today, free-time calculations and grounded briefings.

Two integration levels are useful:

1. **Delegated event creation** - use `ACTION_INSERT` with a prefilled system Calendar screen. This needs no broad calendar permission and leaves final confirmation to the user.
2. **Integrated agenda** - use Android `CalendarContract` with explicit `READ_CALENDAR`/`WRITE_CALENDAR` access for local query, insert, update and delete operations. Synced calendars remain owned by their calendar providers; network freshness depends on the provider's last sync.

Candidate intents:

- `get_agenda`;
- `find_calendar_event`;
- `create_calendar_event`;
- `reschedule_calendar_event`;
- `cancel_calendar_event`;
- `find_free_time`;
- `add_event_reminder`;
- `create_focus_block`.

Candidate widgets:

- `AgendaCard`;
- `CalendarEventCard`;
- `FreeTimeCard`;
- `EventConfirmationCard`.

Qwen may turn a deterministic agenda snapshot into a short spoken briefing, but it must not invent, move or delete events.

### Email

Android does not provide a universal mailbox content provider comparable to `CalendarContract`. Email therefore needs explicit capability levels:

1. **Offline-safe baseline** - create a prefilled email draft through a mail app; derive recent-message summaries from explicitly granted notification access; never claim full mailbox coverage.
2. **Provider connector** - optional Gmail/IMAP integration with OAuth, explicit scopes and an encrypted bounded local cache. Fresh mailbox operations require network access and must be labelled accordingly.
3. **Future AppFunctions** - discover `searchEmails`/draft functions exposed by installed mail apps when the Android preview becomes generally usable.

Candidate intents:

- `draft_email`;
- `find_recent_email_notification`;
- `get_email_digest`;
- `search_email` when a provider is connected;
- `reply_to_email` with mandatory preview and confirmation;
- `archive_email` with undo when the provider supports it.

Candidate widgets:

- `EmailDraftCard`;
- `EmailDigestCard`;
- `EmailSearchResultsCard`;
- `EmailPermissionOrConnectionCard`.

Qwen can summarize a bounded set of selected messages or rewrite draft text. It cannot choose recipients, send, archive or delete mail. Sending always crosses a typed confirmed capability boundary.

### Phone operator

- Battery, storage, network and audio status.
- Flashlight, volume, brightness and system settings panels.
- Active timer/alarm lifecycle.
- Media status and play/pause/next after explicit notification/media access.
- Read-only notification inbox and local digest; replies require confirmation.
- Draft call/SMS/email and delegated calendar actions through public Android contracts.
- Default-assistant role and compact `VoiceInteractionSession` as a separate milestone.

### Routines

- Time, device-state, connectivity and notification triggers.
- Conditions such as day, location class, battery and active mode.
- Reusable action blocks with typed inputs/outputs.
- Dry run and execution history.
- A global kill switch and per-routine enable switch.

### Platform interoperability

- Export `createTask`, `listTasks`, `completeTask`, `createNote` and `searchNotes` as AppFunctions.
- Keep the existing internal Skill contracts platform-independent.
- Add an experimental Android-only AppFunction adapter around those contracts.
- Do not move business logic into annotations or Android services.

## Architecture Implications

1. Keep Qwen answer-only. It can summarize retrieved local records but cannot select or execute actions.
2. Keep RuBERT as the single-action intent/slot path.
3. Add a separate typed draft extractor before supporting multi-item voice dumps. It should be trained/evaluated generically; do not add phrase-specific transcript repairs.
4. Introduce `CapabilityDescriptor` metadata:
   - required permissions/access;
   - availability;
   - confirmation policy;
   - reversibility/undo;
   - background execution support;
   - sensitive-data classification.
5. Introduce `ActionPlan` and `ActionStepResult` contracts before multi-step execution.
6. Migrate growing note/task data from SharedPreferences to Room with schema migrations and FTS.
7. Keep retrieval deterministic and bounded before passing records to Qwen.
8. Preserve a full local audit trail without retaining raw microphone audio by default.

## Prioritized Backlog

### P0: Organizer foundation

- Room-backed Note, Task, TaskList and ChecklistItem.
- Full CRUD intents and widgets.
- Inbox, Today, Calendar Agenda and Search surfaces.
- Local FTS and source-linked results.
- Undo for destructive actions.

### P1: Voice-first capture

- Live task/note draft cards from streaming transcript.
- Pending-command conversational repair.
- One confirmation for a capture session.
- Fixed voice-capture evaluation corpus with corrections and ambiguity cases.

### P1: Safe phone controls

- Device status, flashlight, volume/brightness and settings panels.
- Notification/media access onboarding.
- Notification digest and media controls.
- Integrated calendar read access plus delegated event creation.
- Call/SMS/email drafts with confirmation.
- Optional provider-backed email search remains outside the fully offline core.

### P2: Assistant shell and routines

- `ROLE_ASSISTANT` / `VoiceInteractionService` spike.
- Trigger-condition-action routine model.
- Plan/progress/Stop/takeover UI.
- Background execution history.

### P2: Android AppFunctions spike

- Separate compileSdk 37 compatibility branch or module.
- Export five organizer functions.
- Verify registration and execution with the Android sample test agent.
- Keep behind an experimental build flag until the API leaves preview.

## What Not To Build Yet

- General UI clicking through AccessibilityService.
- Autonomous purchases, message sending or destructive actions.
- Unbounded Qwen access to all personal records.
- LLM-generated command JSON or fallback action execution.
- A large routine marketplace before the local capability model is stable.
- Continuous hotword listening before battery, privacy and false-activation measurements.

## Sources

- [Google Gemini Utilities](https://support.google.com/gemini/answer/15235441?hl=en)
- [Gemini multi-step Android screen automation](https://support.google.com/gemini/answer/16940971?hl=en)
- [Apple App Intents and Siri integration](https://developer.apple.com/documentation/appintents/integrating-actions-with-siri-and-apple-intelligence)
- [Samsung Bixby device agent](https://news.samsung.com/global/samsung-introduces-the-new-bixby-in-one-ui-8-5)
- [Samsung Modes and Routines](https://www.samsung.com/us/support/answer/ANS10002624/)
- [Perplexity Android Assistant](https://www.perplexity.ai/help-center/en/articles/10450852-how-to-use-the-perplexity-android-assistant)
- [Todoist Ramble](https://www.todoist.com/help/articles/turn-your-scattered-thoughts-into-clear-tasks-ramble-jan-21-HhmP8ue8R)
- [Tana Voice Memos](https://outliner.tana.inc/voice-memos)
- [Voicenotes capabilities](https://help.voicenotes.com/en/articles/15391505-what-can-voicenotes-do)
- [Notion AI Meeting Notes](https://www.notion.com/en-US/md/ai-meeting-notes)
- [Capacities product](https://capacities.io/product)
- [MacroDroid triggers](https://www.macrodroidforum.com/wiki/index.php/Triggers)
- [Home Assistant local voice pipeline](https://www.home-assistant.io/voice_control/voice_remote_local_assistant)
- [FUTO Voice Input](https://voiceinput.futo.org/)
- [Dicio](https://f-droid.org/en/packages/org.stypox.dicio/)
- [Picovoice Rhino](https://picovoice.ai/products/voice/speech-to-intent/)
- [Android AppFunctions](https://developer.android.com/ai/appfunctions)
- [Android Calendar Provider](https://developer.android.com/identity/providers/calendar-provider)
- [Android common calendar/email intents](https://developer.android.com/guide/components/intents-common)
- [Gmail API](https://developers.google.com/workspace/gmail/api/reference/rest)
- [AndroidWorld](https://google-research.github.io/android_world/)
