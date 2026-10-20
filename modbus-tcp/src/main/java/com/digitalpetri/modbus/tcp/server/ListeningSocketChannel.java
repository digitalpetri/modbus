package com.digitalpetri.modbus.tcp.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.nio.NioEventLoop;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** A listening channel whose socket-release future also waits for JDK selector deregistration. */
final class ListeningSocketChannel extends NioServerSocketChannel {

  private final AtomicBoolean closeStarted = new AtomicBoolean();
  private final CompletableFuture<Void> socketClosed = new CompletableFuture<>();

  /** Close the channel and wait until its listening socket is released. */
  CompletableFuture<Void> closeSocket() {
    if (!closeStarted.compareAndSet(false, true)) {
      return socketClosed;
    }

    NioEventLoop loop = eventLoop();
    Future<?> termination = loop.terminationFuture();
    GenericFutureListener<Future<Object>> terminated = ignored -> closeAfterTermination();
    termination.addListener(terminated);
    socketClosed.whenComplete((ignored, error) -> termination.removeListener(terminated));

    // A terminated event loop cannot deliver channel-close notifications. Its selector is already
    // closed, so the termination listener can finish releasing the socket directly.
    if (!termination.isDone()) {
      ChannelFuture closed = close();
      if (closed.isDone() && !closed.isSuccess()) {
        closeFailed(closed.cause());
      } else {
        closed.addListener(
            f -> {
              if (f.isSuccess()) {
                checkSocketClosed();
              } else {
                closeFailed(f.cause());
              }
            });
      }
    }

    return socketClosed;
  }

  private void checkSocketClosed() {
    if (socketClosed.isDone()) {
      return;
    }

    // The JDK removes the registration before closing the descriptor. Check on the event-loop
    // thread so we cannot observe the state between those two steps.
    if (!javaChannel().isOpen() && !javaChannel().isRegistered()) {
      socketClosed.complete(null);
    } else if (!eventLoop().isShuttingDown()) {
      try {
        // A normal task can run in the same batch as close(). Yield through the tail queue before
        // checking again, even when ioRatio=100 drains every normal task before selecting.
        eventLoop()
            .executeAfterEventLoopIteration(
                () -> {
                  try {
                    eventLoop().execute(this::checkSocketClosed);
                  } catch (RejectedExecutionException e) {
                    closeFailed(e);
                  }
                });
      } catch (RejectedExecutionException e) {
        closeFailed(e);
      }
    }
  }

  private void closeFailed(Throwable cause) {
    // Shutdown can reject or discard the check. The termination listener runs after the selector
    // is closed and remains responsible for completing socketClosed in that case.
    if (!(cause instanceof RejectedExecutionException && eventLoop().isShuttingDown())) {
      socketClosed.completeExceptionally(cause);
    }
  }

  private void closeAfterTermination() {
    try {
      // Usually already closed. Also covers a channel deregistered before the event loop stopped.
      javaChannel().close();
      socketClosed.complete(null);
    } catch (IOException e) {
      socketClosed.completeExceptionally(e);
    }
  }
}
