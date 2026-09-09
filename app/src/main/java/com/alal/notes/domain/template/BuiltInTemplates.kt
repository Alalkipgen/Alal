package com.alal.notes.domain.template

import com.alal.notes.data.entity.Template

object BuiltInTemplates {
    const val BLANK = "blank"
    const val FEATURE = "feature"
    const val INTERVIEW = "interview"
    const val RESEARCH = "research"
    const val IDEA = "idea"

    val all: List<Template> = listOf(
        Template(id = 1, key = BLANK, name = "Blank", icon = "edit", body = "", isBuiltIn = true, sortOrder = 0),
        Template(
            id = 2, key = FEATURE, name = "Feature Article", icon = "article", isBuiltIn = true, sortOrder = 1,
            body = "# [Headline]\n\n## Lead\n\n## Nut graf\n\n## Body\n\n## Quote\n\n## Kicker\n",
        ),
        Template(
            id = 3, key = INTERVIEW, name = "Interview", icon = "mic", isBuiltIn = true, sortOrder = 2,
            body = "# Interview: [Name]\n\n**Date:**  \n**Place:**  \n\n## Questions\n\n## Answers\n\n## Key quotes\n",
        ),
        Template(
            id = 4, key = RESEARCH, name = "Research", icon = "science", isBuiltIn = true, sortOrder = 3,
            body = "# [Topic]\n\n## Sources\n\n## Facts\n\n## Angles\n\n## To verify\n",
        ),
        Template(
            id = 5, key = IDEA, name = "Idea", icon = "lightbulb", isBuiltIn = true, sortOrder = 4,
            body = "# [Idea]\n\n**Why it matters:**  \n**Who to talk to:**  \n**Deadline:**  \n",
        ),
    )
}
