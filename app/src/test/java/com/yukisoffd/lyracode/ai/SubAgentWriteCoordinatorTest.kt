package com.yukisoffd.lyracode.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubAgentWriteCoordinatorTest {
    @Test
    fun rejectsOverlappingTaskScopes() {
        val coordinator = SubAgentWriteCoordinator()
        val first = SubAgentWriteOwner(1L, 0)
        val second = SubAgentWriteOwner(1L, 1)

        val error = runCatching {
            coordinator.reserveBatch(
                linkedMapOf(
                    first to listOf("app/src"),
                    second to listOf("app/src/Main.kt"),
                ),
            )
        }.exceptionOrNull()

        assertNotNull(error)
    }

    @Test
    fun readOnlyTaskCannotMutateWorkspace() {
        val coordinator = SubAgentWriteCoordinator()
        val owner = SubAgentWriteOwner(1L, 0)
        coordinator.reserveBatch(linkedMapOf(owner to emptyList()))

        val error = runCatching {
            coordinator.acquire(owner, listOf("README.md"))
        }.exceptionOrNull()

        assertNotNull(error)
    }

    @Test
    fun assignedTaskCanMutateOnlyItsExactPath() {
        val coordinator = SubAgentWriteCoordinator()
        val owner = SubAgentWriteOwner(1L, 0)
        val reservation = coordinator.reserveBatch(linkedMapOf(owner to listOf("app/src/Main.kt")))

        val lease = coordinator.acquire(owner, listOf("app/src/Main.kt"))
        val undeclaredError = runCatching {
            coordinator.acquire(owner, listOf("app/src/Other.kt"))
        }.exceptionOrNull()

        assertNotNull(lease)
        assertNotNull(undeclaredError)
        lease?.close()
        reservation.close()
    }

    @Test
    fun blocksParentAndConcurrentMutationOfReservedPath() {
        val coordinator = SubAgentWriteCoordinator()
        val owner = SubAgentWriteOwner(1L, 0)
        val reservation = coordinator.reserveBatch(linkedMapOf(owner to listOf("app/src/Main.kt")))
        val parentError = runCatching {
            coordinator.acquire(null, listOf("app/src/Main.kt"))
        }.exceptionOrNull()
        val firstLease = coordinator.acquire(owner, listOf("app/src/Main.kt"))
        val concurrentError = runCatching {
            coordinator.acquire(owner, listOf("app/src/Main.kt"))
        }.exceptionOrNull()

        assertNotNull(parentError)
        assertNotNull(firstLease)
        assertNotNull(concurrentError)
        firstLease?.close()
        reservation.close()
    }

    @Test
    fun normalizesWorkspacePathsDeterministically() {
        val coordinator = SubAgentWriteCoordinator()

        assertEquals("app/src/Main.kt", coordinator.normalizeWorkspacePath("./app\\src//Main.kt"))
        assertNotNull(runCatching { coordinator.normalizeWorkspacePath("../Main.kt") }.exceptionOrNull())
        assertNotNull(runCatching { coordinator.normalizeWorkspacePath("/storage/Main.kt") }.exceptionOrNull())
    }
}
