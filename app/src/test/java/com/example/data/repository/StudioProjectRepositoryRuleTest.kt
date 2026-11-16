package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AspectRatioPreset
import com.example.data.model.ColorGradePreset
import com.example.data.model.MediaType
import com.example.data.model.StudioProject
import com.example.data.model.StylePreset
import com.example.data.model.VideoSceneItem
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioProjectRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleProject(uid: String, mediaType: MediaType = MediaType.VIDEO): StudioProject {
        return StudioProject(
            userId = uid,
            title = "Neon Rain Reel",
            prompt = "A cybernetic stroll through glowing rain-slicked alleys",
            mediaType = mediaType.name,
            aspectRatio = AspectRatioPreset.PORTRAIT.apiValue,
            stylePreset = StylePreset.CYBERPUNK.id,
            colorGrade = ColorGradePreset.CINEMATIC_TEAL.id,
            exposure = 0.0,
            contrast = 1.1,
            saturation = 1.15,
            warmth = -0.1,
            overlayText = "CYBER RAIN",
            localMediaPath = "/tmp/reel.mp4",
            scenes = if (mediaType == MediaType.VIDEO) {
                listOf(
                    VideoSceneItem(
                        sceneIndex = 0L,
                        visualPrompt = "Wide establishing shot of neon alley",
                        caption = "Arrival",
                        cameraMotion = "ZOOM_IN",
                        transition = "CROSSFADE",
                        durationMs = 3000L,
                        localFramePath = "/tmp/frame_0.png"
                    )
                )
            } else {
                emptyList()
            },
            editHistory = listOf("Initial prompt generation")
        )
    }

    @Test
    fun createProject_validPayload_createsAndRetrievesDocument() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StudioProjectRepository(firestore)

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid))
        }
        assertTrue(createResult.isSuccess)
        val projectId = createResult.getOrThrow()
        assertTrue(projectId.isNotBlank())

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getProject(uid, projectId).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals("Neon Rain Reel", fetched?.title)
        assertEquals(projectId, fetched?.id)
    }

    @Test
    fun observeProjects_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StudioProjectRepository(firestore)

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.IMAGE)).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProjects(uid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(emitted.any { it.id == projectId })
    }

    @Test
    fun updateProject_authenticatedOwner_updatesEditableFields() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StudioProjectRepository(firestore)

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid)).getOrThrow()
        }
        val created = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getProject(uid, projectId).getOrThrow()!!
        }

        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateProject(
                created.copy(
                    title = "Director Cut Reel",
                    colorGrade = ColorGradePreset.WARM_KODAK.id
                )
            )
        }
        assertTrue(updateResult.isSuccess)

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getProject(uid, projectId).getOrThrow()
        }
        assertEquals("Director Cut Reel", updated?.title)
        assertEquals(ColorGradePreset.WARM_KODAK.id, updated?.colorGrade)
    }

    @Test
    fun getProject_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = StudioProjectRepository(firestore)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(sampleProject(aliceUid)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = StudioProjectRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getProject(aliceUid, projectId)
        }
        assertTrue(result.isFailure)
        val fsError = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    @Test
    fun observeProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = StudioProjectRepository(firestore)

        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeProjects("some_user_id").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        assertNotNull(caught)
        val fsError = caught?.unwrapFirestoreException()
        assertNotNull("Expected nested FirebaseFirestoreException, got $caught", fsError)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsError?.code)
    }

    @Test
    fun observeImageHistory_filtersOnlyImageProjects() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StudioProjectRepository(firestore)

        val imageId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.IMAGE)).getOrThrow()
        }
        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.VIDEO)).getOrThrow()
        }

        val imageHistory = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeImageHistory(uid).first { list -> list.any { it.id == imageId } }
        }
        assertTrue(imageHistory.any { it.id == imageId })
        assertTrue(imageHistory.none { it.id == videoId })
    }

    @Test
    fun deleteProjects_authenticatedOwner_deletesMultipleDocuments() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StudioProjectRepository(firestore)

        val id1 = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.IMAGE).copy(title = "Batch 1")).getOrThrow()
        }
        val id2 = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.IMAGE).copy(title = "Batch 2")).getOrThrow()
        }
        val keepId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.IMAGE).copy(title = "Keep 3")).getOrThrow()
        }

        val deleteResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deleteProjects(listOf(id1, id2))
        }
        assertTrue(deleteResult.isSuccess)
        assertEquals(2, deleteResult.getOrThrow())

        val remaining = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeImageHistory(uid).first { list ->
                list.none { it.id == id1 || it.id == id2 } && list.any { it.id == keepId }
            }
        }
        assertTrue(remaining.none { it.id == id1 || it.id == id2 })
        assertTrue(remaining.any { it.id == keepId })
    }

    @Test
    fun observeVideoHistory_filtersOnlyVideoProjects() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = StudioProjectRepository(firestore)

        val videoId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.VIDEO)).getOrThrow()
        }
        val imageId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(sampleProject(uid, MediaType.IMAGE)).getOrThrow()
        }

        val videoHistory = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeVideoHistory(uid).first { list -> list.any { it.id == videoId } }
        }
        assertTrue(videoHistory.any { it.id == videoId })
        assertTrue(videoHistory.none { it.id == imageId })
    }

    private companion object {
        const val ALICE_EMAIL = "alice_studio@test.com"
        const val BOB_EMAIL = "bob_studio@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
