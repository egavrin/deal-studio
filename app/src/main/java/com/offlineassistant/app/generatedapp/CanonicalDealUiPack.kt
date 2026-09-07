package com.offlineassistant.app.generatedapp

internal object CanonicalDealUiPack {
    const val VERSION = "deal-studio-dealui-pack-v13"
    const val PREVIOUS_VERSION = "deal-studio-dealui-pack-v12"
    const val LEGACY_VERSION = "deal-studio-dealui-pack-v11"
    const val SHA256 = GeneratedCanonicalDealUiPackV13.SHA256
    const val PREVIOUS_SHA256 = GeneratedCanonicalDealUiPackV12.SHA256
    const val LEGACY_SHA256 = "78ab770900cb8ec99c854e31d92d7e4621c5ddaa7c205ff712c6a547a313ae49"

    private val legacySource: String = """
        export class Space { value: int = 0; }
        export class ColorToken { value: string = ""; }
        export class TextStyle { value: string = "body"; }
        export class AppThemeProps {
          primary: string = "#2563EB";
          secondary: string = "#0F766E";
          style: string = "clean";
          shape: string = "rounded";
          density: string = "comfortable";
          surface: string = "tonal";
        }
        export class EmptyProps {}
        export class LayoutProps { spacing?: Space; padding?: Space; horizontal: string = "start"; vertical: string = "top"; wrap: boolean = true; }
        export class GridProps { columns: int = 1; minimumCellWidth: int = 0; spacing?: Space; padding?: Space; }
        export class CardProps { tone: string = "surface"; spacing?: Space; padding?: Space; onClick?: Action; accessibilityLabel?: string; }
        export class TextProps { value: string = ""; style?: TextStyle; tone: string = "default"; }
        export class IntTextProps { value: int = 0; prefix: string = ""; suffix: string = ""; minimumDigits: int = 1; style?: TextStyle; tone: string = "default"; }
        export class IconProps { name: string = "info"; description: string = ""; tone: string = "default"; }
        export class IconButtonProps { icon: string = "info"; onClick?: Action; accessibilityLabel: string = ""; style: string = "tonal"; }
        export class ButtonProps { text: string = ""; icon: string = ""; onClick?: Action; accessibilityLabel?: string; style: string = "filled"; }
        export class ProgressProps { value: int = 0; maximum: int = 100; label: string = ""; }
        export class TextFieldProps { value: string = ""; label: string = ""; placeholder: string = ""; onChange?: Action; accessibilityLabel?: string; }
        export class TimeFieldProps { valueMinutes: int = 0; label: string = ""; onChange?: Action; accessibilityLabel?: string; }
        export class ToggleProps { checked: boolean = false; label: string = ""; onChange?: Action; accessibilityLabel?: string; }
        export class ChoiceProps { options: string[] = []; selected: int = 0; onSelect?: Action; accessibilityLabel?: string; }
        export class SliderProps { value: int = 0; minimum: int = 0; maximum: int = 100; label: string = ""; onChange?: Action; accessibilityLabel?: string; }
        export class SpacerProps { size?: Space; }
        export class BadgeProps { text: string = ""; tone: string = "neutral"; icon: string = ""; }
        export class StatProps { label: string = ""; value: string = ""; supporting: string = ""; icon: string = ""; tone: string = "accent"; }
        export class IntStatProps { label: string = ""; value: int = 0; prefix: string = ""; suffix: string = ""; minimumDigits: int = 1; supporting: string = ""; icon: string = ""; tone: string = "accent"; }
        export class ListItemProps { title: string = ""; subtitle: string = ""; leadingIcon: string = ""; trailing: string = ""; tone: string = "surface"; onClick?: Action; accessibilityLabel?: string; }
        export class SectionProps { title: string = ""; subtitle: string = ""; spacing?: Space; }
        export class ImageProps { url: string = ""; description: string = ""; ratioWidth: int = 16; ratioHeight: int = 9; fit: string = "cover"; }
        export class EmptyStateProps { title: string = ""; message: string = ""; icon: string = "info"; actionText: string = ""; onAction?: Action; accessibilityLabel?: string; }
        export class SnackbarProps { visible: boolean = false; message: string = ""; actionText: string = ""; onAction?: Action; onDismiss?: Action; accessibilityLabel?: string; }
        export class TopBarProps { title: string = ""; subtitle: string = ""; leadingIcon: string = ""; tone: string = "surface"; }
        export class CheckboxProps { checked: boolean = false; label: string = ""; supporting: string = ""; onChange?: Action; accessibilityLabel?: string; }
        export class StepperProps { value: int = 0; minimum: int = 0; maximum: int = 100; label: string = ""; decrementLabel: string = "Decrease"; incrementLabel: string = "Increase"; onChange?: Action; }
        export class TabsProps { options: string[] = []; selected: int = 0; onSelect?: Action; accessibilityLabel?: string; }
        export class NavigationProps { labels: string[] = []; icons: string[] = []; selected: int = 0; onSelect?: Action; accessibilityLabel?: string; }
        export class NavigationItemProps { label: string = ""; icon: string = "info"; selected: boolean = false; onClick?: Action; accessibilityLabel?: string; }
        export class ChartProps { series: int[] = []; maximum: int = 100; label: string = ""; tone: string = "accent"; }
        export class AvatarProps { url: string = ""; initials: string = ""; description: string = ""; size: int = 48; }
        export class VisibilityProps { visible: boolean = true; }
        export class ClockProps { intervalMillis: int = 60000; onTick?: Action; }
        export class PointerPayload { x: int = 0; y: int = 0; phase: int = 0; }
        export class PointerProps { coordinateWidth: int = 1000; coordinateHeight: int = 600; onPointer?: Action; accessibilityLabel?: string; }
        export class CanvasProps { width: int = 1000; height: int = 600; background: string = "#FFFFFF"; accessibilityLabel?: string; }
        export class ShapeProps { x: int = 0; y: int = 0; width: int = 0; height: int = 0; color: string = "#000000"; stroke: string = ""; label: string = ""; layer: int = 0; }
        export class RouteProps { route: string = ""; activeRoute: string = ""; }
        export class OverlayProps { visible: boolean = false; onDismiss?: Action; accessibilityLabel?: string; }
        export class CapabilityProps { name: string = ""; available: boolean = false; explanation: string = ""; onRequest?: Action; accessibilityLabel?: string; }

        export component AppTheme(props: AppThemeProps): View { children optional; capability "renderer.android.theme"; }
        export component Widget(props: EmptyProps): View { children optional; capability "renderer.android.widget"; }
        export component Root(props: LayoutProps): View { children optional; token spacing; capability "renderer.android.root"; }
        export component Column(props: LayoutProps): View { children optional; token spacing; capability "renderer.android.column"; }
        export component Row(props: LayoutProps): View { children optional; token spacing; capability "renderer.android.row"; }
        export component Stack(props: LayoutProps): View { children optional; token spacing; capability "renderer.android.stack"; }
        export component Grid(props: GridProps): View { children optional; token spacing; capability "renderer.android.grid"; }
        export component Scroll(props: LayoutProps): View { children optional; token spacing; capability "renderer.android.scroll"; }
        export component Card(props: CardProps): View { children optional; event onClick; accessibility accessibilityLabel; token spacing; capability "renderer.android.card"; }
        export component Section(props: SectionProps): View { children optional; token spacing; capability "renderer.android.section"; }
        export component Text(props: TextProps): View { capability "renderer.android.text"; }
        export component IntText(props: IntTextProps): View { capability "renderer.android.int-text"; }
        export component Icon(props: IconProps): View { accessibility description; capability "renderer.android.icon"; }
        export component IconButton(props: IconButtonProps): View { event onClick; accessibility accessibilityLabel; capability "renderer.android.icon-button"; }
        export component Button(props: ButtonProps): View { event onClick; accessibility accessibilityLabel; capability "renderer.android.button"; }
        export component ProgressBar(props: ProgressProps): View { capability "renderer.android.progress-bar"; }
        export component ProgressRing(props: ProgressProps): View { capability "renderer.android.progress-ring"; }
        export component TextField(props: TextFieldProps): View { event onChange(payload: string); accessibility accessibilityLabel; capability "renderer.android.text-field"; }
        export component TimeField(props: TimeFieldProps): View { event onChange(payload: int); accessibility accessibilityLabel; capability "renderer.android.time-field"; }
        export component Toggle(props: ToggleProps): View { event onChange(payload: boolean); accessibility accessibilityLabel; capability "renderer.android.toggle"; }
        export component Choice(props: ChoiceProps): View { event onSelect(payload: int); accessibility accessibilityLabel; capability "renderer.android.choice"; }
        export component Slider(props: SliderProps): View { event onChange(payload: int); accessibility accessibilityLabel; capability "renderer.android.slider"; }
        export component Spacer(props: SpacerProps): View { token size; capability "renderer.android.spacer"; }
        export component Badge(props: BadgeProps): View { capability "renderer.android.badge"; }
        export component Stat(props: StatProps): View { capability "renderer.android.stat"; }
        export component IntStat(props: IntStatProps): View { capability "renderer.android.int-stat"; }
        export component ListItem(props: ListItemProps): View { event onClick; accessibility accessibilityLabel; capability "renderer.android.list-item"; }
        export component Image(props: ImageProps): View { accessibility description; capability "renderer.android.image.https"; }
        export component EmptyState(props: EmptyStateProps): View { event onAction; accessibility accessibilityLabel; capability "renderer.android.empty-state"; }
        export component Snackbar(props: SnackbarProps): View { event onAction; event onDismiss; accessibility accessibilityLabel; capability "renderer.android.snackbar"; }
        export component TopBar(props: TopBarProps): View { children optional; capability "renderer.android.top-bar"; }
        export component Checkbox(props: CheckboxProps): View { event onChange(payload: boolean); accessibility accessibilityLabel; capability "renderer.android.checkbox"; }
        export component Stepper(props: StepperProps): View { event onChange(payload: int); capability "renderer.android.stepper"; }
        export component Tabs(props: TabsProps): View { event onSelect(payload: int); accessibility accessibilityLabel; capability "renderer.android.tabs"; }
        export component NavigationBar(props: NavigationProps): View { children optional; event onSelect(payload: int); accessibility accessibilityLabel; capability "renderer.android.navigation"; }
        export component NavigationItem(props: NavigationItemProps): View { event onClick; accessibility accessibilityLabel; capability "renderer.android.navigation-item"; }
        export component BarChart(props: ChartProps): View { accessibility label; capability "renderer.android.chart.bar"; }
        export component Sparkline(props: ChartProps): View { accessibility label; capability "renderer.android.chart.sparkline"; }
        export component Avatar(props: AvatarProps): View { accessibility description; capability "renderer.android.avatar.https"; }
        export component AnimatedVisibility(props: VisibilityProps): View { children optional; capability "renderer.android.animated-visibility"; }
        export component Divider(props: EmptyProps): View { capability "renderer.android.divider"; }
        export component FrameClock(props: ClockProps): View { event onTick(payload: int); capability "host.clock.frame"; }
        export component MinuteClock(props: ClockProps): View { event onTick(payload: int); capability "host.clock.minute"; }
        export component PointerSurface(props: PointerProps): View { children optional; event onPointer(payload: PointerPayload); accessibility accessibilityLabel; capability "host.pointer"; }
        export component Canvas(props: CanvasProps): View { children optional; accessibility accessibilityLabel; capability "renderer.android.canvas"; }
        export component Rectangle(props: ShapeProps): View { capability "renderer.android.shape.rectangle"; }
        export component RoundRectangle(props: ShapeProps): View { capability "renderer.android.shape.round-rectangle"; }
        export component Circle(props: ShapeProps): View { capability "renderer.android.shape.circle"; }
        export component Line(props: ShapeProps): View { capability "renderer.android.shape.line"; }
        export component CanvasText(props: ShapeProps): View { capability "renderer.android.shape.text"; }
        export component Route(props: RouteProps): View { children optional; capability "renderer.android.route"; }
        export component Modal(props: OverlayProps): View { children optional; event onDismiss; accessibility accessibilityLabel; capability "renderer.android.modal"; }
        export component BottomSheet(props: OverlayProps): View { children optional; event onDismiss; accessibility accessibilityLabel; capability "renderer.android.bottom-sheet"; }
        export component CapabilityNotice(props: CapabilityProps): View { event onRequest; accessibility accessibilityLabel; capability "renderer.android.capability-notice"; }

        export token spaceXs: Space = { value: 4 };
        export token spaceSm: Space = { value: 8 };
        export token spaceMd: Space = { value: 16 };
        export token spaceLg: Space = { value: 24 };
        export token textCaption: TextStyle = { value: "caption" };
        export token textBody: TextStyle = { value: "body" };
        export token textTitle: TextStyle = { value: "title" };
        export token textHeadline: TextStyle = { value: "headline" };
        export token textMetric: TextStyle = { value: "metric" };
        export token textDisplay: TextStyle = { value: "display" };
    """.trimIndent()

    val source: String = GeneratedCanonicalDealUiPackV13.SOURCE

    fun sourceFor(version: String): String? = when (version) {
        VERSION -> source
        PREVIOUS_VERSION -> GeneratedCanonicalDealUiPackV12.SOURCE
        LEGACY_VERSION -> legacySource
        else -> null
    }

    fun digestFor(version: String): String? = when (version) {
        VERSION -> SHA256
        PREVIOUS_VERSION -> PREVIOUS_SHA256
        LEGACY_VERSION -> LEGACY_SHA256
        else -> null
    }
}
