package com.offlineassistant.core.skills

class SkillRegistry(skills: List<Skill>) {
    private val byIntent: Map<String, Skill>

    init {
        val entries = skills.flatMap { skill ->
            skill.supportedIntents.map { intent -> intent to skill }
        }
        val duplicateIntents = entries
            .groupingBy { it.first }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateIntents.isEmpty()) {
            "Multiple skills registered for: ${duplicateIntents.sorted().joinToString()}"
        }
        byIntent = entries.toMap()
    }

    val supportedIntents: Set<String>
        get() = byIntent.keys

    fun skillFor(intent: String): Skill? = byIntent[intent]

    suspend fun execute(command: NormalizedCommand): SkillResult {
        val skill = skillFor(command.intent)
            ?: return SkillResult(
                status = SkillStatus.ERROR,
                text = "Нет локального навыка для команды ${command.intent}.",
                actionResult = "missing_skill",
            )
        return skill.execute(command)
    }
}
