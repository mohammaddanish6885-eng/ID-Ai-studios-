package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.MediaType
import com.example.data.model.StudioProject
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudioProjectRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProjects(userId: String): Flow<List<StudioProject>> = flow {
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        StudioProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeImageHistory(userId: String): Flow<List<StudioProject>> =
        observeProjects(userId).map { projects ->
            projects.filter { it.mediaType == MediaType.IMAGE.name }
        }

    fun observeVideoHistory(userId: String): Flow<List<StudioProject>> =
        observeProjects(userId).map { projects ->
            projects.filter { it.mediaType == MediaType.VIDEO.name }
        }

    suspend fun getProject(userId: String, projectId: String): Result<StudioProject?> {
        val docRef = db.collection("users").document(userId).collection("projects").document(projectId)
        return try {
            val snapshot = docRef.get().await()
            val project = snapshot.toObject(
                StudioProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(project)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createProject(project: StudioProject): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = if (project.id.isNotBlank()) {
            db.collection("users").document(uid).collection("projects").document(project.id)
        } else {
            db.collection("users").document(uid).collection("projects").document()
        }
        val payload = mapOf(
            "userId" to uid,
            "title" to project.title.take(120).ifBlank { "Untitled Project" },
            "prompt" to project.prompt.take(1200).ifBlank { "Custom creation" },
            "mediaType" to project.mediaType,
            "aspectRatio" to project.aspectRatio,
            "stylePreset" to project.stylePreset,
            "colorGrade" to project.colorGrade,
            "exposure" to project.exposure.coerceIn(-1.0, 1.0),
            "contrast" to project.contrast.coerceIn(0.5, 2.0),
            "saturation" to project.saturation.coerceIn(0.0, 2.0),
            "warmth" to project.warmth.coerceIn(-1.0, 1.0),
            "overlayText" to project.overlayText.take(200),
            "localMediaPath" to project.localMediaPath.take(500),
            "scenes" to project.scenes.take(8).map { it.toMap() },
            "editHistory" to project.editHistory.take(20),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateProject(project: StudioProject): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        require(project.id.isNotBlank()) { "Project ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("projects").document(project.id)
        val updates = mapOf(
            "title" to project.title.take(120).ifBlank { "Untitled Project" },
            "prompt" to project.prompt.take(1200).ifBlank { "Custom creation" },
            "aspectRatio" to project.aspectRatio,
            "stylePreset" to project.stylePreset,
            "colorGrade" to project.colorGrade,
            "exposure" to project.exposure.coerceIn(-1.0, 1.0),
            "contrast" to project.contrast.coerceIn(0.5, 2.0),
            "saturation" to project.saturation.coerceIn(0.0, 2.0),
            "warmth" to project.warmth.coerceIn(-1.0, 1.0),
            "overlayText" to project.overlayText.take(200),
            "localMediaPath" to project.localMediaPath.take(500),
            "scenes" to project.scenes.take(8).map { it.toMap() },
            "editHistory" to project.editHistory.take(20),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        return try {
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProjects(projectIds: List<String>): Result<Int> {
        val uniqueIds = projectIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (uniqueIds.isEmpty()) return Result.success(0)

        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val collectionRef = db.collection("users").document(uid).collection("projects")
        return try {
            uniqueIds.chunked(450).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { projectId ->
                    batch.delete(collectionRef.document(projectId))
                }
                batch.commit().await()
            }
            Result.success(uniqueIds.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, collectionRef.path)
            Result.failure(e)
        }
    }
}
