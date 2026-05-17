import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getProjects, type Project } from '@/api/project'

const PROJECT_KEY = 'hify.currentProjectId'

export const useProjectStore = defineStore('project', () => {
  const projects = ref<Project[]>([])
  const currentProjectId = ref<number | null>(Number(localStorage.getItem(PROJECT_KEY)) || null)
  const loading = ref(false)

  const currentProject = computed(() =>
    projects.value.find((project) => project.id === currentProjectId.value) ?? null,
  )

  async function loadProjects() {
    loading.value = true
    try {
      projects.value = await getProjects()
      if (!currentProjectId.value || !projects.value.some((project) => project.id === currentProjectId.value)) {
        setCurrentProject(projects.value[0]?.id ?? null)
      }
    } finally {
      loading.value = false
    }
  }

  function setCurrentProject(projectId: number | null) {
    currentProjectId.value = projectId
    if (projectId) {
      localStorage.setItem(PROJECT_KEY, String(projectId))
    } else {
      localStorage.removeItem(PROJECT_KEY)
    }
  }

  return {
    projects,
    currentProject,
    currentProjectId,
    loading,
    loadProjects,
    setCurrentProject,
  }
})
