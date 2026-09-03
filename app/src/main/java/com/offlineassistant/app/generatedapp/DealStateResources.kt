package com.offlineassistant.app.generatedapp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Bounded host-managed state used by generated utilities without exposing platform APIs. */
internal class DealStateResources {
    private val resources = linkedMapOf<Int, Resource>()
    private val names = linkedSetOf<String>()
    private var nextHandle = 1
    private var initial: List<Resource>? = null

    val isNotEmpty: Boolean
        get() = resources.isNotEmpty()

    fun sealInitialState() {
        require(resources.isNotEmpty()) { "TRACKER DEAL must create at least one state resource" }
        initial = resources.values.map(Resource::copyResource)
    }

    fun call(name: String, arguments: List<Any?>): DealBuiltinResult? = when (name) {
        "stateCounter" -> result(arguments, 5) {
            val resourceName = arguments[0].resourceName()
            create(
                CounterResource(
                    handle = nextHandle,
                    name = resourceName,
                    value = arguments[1].int(name),
                    target = arguments[2].int(name),
                    min = arguments[3].int(name),
                    max = arguments[4].int(name)
                ).also(CounterResource::validate)
            )
        }

        "stateCounterAdd" -> result(arguments, 2) {
            counter(arguments[0]).apply {
                value = (value.toLong() + arguments[1].int(name)).coerceIn(min.toLong(), max.toLong()).toInt()
            }
            null
        }

        "stateCounterSet" -> result(arguments, 2) {
            counter(arguments[0]).apply { value = arguments[1].int(name).coerceIn(min, max) }
            null
        }

        "stateCounterSetTarget" -> result(arguments, 2) {
            counter(arguments[0]).apply {
                target = arguments[1].int(name).coerceIn(min.coerceAtLeast(1), max)
            }
            null
        }

        "stateCounterValue" -> result(arguments, 1) { counter(arguments[0]).value }

        "stateCounterTarget" -> result(arguments, 1) { counter(arguments[0]).target }

        "stateList" -> result(arguments, 2) {
            val resourceName = arguments[0].resourceName()
            val capacity = arguments[1].int(name)
            require(capacity in 1..MAX_LIST_CAPACITY) { "DEAL state list capacity must be in 1..$MAX_LIST_CAPACITY" }
            create(ListResource(nextHandle, resourceName, capacity))
        }

        "stateListAdd" -> result(arguments, 7) {
            val list = list(arguments[0])
            require(list.items.size < list.capacity) { "DEAL state list ${list.name} is full" }
            require(totalListItems() < MAX_TOTAL_LIST_ITEMS) { "DEAL state list item limit exceeded" }
            list.items += ListItem(
                label = arguments[1].boundedText("label", 120, allowEmpty = false),
                detail = arguments[2].boundedText("detail", 180),
                group = arguments[3].boundedText("group", 60),
                value = arguments[4].boundedNumber(name),
                done = arguments[5].boolean(name),
                icon = arguments[6].boundedText("icon", 32)
            )
            null
        }

        "stateListToggle" -> result(arguments, 2) {
            listItem(arguments).apply { done = !done }
            null
        }

        "stateListSetDone" -> result(arguments, 3) {
            listItem(arguments).done = arguments[2].boolean(name)
            null
        }

        "stateListSetValue" -> result(arguments, 3) {
            listItem(arguments).value = arguments[2].boundedNumber(name)
            null
        }

        "stateListRemove" -> result(arguments, 2) {
            val list = list(arguments[0])
            list.items.removeAt(arguments[1].index(list.items, name))
            null
        }

        "stateListCount" -> result(arguments, 1) { list(arguments[0]).items.size }

        "stateListDoneCount" -> result(arguments, 1) { list(arguments[0]).items.count(ListItem::done) }

        "stateListValue" -> result(arguments, 2) { listItem(arguments).value }

        "stateListDone" -> result(arguments, 2) { listItem(arguments).done }

        "stateSeries" -> result(arguments, 3) {
            val resourceName = arguments[0].resourceName()
            val labels = arguments[1].strings(name)
            val values = arguments[2].ints(name)
            require(
                values.size in 1..MAX_SERIES_POINTS &&
                    values.all { it in -MAX_ABSOLUTE_VALUE..MAX_ABSOLUTE_VALUE }
            ) {
                "DEAL state series size must be in 1..$MAX_SERIES_POINTS"
            }
            require(labels.isEmpty() || labels.size == values.size) {
                "DEAL state series labels must be empty or match values"
            }
            require(totalSeriesPoints() + values.size <= MAX_TOTAL_SERIES_POINTS) {
                "DEAL state series point limit exceeded"
            }
            create(SeriesResource(nextHandle, resourceName, labels.toMutableList(), values.toMutableList()))
        }

        "stateSeriesSet" -> result(arguments, 3) {
            val series = series(arguments[0])
            series.values[arguments[1].index(series.values, name)] = arguments[2].boundedNumber(name)
            null
        }

        "stateSeriesAdd" -> result(arguments, 3) {
            val series = series(arguments[0])
            val index = arguments[1].index(series.values, name)
            series.values[index] = (series.values[index].toLong() + arguments[2].int(name))
                .coerceIn(-MAX_ABSOLUTE_VALUE.toLong(), MAX_ABSOLUTE_VALUE.toLong())
                .toInt()
            null
        }

        "stateSeriesCount" -> result(arguments, 1) { series(arguments[0]).values.size }

        "stateSeriesValue" -> result(arguments, 2) {
            val series = series(arguments[0])
            series.values[arguments[1].index(series.values, name)]
        }

        "stateReset" -> result(arguments, 0) {
            val baseline = requireNotNull(initial) { "DEAL stateReset is unavailable during initialization" }
            resources.clear()
            baseline.forEach { resource -> resources[resource.handle] = resource.copyResource() }
            null
        }

        else -> null
    }

    fun snapshot(): JsonObject = buildJsonObject {
        resources.values.forEach { resource -> put(resource.name, resource.snapshot()) }
    }

    private fun create(resource: Resource): Int {
        require(initial == null) { "DEAL state resources can only be created during initialization" }
        require(resources.size < MAX_RESOURCES) { "DEAL state resource limit exceeded" }
        require(names.add(resource.name)) { "Duplicate DEAL state resource name: ${resource.name}" }
        resources[resource.handle] = resource
        nextHandle++
        return resource.handle
    }

    private fun counter(value: Any?): CounterResource = resource(value) as? CounterResource
        ?: error("DEAL state handle is not a counter")

    private fun list(value: Any?): ListResource = resource(value) as? ListResource
        ?: error("DEAL state handle is not a list")

    private fun series(value: Any?): SeriesResource = resource(value) as? SeriesResource
        ?: error("DEAL state handle is not a series")

    private fun resource(value: Any?): Resource = resources[value.int("state resource")]
        ?: error("Unknown DEAL state resource handle")

    private fun listItem(arguments: List<Any?>): ListItem {
        val list = list(arguments[0])
        return list.items[arguments[1].index(list.items, "state list")]
    }

    private fun totalListItems(): Int = resources.values.filterIsInstance<ListResource>().sumOf { it.items.size }
    private fun totalSeriesPoints(): Int = resources.values.filterIsInstance<SeriesResource>().sumOf { it.values.size }

    private fun result(arguments: List<Any?>, arity: Int, block: () -> Any?): DealBuiltinResult {
        require(arguments.size == arity) { "Invalid DEAL state builtin arity: expected $arity, got ${arguments.size}" }
        return DealBuiltinResult(block())
    }

    private sealed interface Resource {
        val handle: Int
        val name: String
        fun copyResource(): Resource
        fun snapshot(): JsonObject
    }

    private data class CounterResource(
        override val handle: Int,
        override val name: String,
        var value: Int,
        var target: Int,
        val min: Int,
        val max: Int
    ) : Resource {
        fun validate() {
            require(
                min < max && min >= -MAX_ABSOLUTE_VALUE && max <= MAX_ABSOLUTE_VALUE &&
                    value in min..max && target in min.coerceAtLeast(1)..max
            ) {
                "DEAL state counter bounds are invalid"
            }
        }

        override fun copyResource(): Resource = copy()

        override fun snapshot(): JsonObject = buildJsonObject {
            put("kind", "counter")
            put("value", value)
            put("target", target)
            put("min", min)
            put("max", max)
            put("progress", if (target == 0) 0 else (value * 100 / target).coerceIn(0, 100))
        }
    }

    private data class ListItem(
        val label: String,
        val detail: String,
        val group: String,
        var value: Int,
        var done: Boolean,
        val icon: String
    )

    private data class ListResource(
        override val handle: Int,
        override val name: String,
        val capacity: Int,
        val items: MutableList<ListItem> = mutableListOf()
    ) : Resource {
        override fun copyResource(): Resource = copy(items = items.mapTo(mutableListOf(), ListItem::copy))

        override fun snapshot(): JsonObject = buildJsonObject {
            put("kind", "list")
            put("count", items.size)
            put("doneCount", items.count(ListItem::done))
            put(
                "items",
                buildJsonArray {
                    items.forEachIndexed { index, item ->
                        add(
                            buildJsonObject {
                                put("index", index)
                                put("label", item.label)
                                put("detail", item.detail)
                                put("group", item.group)
                                put("value", item.value)
                                put("done", item.done)
                                put("icon", item.icon)
                            }
                        )
                    }
                }
            )
        }
    }

    private data class SeriesResource(
        override val handle: Int,
        override val name: String,
        val labels: MutableList<String>,
        val values: MutableList<Int>
    ) : Resource {
        override fun copyResource(): Resource = copy(labels = labels.toMutableList(), values = values.toMutableList())

        override fun snapshot(): JsonObject = buildJsonObject {
            put("kind", "series")
            put("labels", JsonArray(labels.map(::JsonPrimitive)))
            put("values", JsonArray(values.map(::JsonPrimitive)))
            put("total", values.sum())
            put("maximum", values.maxOrNull() ?: 0)
        }
    }

    private fun Any?.resourceName(): String = (this as? String)
        ?.takeIf { it.matches(RESOURCE_NAME) }
        ?: error("DEAL state resource name is invalid")

    private fun Any?.boundedText(field: String, maxLength: Int, allowEmpty: Boolean = true): String {
        val value = this as? String ?: error("DEAL state $field must be a string")
        require(value.length <= maxLength && (allowEmpty || value.isNotBlank())) { "DEAL state $field is invalid" }
        return value
    }

    private fun Any?.int(function: String): Int = this as? Int ?: error("DEAL $function expected int argument")
    private fun Any?.boundedNumber(function: String): Int = int(function).also {
        require(it in -MAX_ABSOLUTE_VALUE..MAX_ABSOLUTE_VALUE) {
            "DEAL $function numeric value is outside the state limit"
        }
    }
    private fun Any?.boolean(function: String): Boolean = this as? Boolean ?: error("DEAL $function expected boolean argument")
    private fun Any?.ints(function: String): List<Int> = (this as? List<*>)
        ?.map { it as? Int ?: error("DEAL $function expected int[] argument") }
        ?: error("DEAL $function expected int[] argument")

    private fun Any?.strings(function: String): List<String> = (this as? List<*>)
        ?.map { it as? String ?: error("DEAL $function expected string[] argument") }
        ?: error("DEAL $function expected string[] argument")

    private fun Any?.index(values: List<*>, function: String): Int = int(function).also { index ->
        require(index in values.indices) { "DEAL $function index $index is outside 0..${values.lastIndex}" }
    }

    private companion object {
        const val MAX_RESOURCES = 16
        const val MAX_LIST_CAPACITY = 64
        const val MAX_TOTAL_LIST_ITEMS = 128
        const val MAX_SERIES_POINTS = 366
        const val MAX_TOTAL_SERIES_POINTS = 512
        const val MAX_ABSOLUTE_VALUE = 1_000_000
        val RESOURCE_NAME = Regex("[a-z][A-Za-z0-9_]{0,31}")
    }
}
