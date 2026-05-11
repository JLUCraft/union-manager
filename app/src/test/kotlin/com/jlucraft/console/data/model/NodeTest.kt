package com.jlucraft.console.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun deserialize_instance_with_admission_strongly_typed() {
        val input = """
        {
            "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            "name": "test-instance",
            "kind": "room",
            "status": "running",
            "owner": "peer1",
            "club": "JLU",
            "current_host": "12D3KooWSomePeerId",
            "player_count": 4,
            "created_at": "2026-05-06T10:00:00Z",
            "updated_at": "2026-05-06T10:30:00Z",
            "host_port": 25566,
            "rcon_port": 25576,
            "auto_restart": true,
            "migration_target": null,
            "migration_progress": null,
            "admission": {
                "mode": "public",
                "allowed_clubs": [],
                "allowed_players": [],
                "requires_verified_email": false,
                "allowed_email_domains": []
            },
            "runtime": {
                "image": "itzg/minecraft-server:latest",
                "command": ["java"],
                "env": {},
                "labels": {},
                "working_dir": "/data",
                "data_mount_path": "/data",
                "log_path": "logs/latest.log"
            },
            "resources": {
                "cpu_cores": 2,
                "memory_gb": 4,
                "disk_gb": 20
            }
        }
        """.trimIndent()

        val instance = json.decodeFromString<Instance>(input)
        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", instance.id)
        assertEquals("test-instance", instance.name)

        // Verify admission is a strongly-typed AdmissionPolicy
        val admission = instance.admission!!
        assertEquals("public", admission.mode)
        assertTrue(admission.allowed_clubs.isEmpty())
        assertTrue(admission.allowed_players.isEmpty())
        assertTrue(!admission.requires_verified_email)

        // Verify runtime is a strongly-typed InstanceRuntimeSpec
        val runtime = instance.runtime!!
        assertEquals("itzg/minecraft-server:latest", runtime.image)
        assertEquals(listOf("java"), runtime.command)
        assertEquals("/data", runtime.workingDir)
        assertEquals("/data", runtime.dataMountPath)
        assertEquals("logs/latest.log", runtime.logPath)

        // Verify resources is a strongly-typed ResourceRequest
        val resources = instance.resources!!
        assertEquals(2, resources.cpuCores)
        assertEquals(4, resources.memoryGb)
        assertEquals(20, resources.diskGb)
    }

    @Test
    fun deserialize_instance_with_migration_progress_strongly_typed() {
        val input = """
        {
            "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
            "name": "migrating-instance",
            "kind": "room",
            "status": "migrating",
            "owner": "peer2",
            "club": "JLU",
            "current_host": "12D3KooWAnotherPeer",
            "player_count": 0,
            "created_at": "2026-05-06T09:00:00Z",
            "updated_at": "2026-05-06T09:15:00Z",
            "host_port": 25567,
            "rcon_port": 25577,
            "auto_restart": false,
            "migration_target": "12D3KooWTarget",
            "migration_progress": {
                "phase": "s3-sync",
                "started_at": "2026-05-06T09:10:00Z",
                "updated_at": "2026-05-06T09:15:00Z",
                "pre_dump_key": "worlds/some-uuid/snapshots/t1.tar",
                "final_dump_key": null,
                "error": null,
                "trigger": "active"
            },
            "admission": {
                "mode": "club-only",
                "allowed_clubs": ["JLU"],
                "allowed_players": [],
                "requires_verified_email": true,
                "allowed_email_domains": ["jlu.edu.cn"]
            },
            "runtime": {
                "image": "itzg/minecraft-server:java21",
                "command": ["java", "-jar", "server.jar"],
                "env": {},
                "labels": {},
                "working_dir": "/data",
                "data_mount_path": "/srv/mc",
                "log_path": "logs/latest.log"
            },
            "resources": {
                "cpu_cores": 4,
                "memory_gb": 8,
                "disk_gb": 40
            }
        }
        """.trimIndent()

        val instance = json.decodeFromString<Instance>(input)
        assertEquals("migrating", instance.status)

        // Verify migration_progress is a strongly-typed MigrationProgress
        val progress = instance.migrationProgress!!
        assertEquals("s3-sync", progress.phase)
        assertEquals("2026-05-06T09:10:00Z", progress.startedAt)
        assertEquals("2026-05-06T09:15:00Z", progress.updatedAt)
        assertEquals("worlds/some-uuid/snapshots/t1.tar", progress.preDumpKey)
        assertNull(progress.finalDumpKey)
        assertNull(progress.error)
        assertEquals("active", progress.trigger)

        // Verify admission strongly typed
        val admission = instance.admission!!
        assertEquals("club-only", admission.mode)
        assertEquals(listOf("JLU"), admission.allowed_clubs)
        assertTrue(admission.requires_verified_email)
        assertEquals(listOf("jlu.edu.cn"), admission.allowed_email_domains)
    }

    @Test
    fun deserialize_instance_with_null_admission() {
        val input = """
        {
            "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
            "name": "no-admission",
            "kind": "service",
            "status": "stopped",
            "owner": "peer3",
            "club": "JLU",
            "current_host": "12D3KooWNode",
            "player_count": 0,
            "created_at": "2026-05-06T08:00:00Z",
            "updated_at": "2026-05-06T08:05:00Z",
            "host_port": 25568,
            "rcon_port": 25578,
            "auto_restart": false,
            "migration_target": null,
            "migration_progress": null,
            "admission": null,
            "runtime": {
                "image": "itzg/minecraft-server:latest",
                "command": ["java"],
                "env": {},
                "labels": {},
                "working_dir": "/data",
                "data_mount_path": "/data",
                "log_path": "logs/latest.log"
            },
            "resources": {
                "cpu_cores": 1,
                "memory_gb": 2,
                "disk_gb": 10
            }
        }
        """.trimIndent()

        val instance = json.decodeFromString<Instance>(input)
        assertNull(instance.admission)
        assertNotNull(instance.runtime)
        assertNotNull(instance.resources)
        assertNull(instance.migrationProgress)
    }
}
