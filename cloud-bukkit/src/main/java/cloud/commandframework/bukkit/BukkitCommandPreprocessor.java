//
// MIT License
//
// Copyright (c) 2024 Incendo
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package cloud.commandframework.bukkit;

import cloud.commandframework.brigadier.parser.WrappedBrigadierParser;
import cloud.commandframework.bukkit.internal.BukkitBackwardsBrigadierSenderMapper;
import cloud.commandframework.execution.preprocessor.CommandPreprocessingContext;
import cloud.commandframework.execution.preprocessor.CommandPreprocessor;
import java.util.concurrent.Executor;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Command preprocessor which decorates incoming {@link cloud.commandframework.context.CommandContext}
 * with Bukkit specific objects
 *
 * @param <C> command sender type
 */
final class BukkitCommandPreprocessor<C> implements CommandPreprocessor<C> {

    private final BukkitCommandManager<C> commandManager;
    private final @Nullable BukkitBackwardsBrigadierSenderMapper<C, ?> mapper;

    /**
     * The Bukkit Command Preprocessor for storing Bukkit-specific contexts in the command contexts
     *
     * @param commandManager The BukkitCommandManager
     */
    BukkitCommandPreprocessor(final @NonNull BukkitCommandManager<C> commandManager) {
        this.commandManager = commandManager;

        if (this.commandManager.hasCapability(CloudBukkitCapabilities.BRIGADIER)) {
            this.mapper = new BukkitBackwardsBrigadierSenderMapper<>(this.commandManager);
        } else {
            this.mapper = null;
        }
    }

    @Override
    public void accept(final @NonNull CommandPreprocessingContext<C> context) {
        if (this.mapper != null) {
            // If the server is Brigadier capable but the Brigadier manager has not been registered, store the native
            // sender in context manually so that getting suggestions from WrappedBrigadierParser works like expected.
            if (!context.commandContext().contains(WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER)) {
                context.commandContext().store(
                        WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER,
                        this.mapper.apply(context.commandContext().sender())
                );
            }
        }
        context.commandContext().store(
                BukkitCommandContextKeys.BUKKIT_COMMAND_SENDER,
                this.commandManager.senderMapper().reverse(context.commandContext().sender())
        );

        // Store if PaperCommandManager's preprocessor didn't already
        context.commandContext().computeIfAbsent(
                BukkitCommandContextKeys.SENDER_SCHEDULER_EXECUTOR,
                $ -> this.mainThreadExecutor()
        );
    }

    private Executor mainThreadExecutor() {
        final Plugin plugin = this.commandManager.owningPlugin();
        final Server server = plugin.getServer();
        return task -> {
            if (server.isPrimaryThread()) {
                task.run();
                return;
            }
            server.getScheduler().runTask(plugin, task);
        };
    }
}
