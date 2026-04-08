package com.github.noamm9.features.impl.dungeon.waypoints

import com.github.noamm9.NoammAddons
import com.github.noamm9.event.impl.DungeonEvent
import com.github.noamm9.utils.WorldUtils
import com.github.noamm9.utils.Utils.equalsOneOf
import com.github.noamm9.utils.dungeons.enums.SecretType
import com.github.noamm9.utils.dungeons.map.core.RoomState
import com.github.noamm9.utils.dungeons.map.core.UniqueRoom
import com.github.noamm9.utils.dungeons.map.utils.ScanUtils
import com.github.noamm9.utils.location.LocationUtils
import com.github.noamm9.utils.render.Render3D
import com.github.noamm9.utils.render.RenderContext
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import java.awt.Color
import java.util.concurrent.CopyOnWriteArrayList

object SecretsWaypoints {
    private data class SecretWaypoint(val pos: BlockPos, val type: SecretType) {
        val color: Color = when (type) {
            SecretType.REDSTONE_KEY -> Color.RED
            SecretType.WITHER_ESSANCE -> Color.BLACK
            else -> Color.MAGENTA
        }
    }

    private val secretDefinitions by lazy { ScanUtils.roomList.associate { it.name to it.secretCoords } }
    private val persistentHeadTypes = setOf(SecretType.REDSTONE_KEY, SecretType.WITHER_ESSANCE)

    private val currentSecrets = CopyOnWriteArrayList<SecretWaypoint>()
    private var currentRoom: UniqueRoom? = null

    fun onRoomEnter(room: UniqueRoom) {
        if (! DungeonWaypoints.secretWaypoints.value) return
        currentRoom = room
        currentSecrets.clear()

        val rotation = room.rotation?.let { 360 - it } ?: return
        val corner = room.corner ?: return

        val coords = secretDefinitions[room.name] ?: return

        val activeSecrets = buildList {
            fun addSecrets(list: List<BlockPos>, type: SecretType) {
                list.forEach { add(SecretWaypoint(ScanUtils.getRealCoord(it, corner, rotation), type)) }
            }

            addSecrets(coords.redstoneKey, SecretType.REDSTONE_KEY)
            addSecrets(coords.wither, SecretType.WITHER_ESSANCE)
            addSecrets(coords.bat, SecretType.BAT)
            addSecrets(coords.item, SecretType.ITEM)
            addSecrets(coords.chest, SecretType.CHEST)
        }

        if (room.mainRoom.state == RoomState.GREEN) {
            currentSecrets.addAll(activeSecrets.filter { it.type in persistentHeadTypes })
            return
        }

        currentSecrets.addAll(activeSecrets)
    }

    fun onRenderWorld(ctx: RenderContext) {
        if (! DungeonWaypoints.secretWaypoints.value) return
        if (LocationUtils.inBoss) return
        if (currentSecrets.isEmpty()) return
        val room = currentRoom
        val completed = DungeonWaypoints.hideWhenCompleted.value && room != null && room.data.secrets > 0 && room.foundSecrets >= room.data.secrets
        val secretsToRender = if (completed) currentSecrets.filter { it.type in persistentHeadTypes } else currentSecrets

        for (wp in secretsToRender) {
            if (wp.type in persistentHeadTypes && WorldUtils.getBlockAt(wp.pos) != Blocks.PLAYER_HEAD) {
                renderGhostHead(ctx, wp)
                continue
            }

            Render3D.renderBlock(
                ctx, wp.pos,
                DungeonWaypoints.outlineColor.value,
                DungeonWaypoints.fillColor.value,
                DungeonWaypoints.mode.value.equalsOneOf(0, 2),
                DungeonWaypoints.mode.value.equalsOneOf(1, 2),
                phase = DungeonWaypoints.phase.value,
                lineWidth = DungeonWaypoints.lineWidth.value.toFloat()
            )
        }
    }

    fun onSecret(event: DungeonEvent.SecretEvent) {
        if (! DungeonWaypoints.secretWaypoints.value || currentSecrets.isEmpty()) return
        if (event.type == SecretType.LEVER) return
        val playerPos = NoammAddons.mc.player?.blockPosition() ?: return
        if (event.pos.distSqr(playerPos) > 36) return

        val distinctTypes = setOf(SecretType.BAT, SecretType.ITEM)

        val target = if (event.type !in distinctTypes) currentSecrets.find { it.pos == event.pos }
        else currentSecrets.filter { it.type in distinctTypes }.minByOrNull { it.pos.distSqr(event.pos) }

        if (target?.type in persistentHeadTypes) return
        target?.let(currentSecrets::remove)
    }

    fun findGhostHeadTargetForRoute(maxDistance: Double = 6.0): BlockPos? {
        val player = NoammAddons.mc.player ?: return null
        val eye = player.eyePosition
        val look = player.lookAngle.normalize()

        return currentSecrets.asSequence()
            .filter { it.type in persistentHeadTypes && WorldUtils.getBlockAt(it.pos) != Blocks.PLAYER_HEAD }
            .mapNotNull { waypoint ->
                val center = Vec3.atCenterOf(waypoint.pos)
                val delta = center.subtract(eye)
                val forward = delta.dot(look)
                if (forward !in 0.0..maxDistance) return@mapNotNull null

                val closestPoint = eye.add(look.scale(forward))
                val offset = center.distanceTo(closestPoint)
                if (offset > 0.85) return@mapNotNull null

                waypoint.pos to Pair(offset, forward)
            }
            .minWithOrNull(compareBy<Pair<BlockPos, Pair<Double, Double>>>({ it.second.first }, { it.second.second }))
            ?.first
    }

    fun hasSpawnedBatInCurrentRoom(): Boolean {
        val level = NoammAddons.mc.level ?: return false
        val batSecrets = currentSecrets.filter { it.type == SecretType.BAT }
        if (batSecrets.isEmpty()) return false

        return level.entitiesForRendering()
            .filterIsInstance<Bat>()
            .any { bat ->
                ! bat.isRemoved && ! bat.isInvisible && batSecrets.any { it.pos.distSqr(bat.blockPosition()) <= 9.0 }
            }
    }

    private fun renderGhostHead(ctx: RenderContext, waypoint: SecretWaypoint) {
        Render3D.renderBox(
            ctx,
            waypoint.pos.x + 0.5,
            waypoint.pos.y,
            waypoint.pos.z + 0.5,
            0.5,
            0.5,
            waypoint.color,
            outline = true,
            fill = true,
            phase = DungeonWaypoints.phase.value,
            lineWidth = DungeonWaypoints.lineWidth.value.toFloat()
        )
    }

    fun clear() {
        currentRoom = null
        currentSecrets.clear()
    }
}
