package com.aos.agent.core.engine

/** One independently rendered prompt contribution. */
data class PromptSection(
    val name: String,
    val order: Int,
    val render: (PromptContext) -> String,
)

data class PromptContext(
    val values: Map<String, String> = emptyMap(),
) {
    operator fun get(key: String): String = values[key].orEmpty()
}

class PromptAssembler(sections: List<PromptSection>) {
    private val sections = sections.toList()

    init {
        require(this.sections.map { it.name }.distinct().size == this.sections.size) {
            "Prompt section names must be unique"
        }
    }

    fun assemble(context: PromptContext): String = sections
        .sortedWith(compareBy(PromptSection::order, PromptSection::name))
        .map { it.render(context).trim() }
        .filter(String::isNotEmpty)
        .joinToString("\n\n")
}
