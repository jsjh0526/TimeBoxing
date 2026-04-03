package com.example.timeboxing.data.repository

import com.example.timeboxing.domain.model.TaskTemplate

interface TemplateProvider {
    fun getTemplates(): List<TaskTemplate>
}
