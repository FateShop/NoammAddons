package com.github.noamm9.commands.impl

import com.github.noamm9.commands.BaseCommand
import com.github.noamm9.commands.CommandNodeBuilder
import com.github.noamm9.features.impl.dungeon.waypoints.SecretRoutes

object SecretRouteCommand: BaseCommand("nsr") {
    override fun CommandNodeBuilder.build() {
        literal("save") {
            runs {
                SecretRoutes.saveRecording()
            }
        }

        literal("cancel") {
            runs {
                SecretRoutes.cancelRecording()
            }
        }

        literal("wait") {
            runs {
                SecretRoutes.insertWaitStep()
            }
        }

        literal("bat") {
            runs {
                SecretRoutes.insertBatWaitStep()
            }
        }

        literal("delete") {
            runs {
                SecretRoutes.deleteCurrentRoomRoute()
            }
        }

        runs {
            SecretRoutes.startRecording()
        }
    }
}
