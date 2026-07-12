package dev.jsjh.timebox.data.repository

import dev.jsjh.timebox.domain.model.TaskTemplate

interface TemplateProvider {
    suspend fun getTemplates(): List<TaskTemplate>
}
